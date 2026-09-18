package id.co.jari.ocr.model

data class OcrRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}
