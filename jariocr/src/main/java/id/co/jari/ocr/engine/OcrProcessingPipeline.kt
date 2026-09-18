package id.co.jari.ocr.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import id.co.jari.ocr.confidence.ConfidenceCalculator
import id.co.jari.ocr.core.ImageQuality
import id.co.jari.ocr.core.ImageQualityAnalyzer
import id.co.jari.ocr.model.DocumentType
import id.co.jari.ocr.model.ExtractedText
import id.co.jari.ocr.model.OcrError
import id.co.jari.ocr.model.OcrResult
import id.co.jari.ocr.model.OcrScanMeta
import id.co.jari.ocr.parser.KtpOcrParser
import id.co.jari.ocr.parser.StnkOcrParser
import id.co.jari.ocr.util.OcrPreprocessor
import id.co.jari.ocr.util.YuvToRgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrProcessingPipeline(context: Context) {

    private val textEngine = MlKitTextEngine(context.applicationContext)

    suspend fun process(bitmap: Bitmap, documentType: DocumentType): OcrResult = withContext(Dispatchers.Default) {
        if (bitmap.width < 64 || bitmap.height < 64) {
            return@withContext OcrResult.Failure(
                OcrError.INVALID_INPUT,
                "Citra dokumen terlalu kecil (min 64x64 px)."
            )
        }
        val quality = ImageQualityAnalyzer.analyze(bitmap)
        processText(documentType, quality, 0L) {
            recognizeBetter(bitmap)
        }
    }

    suspend fun processFrame(
        image: Image,
        rotationDegrees: Int,
        documentType: DocumentType,
        quality: ImageQuality
    ): OcrResult = withContext(Dispatchers.Default) {
        processText(documentType, quality, 0L) {
            val frame = YuvToRgb.toBitmap(image, rotationDegrees, MAX_OCR_WIDTH)
            if (frame.width < 64 || frame.height < 64) {
                throw IllegalArgumentException("Citra terlalu kecil untuk OCR")
            }
            recognizeBetter(frame)
        }
    }

    private suspend fun recognizeBetter(bitmap: Bitmap): com.google.mlkit.vision.text.Text {
        val raw = textEngine.recognize(bitmap)
        val rawOptical = ConfidenceCalculator.opticalConfidence(ExtractedTextMapper.map(raw))
        if (rawOptical >= ENHANCE_TRIGGER) return raw

        val enhanced = runCatching { OcrPreprocessor.enhance(bitmap) }.getOrNull()
        if (enhanced == null) return raw
        val retry = runCatching { textEngine.recognize(enhanced) }.getOrNull() ?: return raw
        val retryOptical = ConfidenceCalculator.opticalConfidence(ExtractedTextMapper.map(retry))
        return if (retryOptical > rawOptical) retry else raw
    }

    private suspend fun processText(
        documentType: DocumentType,
        quality: ImageQuality,
        _elapsedHintMs: Long,
        recognize: suspend () -> com.google.mlkit.vision.text.Text
    ): OcrResult {
        val startedAtNanos = System.nanoTime()
        val mlText = try {
            recognize()
        } catch (e: Exception) {
            return OcrResult.Failure(
                OcrError.TEXT_RECOGNITION_FAILED,
                "Gagal memproses teks: ${e.message}"
            )
        }
        val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000

        val extracted = ExtractedTextMapper.map(mlText)
        if (!extracted.hasText) {
            return OcrResult.Failure(OcrError.NO_TEXT_FOUND, "Tidak ada teks yang terdeteksi dari dokumen.")
        }
        return buildResult(extracted, documentType, quality, elapsedMs)
    }

    private fun buildResult(
        extracted: ExtractedText,
        documentType: DocumentType,
        quality: ImageQuality,
        elapsedMs: Long
    ): OcrResult {
        val rawText = extracted.raw
        val optical = ConfidenceCalculator.opticalConfidence(extracted)
        val imageScore = ConfidenceCalculator.imageQualityScore(quality)

        return when (documentType) {
            DocumentType.KTP -> {
                val parsed = KtpOcrParser.parse(extracted)
                val format = ConfidenceCalculator.ktpFormatConfidence(parsed)
                val score = ConfidenceCalculator.composite(optical, format, imageScore)
                val meta = OcrScanMeta(
                    documentType = DocumentType.KTP,
                    opticalConfidence = optical,
                    formatConfidence = format,
                    imageQualityScore = imageScore,
                    laplacianVariance = quality.laplacianVariance,
                    glareRatio = quality.glareRatio,
                    elapsedMs = elapsedMs,
                    rawText = rawText
                )
                OcrResult.Ktp(parsed.copy(confidenceScore = score, confidenceTier = ConfidenceCalculator.tier(score), scanMeta = meta))
            }

            DocumentType.STNK -> {
                val parsed = StnkOcrParser.parse(extracted)
                val format = ConfidenceCalculator.stnkFormatConfidence(parsed)
                Log.d(
                    "Validasi STNK",
                    ConfidenceCalculator.stnkFormatChecks(parsed).joinToString(" | ") { (label, ok) ->
                        "$label=${if (ok) "OK" else "GAGAL"}"
                    }
                )
                val score = ConfidenceCalculator.composite(optical, format, imageScore)
                val meta = OcrScanMeta(
                    documentType = DocumentType.STNK,
                    opticalConfidence = optical,
                    formatConfidence = format,
                    imageQualityScore = imageScore,
                    laplacianVariance = quality.laplacianVariance,
                    glareRatio = quality.glareRatio,
                    elapsedMs = elapsedMs,
                    rawText = rawText
                )
                OcrResult.Stnk(parsed.copy(confidenceScore = score, confidenceTier = ConfidenceCalculator.tier(score), scanMeta = meta))
            }
        }
    }

    fun close() {
        textEngine.close()
    }

    private companion object {
        const val MAX_OCR_WIDTH = 1600
        const val ENHANCE_TRIGGER = 0.6f
    }
}
