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
}
