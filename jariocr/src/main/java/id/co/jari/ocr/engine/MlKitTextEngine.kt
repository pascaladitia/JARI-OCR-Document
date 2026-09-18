package id.co.jari.ocr.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import id.co.jari.ocr.util.await

class MlKitTextEngine(context: Context) {

    private val appContext = context.applicationContext

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): Text =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

    suspend fun recognize(image: Image, rotationDegrees: Int): Text =
        recognizer.process(InputImage.fromMediaImage(image, rotationDegrees)).await()

    fun close() {
        runCatching { recognizer.close() }
    }
}
