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
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    objectDetector?.detectAsync(mpImage, timestamp)
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