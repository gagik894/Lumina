package com.lumina.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
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
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    onFrame: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.await(context)
        val preview = Preview.Builder()
            // Match preview to analysis resolution for consistent aspect ratio.
            .setTargetResolution(Size(640, 480))
            .build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            // Reduce resolution to save processing power and bandwidth.
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
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