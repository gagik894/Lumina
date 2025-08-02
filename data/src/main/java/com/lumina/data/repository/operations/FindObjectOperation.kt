package com.lumina.data.repository.operations

import android.util.Log
import com.lumina.data.datasource.ObjectDetectorDataSource
import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.FrameBufferManager
import com.lumina.data.repository.PromptGenerator
import com.lumina.data.repository.TransientOperationCoordinator
import com.lumina.domain.model.NavigationCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "FindObjectOperation"

private val COCO_LABELS = setOf(
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train",
    "truck", "boat", "traffic light", "fire hydrant", "stop sign", "parking meter",
    "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant",
    "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag",
)

class FindObjectOperation @Inject constructor(
    private val transientOperationCoordinator: TransientOperationCoordinator,
    private val objectDetectorDataSource: ObjectDetectorDataSource,
    private val frameBufferManager: FrameBufferManager,
    private val promptGenerator: PromptGenerator,
    private val aiOperationHelper: AiOperationHelper,
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    fun execute(target: String): Flow<NavigationCue> {
        val normalizedTarget = target.trim().lowercase()

        return callbackFlow {
            val operationJob = repositoryScope.launch {
                transientOperationCoordinator.executeTransientOperation("find_object_$target") {
                    if (!isActive) return@executeTransientOperation

                    val sourceFlow = if (normalizedTarget in COCO_LABELS) {
                        findObjectWithObjectDetector(normalizedTarget, target)
                    } else {
                        findObjectWithAi(target)
                    }

                    sourceFlow.collect {
                        trySend(it)
                    }
                    close()
                }
            }
            awaitClose { operationJob.cancel() }
        }
    }

    private fun findObjectWithObjectDetector(normalizedTarget: String, target: String) =
        callbackFlow {
            objectDetectorDataSource.setPaused(false)

            val detectionsJob = repositoryScope.launch {
                objectDetectorDataSource.getDetectionStream(frameBufferManager.getFrameFlow())
                    .collect { detections ->
                        if (detections.any { it.equals(normalizedTarget, ignoreCase = true) }) {
                            trySend(NavigationCue.InformationalAlert("$target detected", false))

                            val bestFrame =
                                frameBufferManager.getBestQualityFrame() ?: return@collect
                            val locationPrompt =
                                promptGenerator.generateObjectLocationPrompt(target)
                            objectDetectorDataSource.setPaused(true)

                            try {
                                aiOperationHelper.withAiOperation {
                                    aiOperationHelper.generateResponse(
                                        locationPrompt,
                                        bestFrame.bitmap
                                    )
                                        .collect { (text, done) ->
                                            if (text.isNotBlank()) {
                                                trySend(
                                                    NavigationCue.InformationalAlert(
                                                        text,
                                                        done
                                                    )
                                                )
                                            }
                                            if (done) this@callbackFlow.close()
                                        }
                                }
                            } finally {
                                objectDetectorDataSource.setPaused(false)
                            }
                        }
                    }
            }
            awaitClose { detectionsJob.cancel() }
        }

    private fun findObjectWithAi(target: String) = callbackFlow {
        val detectionPrompt = promptGenerator.generateObjectDetectionPrompt(target)
        val locationPrompt = promptGenerator.generateObjectLocationPrompt(target)

        val collectorJob = repositoryScope.launch {
            frameBufferManager.getFrameFlow().collect { (_, _) ->
                val bestFrame = frameBufferManager.getBestQualityFrame()
                if (bestFrame == null) {
                    Log.d(TAG, "No quality frame available for analysis")
                    return@collect
                }

                try {
                    var fullResponse = ""
                    var objectDetected = false

                    aiOperationHelper.withAiOperation {
                        aiOperationHelper.generateResponse(detectionPrompt, bestFrame.bitmap)
                            .collect { (chunk, isDone) ->
                                fullResponse += chunk
                                if (isDone) {
                                    objectDetected = fullResponse.trim().lowercase().startsWith("y")
                                    Log.d(
                                        TAG,
                                        "Detection phase complete. Object detected: $objectDetected"
                                    )
                                }
                            }
                    }

                    if (objectDetected) {
                        trySend(NavigationCue.InformationalAlert("$target detected", false))
                        Log.d(TAG, "Object detected! Starting location description...")

                        aiOperationHelper.withAiOperation {
                            aiOperationHelper.generateResponse(locationPrompt, bestFrame.bitmap)
                                .collect { (locationChunk, locationDone) ->
                                    Log.d(
                                        TAG,
                                        "Location chunk: '$locationChunk', done: $locationDone"
                                    )
                                    if (locationChunk.isNotBlank()) {
                                        trySend(
                                            NavigationCue.InformationalAlert(
                                                locationChunk,
                                                locationDone
                                            )
                                        )
                                    }
                                    if (locationDone) {
                                        Log.d(TAG, "Location description complete, closing flow")
                                        this@callbackFlow.close()
                                    }
                                }
                        }
                    } else {
                        Log.d(TAG, "Object not detected in frame, continuing...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during object detection", e)
                }
            }
        }
        awaitClose { collectorJob.cancel() }
    }
}
