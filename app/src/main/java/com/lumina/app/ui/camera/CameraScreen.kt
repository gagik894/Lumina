package com.lumina.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val TAG = "CameraScreen"

/**
 * Camera screen composable that provides real-time camera feed and frame analysis.
 *
 * This component sets up CameraX for continuous camera preview and image analysis.
 * Each frame is automatically processed and passed to the provided callback for
 * AI analysis. The implementation uses:
 * - Back camera as the default camera source
 * - KEEP_ONLY_LATEST backpressure strategy for optimal performance
 * - Main executor for UI thread safety
 *
 * @param onFrame Callback invoked for each camera frame, receiving a Bitmap for analysis
 */
@RequiresApi(Build.VERSION_CODES.P)
@OptIn(ExperimentalCamera2Interop::class, androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    onFrame: (Bitmap) -> Unit,
    showPreview: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(cameraProviderFuture, showPreview) {
        val cameraProvider = cameraProviderFuture.await(context)

        val analysisBuilder = ImageAnalysis.Builder()
            .setTargetResolution(Size(448, 448))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)

        // Apply same FPS range to analysis use case.
        Camera2Interop.Extender(analysisBuilder)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(15, 15)
            )

        val imageAnalyzer = analysisBuilder
            .build()
            .also {
                it.setAnalyzer(context.mainExecutor) { imageProxy ->
                    val bitmap = imageProxy.toBitmap()
                    onFrame(bitmap)
                    imageProxy.close()
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            if (cameraProvider.isBound(imageAnalyzer)) cameraProvider.unbind(imageAnalyzer)

            if (showPreview) {
                // create preview use case
                val previewBuilder = Preview.Builder()
                    .setTargetResolution(Size(448, 448))

                Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(15, 15)
                    )

                val preview = previewBuilder.build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalyzer,
                    preview
                )
            } else {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalyzer
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    if (showPreview) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {}
    }
}

/**
 * Extension function to await the result of a ListenableFuture in a coroutine.
 *
 * @param context Android context for accessing the main executor
 * @return The result of the future operation
 * @throws Exception if the future operation fails
 */
@RequiresApi(Build.VERSION_CODES.P)
private suspend fun <T> ListenableFuture<T>.await(context: Context): T {
    return suspendCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, context.mainExecutor)
    }
}

/**
 * Extension property to get the main executor for the given context.
 */
private val Context.mainExecutor: Executor
    get() = ContextCompat.getMainExecutor(this)