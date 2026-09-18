package id.co.jari.ocr.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image

object YuvToRgb {

    fun toBitmap(image: Image, rotationDegrees: Int = 0, maxWidth: Int = 1024): Bitmap {
        val nv21 = toNv21(image)
        var bitmap = nv21ToSampledBitmap(nv21, image.width, image.height, maxWidth)
        val rotation = ((rotationDegrees % 360) + 360) % 360
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    private fun toNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val frameSize = width * height
        val nv21 = ByteArray(frameSize + frameSize / 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        copyPlane(yPlane, nv21, 0, width, height, 1)
        val uvWidth = (width + 1) / 2
        val uvHeight = (height + 1) / 2

        copyPlane(vPlane, nv21, frameSize, uvWidth, uvHeight, 2)
        copyPlane(uPlane, nv21, frameSize + 1, uvWidth, uvHeight, 2)

        return nv21
    }

    private fun copyPlane(
        plane: Image.Plane,
        dst: ByteArray,
        dstOffset: Int,
        width: Int,
        height: Int,
        dstStride: Int
    ) {
        val buffer = plane.buffer
        buffer.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (y in 0 until height) {
            val srcRow = y * rowStride
            val dstRow = dstOffset + y * width * dstStride
            var x = 0
            while (x < width) {
                dst[dstRow + x * dstStride] = buffer[srcRow + x * pixelStride]
                x++
            }
        }
    }

    private fun nv21ToSampledBitmap(nv21: ByteArray, width: Int, height: Int, maxWidth: Int): Bitmap {
        val step = ((width + maxWidth - 1) / maxWidth).coerceAtLeast(1)
        val outWidth = (width / step).coerceAtLeast(1)
        val outHeight = (height / step).coerceAtLeast(1)
        val pixels = IntArray(outWidth * outHeight)
        val frameSize = width * height

        var pixelIndex = 0
        for (oj in 0 until outHeight) {
            val srcY = oj * step
            val yRow = srcY * width
            val uvRow = frameSize + (srcY / 2) * width
            for (oi in 0 until outWidth) {
                val srcX = oi * step
                val y = nv21[yRow + srcX].toInt() and 0xFF
                val uvIndex = uvRow + (srcX / 2) * 2
                val v = (nv21[uvIndex].toInt() and 0xFF) - 128
                val u = (nv21[uvIndex + 1].toInt() and 0xFF) - 128

                val yy = y.coerceIn(16, 235)
                val r = (1.164 * (yy - 16) + 1.596 * v).toInt().coerceIn(0, 255)
                val g = (1.164 * (yy - 16) - 0.813 * v - 0.391 * u).toInt().coerceIn(0, 255)
                val b = (1.164 * (yy - 16) + 2.018 * u).toInt().coerceIn(0, 255)

                pixels[pixelIndex++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888)
    }
}
