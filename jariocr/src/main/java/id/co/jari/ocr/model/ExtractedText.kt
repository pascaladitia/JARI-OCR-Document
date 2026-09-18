package id.co.jari.ocr.model

import id.co.jari.ocr.core.TextNormalizer

data class OcrElement(
    val text: String,
    val confidence: Float,
    val boundingBox: OcrRect?
)

data class OcrLine(
    val text: String?,
    val boundingBox: OcrRect?,
    val elements: List<OcrElement>
) {
    val safeText: String
        get() = text?.trim() ?: elements.joinToString("") { it.text }

    val confidence: Float
        get() = if (elements.isEmpty()) {
            1f
        } else {
            elements.map { it.confidence }.average().toFloat().coerceIn(0f, 1f)
        }

    fun normalizedText(): String = TextNormalizer.normalize(safeText)
}

data class OcrBlock(val lines: List<OcrLine>)

class ExtractedText(val blocks: List<OcrBlock>) {
    val lines: List<OcrLine> get() = blocks.flatMap { it.lines }
    val hasText: Boolean get() = lines.any { it.safeText.isNotBlank() }
    val raw: String get() = lines.joinToString("\n") { it.safeText }
}
