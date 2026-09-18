package id.co.jari.ocr.core

import android.graphics.Bitmap

data class ImageQuality(
    val laplacianVariance: Float,
    val glareRatio: Float
) {
    val isSharp: Boolean get() = laplacianVariance >= 100f
    val hasGlare: Boolean get() = glareRatio >= 0.15f
}

object ImageQualityAnalyzer {

    private const val MAX_ANALYSIS_WIDTH = 192
    private const val GLARE_THRESHOLD = 240

    private val LAPLACIAN = arrayOf(
        intArrayOf(0, 1, 0),
        intArrayOf(1, -4, 1),
        intArrayOf(0, 1, 0)
    )

    fun analyze(bitmap: Bitmap): ImageQuality {
        val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        val source = downscale(safeBitmap, MAX_ANALYSIS_WIDTH)
        val w = source.width
        val h = source.height
        if (w < 3 || h < 3) return ImageQuality(0f, 0f)

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        var glareCount = 0
        for (i in pixels.indices) {
            val g = luminance(pixels[i])
            gray[i] = g
            if (g >= GLARE_THRESHOLD) glareCount++
        }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            val rowBase = y * w
            for (x in 1 until w - 1) {
                var lap = 0
                for (ky in 0 until 3) {
                    val srcRow = (y + ky - 1) * w
                    for (kx in 0 until 3) {
                        lap += gray[srcRow + x + kx - 1] * LAPLACIAN[ky][kx]
                    }
                }
                sum += lap
                sumSq += lap.toDouble() * lap
                count++
            }
        }

        val mean = if (count > 0) sum / count else 0.0
        val variance = if (count > 0) (sumSq / count - mean * mean) else 0.0
        val glareRatio = if (pixels.isNotEmpty()) glareCount.toFloat() / pixels.size else 0f

        return ImageQuality(
            laplacianVariance = variance.toFloat().coerceAtLeast(0f),
            glareRatio = glareRatio.coerceIn(0f, 1f)
        )
    }

    private fun downscale(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth && src.height <= maxWidth) return src
        val scale = maxWidth.toFloat() / src.width
        val w = if (src.width <= maxWidth) src.width else maxWidth
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}
