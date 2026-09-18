package id.co.jari.ocr.ui

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import id.co.jari.ocr.core.ImageQualityAnalyzer
import id.co.jari.ocr.engine.OcrProcessingPipeline
import id.co.jari.ocr.model.ConfidenceTier
import id.co.jari.ocr.model.DocumentType
import id.co.jari.ocr.model.OcrResult
import id.co.jari.ocr.util.YuvToRgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executor
import java.util.concurrent.Executors

data class OcrScanStatus(
    val confidence: Float = 0f,
    val tier: ConfidenceTier = ConfidenceTier.LOW,
    val statusText: String = "Arahkan kamera ke dokumen",
    val bestResult: OcrResult? = null
)

class DocumentScannerAnalyzer(
    context: Context,
    private val autoCaptureThreshold: Float,
    private val onAutoCapture: (OcrResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val pipeline = OcrProcessingPipeline(context.applicationContext)

    val analyzerExecutor: Executor = Executors.newSingleThreadExecutor()

    private val _status = MutableStateFlow(OcrScanStatus())
    val status: StateFlow<OcrScanStatus> = _status.asStateFlow()

    @Volatile
    private var documentType: DocumentType = DocumentType.KTP

    @Volatile
    private var closed = false

    private var lastAnalysisMs = 0L
    private var bestScore = 0f
    private var bestResult: OcrResult? = null
    private var captureStreak = 0

    fun setDocumentType(type: DocumentType) {
        if (documentType != type) {
            documentType = type
            bestScore = 0f
            bestResult = null
            captureStreak = 0
        }
    }

    fun latestResult(): OcrResult? = bestResult

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        if (closed || now - lastAnalysisMs < MIN_FRAME_INTERVAL_MS) {
            proxy.close()
            return
        }
        lastAnalysisMs = now

        try {
            val image = proxy.image
            if (image == null) {
                _status.update { it.copy(statusText = "Menunggu gambar kamera...") }
                return
            }

            val rotation = proxy.imageInfo.rotationDegrees
            val type = documentType

            val quality = ImageQualityAnalyzer.analyze(
                YuvToRgb.toBitmap(image, rotation, MAX_QUALITY_WIDTH)
            )
            if (quality.laplacianVariance < MIN_SHARPNESS) {
                _status.update {
                    it.copy(
                        confidence = 0f,
                        tier = ConfidenceTier.LOW,
                        statusText = "Fokuskan dokumen ke kamera...",
                        bestResult = bestResult
                    )
                }
                return
            }

            val result = runBlocking { pipeline.processFrame(image, rotation, type, quality) }
            updateBest(result)
        } catch (e: Exception) {
            _status.update {
                it.copy(
                    confidence = 0f,
                    tier = ConfidenceTier.LOW,
                    statusText = "Periksa posisi & pencahayaan dokumen"
                )
            }
        } finally {
            runCatching { proxy.close() }
        }
    }

    private fun updateBest(result: OcrResult) {
        val score = result.confidenceScore()
        if (score > bestScore) {
            bestScore = score
            bestResult = result
        }
        captureStreak = if (score >= autoCaptureThreshold) captureStreak + 1 else 0

        _status.update {
            it.copy(
                confidence = score,
                tier = result.confidenceTier(),
                statusText = result.snippetText(),
                bestResult = bestResult
            )
        }

        if (captureStreak >= AUTO_CAPTURE_STREAK) {
            val finalResult = bestResult ?: result
            captureStreak = 0
            onAutoCapture(finalResult)
        }
    }

    fun close() {
        closed = true
        runCatching { pipeline.close() }
        runCatching { (analyzerExecutor as? java.util.concurrent.ExecutorService)?.shutdownNow() }
    }

    private companion object {
        const val MIN_FRAME_INTERVAL_MS = 400L
        const val MAX_QUALITY_WIDTH = 400
        const val MIN_SHARPNESS = 10f
        const val AUTO_CAPTURE_STREAK = 2
    }
}

internal fun OcrResult?.confidenceScore(): Float = when (this) {
    is OcrResult.Ktp -> data.confidenceScore
    is OcrResult.Stnk -> data.confidenceScore
    else -> 0f
}

internal fun OcrResult?.confidenceTier(): ConfidenceTier = when (this) {
    is OcrResult.Ktp -> data.confidenceTier
    is OcrResult.Stnk -> data.confidenceTier
    else -> ConfidenceTier.LOW
}

internal fun OcrResult.snippetText(): String = when (this) {
    is OcrResult.Ktp -> "NIK: ${data.nik.ifBlank { "-" }}"
    is OcrResult.Stnk -> "NRKB: ${data.nrkb.ifBlank { "-" }}"
    is OcrResult.Failure -> "Belum terdeteksi"
}
