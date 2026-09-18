package id.co.jari.ocr.util

import android.graphics.Bitmap

object OcrPreprocessor {

    private fun luma(pixel: Int): Int =
        ((pixel ushr 16 and 0xFF) * 77 + (pixel ushr 8 and 0xFF) * 150 + (pixel and 0xFF) * 29) ushr 8

    fun enhance(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            gray[i] = luma(pixels[i])
        }

        val background = separableBoxBlur(gray, width, height, BACKGROUND_RADIUS)
        val outGray = IntArray(gray.size)
        for (i in gray.indices) {
            val bg = background[i].coerceAtLeast(MIN_BG)
            var v = gray[i] * 255 / bg
            v = ((v - LOW) * 255 / (HIGH - LOW)).coerceIn(0, 255)
            outGray[i] = v
        }

        val out = IntArray(outGray.size)
        for (i in outGray.indices) {
            val v = outGray[i]
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun separableBoxBlur(gray: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val tmp = IntArray(gray.size)
        val out = IntArray(gray.size)
        val prefix = IntArray(width + 1)

        for (y in 0 until height) {
            val row = y * width
            prefix[0] = 0
            for (x in 0 until width) {
                prefix[x + 1] = prefix[x] + gray[row + x]
            }
            for (x in 0 until width) {
                val lo = (x - radius).coerceAtLeast(0)
                val hi = (x + radius).coerceAtMost(width - 1)
                tmp[row + x] = (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
            }
        }

        val colPrefix = IntArray(height + 1)
        for (x in 0 until width) {
            colPrefix[0] = 0
            for (y in 0 until height) {
                colPrefix[y + 1] = colPrefix[y] + tmp[y * width + x]
            }
            for (y in 0 until height) {
                val lo = (y - radius).coerceAtLeast(0)
                val hi = (y + radius).coerceAtMost(height - 1)
                out[y * width + x] = (colPrefix[hi + 1] - colPrefix[lo]) / (hi - lo + 1)
            }
        }

        return out
    }

    private const val BACKGROUND_RADIUS = 32
    private const val MIN_BG = 48
    private const val LOW = 5
    private const val HIGH = 250
}