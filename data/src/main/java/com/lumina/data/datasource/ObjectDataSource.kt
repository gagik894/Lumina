package com.lumina.data.datasource

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

interface ObjectDetectorDataSource {
    fun getDetectionStream(imageStream: Flow<Pair<Bitmap, Long>>): Flow<List<String>>
    fun close()
}

@Singleton
class MediaPipeObjectDetector @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ObjectDetectorDataSource {

    private val modelName = "efficientdet_lite0.tflite"
    private var objectDetector: ObjectDetector? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    val lastTimestamp = AtomicLong(0L)

    override fun getDetectionStream(imageStream: Flow<Pair<Bitmap, Long>>): Flow<List<String>> =
        callbackFlow {
            val listener = { result: ObjectDetectorResult, image: MPImage ->
                val detectedObjects = result.detections().mapNotNull { detection ->
                    detection.categories().firstOrNull()?.categoryName()
                }
                trySend(detectedObjects)
                Unit
            }

            initializeDetector(listener)

            val job: Job = scope.launch {
                imageStream.collect { (bitmap, timestamp) ->
                    // MediaPipe requires strictly increasing timestamps.
                    val safeTimestamp = generateSafeTimestamp(timestamp)
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    objectDetector?.detectAsync(mpImage, safeTimestamp)
                }
            }

            awaitClose {
                job.cancel()
                close()
            }
        }

    private fun initializeDetector(listener: (ObjectDetectorResult, MPImage) -> Unit) {
        val modelPath = getAbsoluteModelPath()
        val baseOptions = BaseOptions.builder().setModelAssetPath(modelPath).build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setScoreThreshold(0.5f)
            .setMaxResults(5)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(listener)
            .build()

        objectDetector = ObjectDetector.createFromOptions(context, options)
    }

    override fun close() {
        objectDetector?.close()
        objectDetector = null
    }

    private fun getAbsoluteModelPath(): String {
        val destinationFile = File(context.cacheDir, modelName)
        if (destinationFile.exists()) {
            return destinationFile.absolutePath
        }
        context.assets.open(modelName).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return destinationFile.absolutePath
    }
}

/**
 * Generates a timestamp that is guaranteed to be monotonically increasing.
 */
private fun MediaPipeObjectDetector.generateSafeTimestamp(rawTimestamp: Long): Long {
    while (true) {
        val prev = lastTimestamp.get()
        val candidate = if (rawTimestamp > prev) rawTimestamp else prev + 1
        if (lastTimestamp.compareAndSet(prev, candidate)) return candidate
    }
}