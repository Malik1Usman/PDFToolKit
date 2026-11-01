package com.example.pdftoolkit.ui.screens.scanscreen

import android.R.attr.scaleType
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pdftoolkit.core.scan.FlashSetting
import com.example.pdftoolkit.core.scan.ImageProcessor
import java.io.File
import java.util.concurrent.Executors

/** Shutter dabane par photo lene ke liye handle. */
class CameraController {
    internal var imageCapture: ImageCapture? = null

    fun takePicture(context: Context, onSaved: (File) -> Unit, onFailed: (String) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onFailed("Camera is not ready yet")
            return
        }
        val file = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onSaved(file)
                override fun onError(exception: ImageCaptureException) =
                    onFailed(exception.message ?: "Capture failed")
            }
        )
    }
}

/**
 * CameraX live preview + frame analysis (edge detection ke liye) + ImageCapture.
 * 4:3 use karte hain taaki 3:4 viewfinder mein bina crop ke fit ho (overlay coordinates seedhe map hote hain).
 */
@Composable
fun CameraPreviewHost(
    controller: CameraController,
    flash: FlashSetting,
    analysisEnabled: Boolean,
    onFrame: (Bitmap) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentAnalysis by rememberUpdatedState(analysisEnabled)
    val currentFlash by rememberUpdatedState(flash)

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(currentFlash.toCameraX())
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastRun = 0L
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (currentAnalysis && now - lastRun >= 200L) {
                            lastRun = now
                            val raw = image.toBitmap()
                            val degrees = image.imageInfo.rotationDegrees
                            val upright = if (degrees != 0) {
                                ImageProcessor.rotate(raw, degrees.toFloat()).also { if (it !== raw) raw.recycle() }
                            } else raw
                            currentOnFrame(upright)
                            upright.recycle()
                        }
                    } catch (_: Throwable) {
                        // ek frame fail hua to ignore
                    } finally {
                        image.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis
                )
                controller.imageCapture = capture
            } catch (t: Throwable) {
                onCameraError(t.message ?: "Camera error")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            controller.imageCapture = null
            runCatching { future.get().unbindAll() }
        }
    }

    LaunchedEffect(flash) {
        controller.imageCapture?.flashMode = flash.toCameraX()
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun FlashSetting.toCameraX(): Int = when (this) {
    FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
    FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
    FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
}