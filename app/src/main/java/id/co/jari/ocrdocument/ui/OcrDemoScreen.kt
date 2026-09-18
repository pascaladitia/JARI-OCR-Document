package id.co.jari.ocrdocument.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.co.jari.ocr.JariOcr
import id.co.jari.ocr.model.DocumentType
import id.co.jari.ocr.model.OcrError
import id.co.jari.ocr.model.OcrResult
import id.co.jari.ocr.ui.DocumentScannerScreen
import id.co.jari.ocr.ui.rememberOcrGalleryPicker
import id.co.jari.ocrdocument.ui.component.OcrResultAccordionCard
import id.co.jari.ocrdocument.ui.component.toFieldList
import kotlinx.coroutines.launch

@Composable
fun OcrDemoScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jariOcr = remember { JariOcr.create(context.applicationContext) }

    var selectedType by remember { mutableStateOf(DocumentType.KTP) }
    var activeScan by remember { mutableStateOf<DocumentType?>(null) }
    var processing by remember { mutableStateOf(false) }
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }

    val openGallery = rememberOcrGalleryPicker(
        onImage = { bitmap ->
            photo = bitmap
            processing = true
            ocrResult = null
            scope.launch {
                ocrResult = jariOcr.recognize(bitmap, selectedType)
                processing = false
            }
        },
        onError = {
            ocrResult = OcrResult.Failure(OcrError.INVALID_INPUT, "Gambar tidak dapat dibaca.")
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "JARI OCR - e-KTP & STNK",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Offline mode for OCR Document",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B)
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DocumentType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text("Dokumen: ${type.label}") }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ocrResult = null; photo = null; activeScan = selectedType },
                modifier = Modifier.weight(1f)
            ) {
                Text("Scan Kamera")
            }
            OutlinedButton(
                onClick = { openGallery() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Pilih Galeri")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (processing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.height(20.dp)
                )
                Spacer(Modifier.height(0.dp))
                Text("  Memproses OCR...", style = MaterialTheme.typography.bodyMedium)
            }
        }

        when (val result = ocrResult) {
            is OcrResult.Ktp -> {
                if (!result.data.isValidNik) {
                    WarningText("NIK belum terbaca 16 digit (${result.data.nik.ifBlank { "-" }}). Mohon periksa hasil.")
                }
                OcrResultAccordionCard(
                    confidenceScore = result.data.confidenceScore,
                    confidenceTier = result.data.confidenceTier,
                    fields = result.data.toFieldList(),
                    onRescanClicked = { ocrResult = null; photo = null; activeScan = DocumentType.KTP },
                    photoBitmap = photo,
                    title = "Hasil Pemindaian e-KTP"
                )
            }

            is OcrResult.Stnk -> {
                OcrResultAccordionCard(
                    confidenceScore = result.data.confidenceScore,
                    confidenceTier = result.data.confidenceTier,
                    fields = result.data.toFieldList(),
                    onRescanClicked = { ocrResult = null; photo = null; activeScan = DocumentType.STNK },
                    photoBitmap = photo,
                    title = "Hasil Pemindaian STNK"
                )
            }

            is OcrResult.Failure -> {
                WarningText("Gagal: ${result.message}")
            }

            null -> Unit
        }
    }

    activeScan?.let { type ->
        Surface(modifier = Modifier.fillMaxSize()) {
            DocumentScannerScreen(
                documentType = type,
                onResult = { scanResult ->
                    activeScan = null
                    ocrResult = scanResult
                    photo = null
                },
                onDismiss = { activeScan = null }
            )
        }
    }
}

@Composable
private fun WarningText(message: String) {
    Surface(
        color = Color(0xFFFFF7ED),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = "\u26A0  $message",
            color = Color(0xFF9A3412),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp)
        )
    }
}
