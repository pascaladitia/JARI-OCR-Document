package id.co.jari.ocr.model

data class OcrScanMeta(
    val documentType: DocumentType,
    val opticalConfidence: Float,
    val formatConfidence: Float,
    val imageQualityScore: Float,
    val laplacianVariance: Float,
    val glareRatio: Float,
    val elapsedMs: Long,
    val rawText: String
)
