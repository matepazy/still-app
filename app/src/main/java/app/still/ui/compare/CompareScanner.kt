package app.still.ui.compare

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import app.still.ui.components.StillIcons

@Composable
fun CompareScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Column(modifier.padding(24.dp)) {
            Text("Camera access lets Still scan your friend's QR code. The image stays on this phone.")
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
        return
    }
    val previewView = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var detectedCode by remember { mutableStateOf<String?>(null) }
    val successAlpha by animateFloatAsState(if (detectedCode == null) 0f else 1f,
        animationSpec = tween(220), label = "QR scan confirmation")
    LaunchedEffect(detectedCode) {
        detectedCode?.let { code ->
            delay(650)
            onCode(code)
        }
    }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        if (!delivered.get()) {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                            val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE))) }
                            val result = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))) }
                                .recoverCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.rotateCounterClockwise()))) }.getOrNull()
                            if (result != null && delivered.compareAndSet(false, true)) {
                                ContextCompat.getMainExecutor(context).execute { detectedCode = result.text }
                            }
                        }
                    } finally { image.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { cameraError = "Camera preview is unavailable on this device." }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        val frameSide = minOf(maxWidth * 0.76f, maxHeight * 0.55f)
        Canvas(Modifier.fillMaxSize()) {
            val side = minOf(size.width * 0.76f, size.height * 0.55f)
            val left = (size.width - side) / 2
            val top = (size.height - side) / 2
            val right = left + side
            val bottom = top + side
            val shade = Color.Black.copy(alpha = 0.36f)
            val corner = side * 0.24f
            val radius = 20.dp.toPx()
            drawPath(Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(radius)))
            }, shade)
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            listOf(
                Path().apply {
                    moveTo(left, top + corner); lineTo(left, top + radius)
                    arcTo(Rect(left, top, left + 2 * radius, top + 2 * radius), 180f, 90f, false)
                    lineTo(left + corner, top)
                },
                Path().apply {
                    moveTo(right - corner, top); lineTo(right - radius, top)
                    arcTo(Rect(right - 2 * radius, top, right, top + 2 * radius), 270f, 90f, false)
                    lineTo(right, top + corner)
                },
                Path().apply {
                    moveTo(right, bottom - corner); lineTo(right, bottom - radius)
                    arcTo(Rect(right - 2 * radius, bottom - 2 * radius, right, bottom), 0f, 90f, false)
                    lineTo(right - corner, bottom)
                },
                Path().apply {
                    moveTo(left + corner, bottom); lineTo(left + radius, bottom)
                    arcTo(Rect(left, bottom - 2 * radius, left + 2 * radius, bottom), 90f, 90f, false)
                    lineTo(left, bottom - corner)
                },
            ).forEach { drawPath(it, if (detectedCode == null) Color.White else Color(0xFF9FE3B2), style = stroke) }
        }
        Box(Modifier.align(Alignment.Center).alpha(successAlpha)
            .background(Color(0xFF203D2A), RoundedCornerShape(100.dp)).padding(20.dp)) {
            Icon(painterResource(StillIcons.Check), contentDescription = "Code scanned",
                tint = Color(0xFF9FE3B2), modifier = Modifier.align(Alignment.Center))
        }
        Text(if (detectedCode == null) "Point at your friend's QR code" else "Code scanned",
            modifier = Modifier.align(Alignment.Center)
            .offset(y = frameSide / 2 + 32.dp)
            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = Color.Black)
        cameraError?.let { Text(it, modifier = Modifier.align(Alignment.TopCenter).padding(24.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(12.dp),
            color = MaterialTheme.colorScheme.error) }
    }
}
