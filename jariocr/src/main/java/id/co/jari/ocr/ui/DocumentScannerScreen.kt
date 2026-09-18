package id.co.jari.ocr.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.co.jari.ocr.model.ConfidenceTier
import id.co.jari.ocr.model.DocumentType
import id.co.jari.ocr.model.OcrResult
import java.util.concurrent.Executor
import kotlinx.coroutines.delay

@Composable
fun DocumentScannerScreen(
    documentType: DocumentType,
    onResult: (OcrResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoCaptureThreshold: Float = 0.85f
) {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val finished = remember { mutableStateOf(false) }

    val analyzer = remember {
        DocumentScannerAnalyzer(
            context = context.applicationContext,
            autoCaptureThreshold = autoCaptureThreshold,
            onAutoCapture = { result ->
                if (!finished.value) {
                    finished.value = true
                    currentOnResult(result)
                }
            }
        )
    }
    val status by analyzer.status.collectAsState()

    var flashEnabled by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var message by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) message = "Izin kamera diperlukan untuk memindai dokumen."
    }

    LaunchedEffect(documentType) { analyzer.setDocumentType(documentType) }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            message = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { analyzer.close() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            CameraPreview(
                analyzer = analyzer,
                analyzerExecutor = analyzer.analyzerExecutor,
                flashEnabled = flashEnabled,
                modifier = Modifier.fillMaxSize()
            )
            ScannerHud(
                documentType = documentType,
                status = status,
                flashEnabled = flashEnabled,
                onToggleFlash = { flashEnabled = !flashEnabled },
                onDismiss = {
                    if (!finished.value) {
                        finished.value = true
                        currentOnDismiss()
                    }
                },
                onCapture = {
                    if (finished.value) return@ScannerHud
                    val best = analyzer.latestResult()
                    if (best == null || !best.isSuccess) {
                        message = "Belum ada teks yang terbaca. Dekatkan kamera & pastikan fokus."
                    } else {
                        finished.value = true
                        currentOnResult(best)
                    }
                }
            )
        } else {
            PermissionPlaceholder(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onDismiss = { if (!finished.value) { finished.value = true; currentOnDismiss() } }
            )
        }

        message?.let { text ->
            Surface(
                color = Color(0xE6111724),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 120.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerHud(
    documentType: DocumentType,
    status: OcrScanStatus,
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    onDismiss: () -> Unit,
    onCapture: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("\u2715", color = Color.White, fontSize = 18.sp)
            }
            Text(
                text = "Scan ${documentType.label}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onToggleFlash) {
                Text(if (flashEnabled) "\uD83D\uDD26 ON" else "\uD83D\uDD26 OFF", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val corner = 42.dp.toPx()
                val stroke = 6f
                val color = Color(0xFF4ADE80)
                val w = size.width
                val h = size.height
                drawLine(color, Offset(0f, 0f), Offset(0f, corner), stroke)
                drawLine(color, Offset(0f, 0f), Offset(corner, 0f), stroke)
                drawLine(color, Offset(w, 0f), Offset(w, corner), stroke)
                drawLine(color, Offset(w, 0f), Offset(w - corner, 0f), stroke)
                drawLine(color, Offset(0f, h), Offset(0f, h - corner), stroke)
                drawLine(color, Offset(0f, h), Offset(corner, h), stroke)
                drawLine(color, Offset(w, h), Offset(w, h - corner), stroke)
                drawLine(color, Offset(w, h), Offset(w - corner, h), stroke)
            }

            Surface(
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = status.statusText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ScannerConfidence(tier = status.tier, score = status.confidence)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Posisikan dokumen di dalam bingkai, pastikan teks fokus dan tidak tertutup bayangan atau pantulan cahaya.",
            color = Color(0xFFCBD5E1),
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCapture,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A))
        ) {
            Text("\uD83D\uDD0D  PINDAI DOKUMEN", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PermissionPlaceholder(onRequest: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Izin Kamera Diperlukan",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Aplikasi memerlukan akses kamera untuk memindai e-KTP dan STNK secara offline di perangkat.",
            color = Color(0xFFCBD5E1),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A))
        ) {
            Text("Izinkan Kamera", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onDismiss) {
            Text("Batal", color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun CameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    analyzerExecutor: Executor,
    flashEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val cameraState = remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        var disposed = false
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (disposed) return@addListener
            runCatching {
                val provider = providerFuture.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor, analyzer)
                cameraState.value = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }.onFailure { cameraState.value = null }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            runCatching { if (providerFuture.isDone) providerFuture.get().unbindAll() }
            cameraState.value = null
        }
    }

    LaunchedEffect(flashEnabled, cameraState.value) {
        val camera = cameraState.value ?: return@LaunchedEffect
        runCatching { camera.cameraControl.enableTorch(flashEnabled) }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun ScannerConfidence(tier: ConfidenceTier, score: Float) {
    val color = when (tier) {
        ConfidenceTier.HIGH -> Color(0xFF22C55E)
        ConfidenceTier.MEDIUM -> Color(0xFFF59E0B)
        ConfidenceTier.LOW -> Color(0xFFEF4444)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${(score * 100).toInt()}%  ${tier.name}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
