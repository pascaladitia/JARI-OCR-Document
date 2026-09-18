package id.co.jari.ocr

import android.content.Context
import android.graphics.Bitmap
import id.co.jari.ocr.engine.OcrProcessingPipeline
import id.co.jari.ocr.model.DocumentType
import id.co.jari.ocr.model.OcrResult

class JariOcr private constructor(
    private val pipeline: OcrProcessingPipeline
) {

    suspend fun recognizeKtp(bitmap: Bitmap): OcrResult =
        pipeline.process(bitmap, DocumentType.KTP)

    suspend fun recognizeStnk(bitmap: Bitmap): OcrResult =
        pipeline.process(bitmap, DocumentType.STNK)

    suspend fun recognize(bitmap: Bitmap, documentType: DocumentType): OcrResult =
        pipeline.process(bitmap, documentType)

    fun close() {
        pipeline.close()
    }

    companion object {
        @Volatile
        private var instance: JariOcr? = null

        fun create(context: Context): JariOcr =
            instance ?: synchronized(this) {
                instance ?: JariOcr(OcrProcessingPipeline(context.applicationContext)).also { instance = it }
            }
    }
}
