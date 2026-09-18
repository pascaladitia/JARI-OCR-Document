package id.co.jari.ocr.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

object OcrImageLoader {

    private const val MAX_DIMENSION = 2400

    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIMENSION ||
            bounds.outHeight / (sample * 2) >= MAX_DIMENSION
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}

@Composable
fun rememberOcrGalleryPicker(
    onImage: (Bitmap) -> Unit,
    onError: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val currentOnImage by rememberUpdatedState(onImage)
    val currentOnError by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bitmap = OcrImageLoader.loadBitmap(context, uri)
            if (bitmap != null) currentOnImage(bitmap) else currentOnError()
        }
    }
    return remember(launcher) { { launcher.launch("image/*") } }
}
