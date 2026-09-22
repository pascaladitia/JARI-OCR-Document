package id.co.jari.ocr.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.graphics.scale

object BitmapUtils {

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return src
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun downscale(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val scale = maxWidth.toFloat() / src.width
        return src.scale(maxWidth, (src.height * scale).toInt().coerceAtLeast(1))
    }

    fun upscale(src: Bitmap, factor: Float, maxDimension: Int): Bitmap {
        val widthScale = maxDimension.toFloat() / src.width.coerceAtLeast(1)
        val heightScale = maxDimension.toFloat() / src.height.coerceAtLeast(1)
        val scale = factor.coerceAtMost(widthScale).coerceAtMost(heightScale)
        if (scale <= 1f) return src
        val width = (src.width * scale).toInt().coerceAtLeast(src.width)
        val height = (src.height * scale).toInt().coerceAtLeast(src.height)
        return src.scale(width, height)
    }
}
