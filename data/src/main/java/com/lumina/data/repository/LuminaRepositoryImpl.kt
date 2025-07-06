package com.lumina.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lumina.data.datasource.AiDataSource
import com.lumina.data.datasource.ObjectDetectorDataSource
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.SceneDescription
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LuminaRepository] that coordinates between AI data sources and object detection
 * to provide proactive navigation assistance for visually impaired users.
 *
 * This repository implements a "Director Pattern" that intelligently decides when to trigger
 * AI descriptions based on object detection changes, optimizing for performance and user experience.
 *
 * @param gemmaDataSource The AI data source responsible for generating scene descriptions
 * @param objectDetectorDataSource The data source for detecting objects in camera frames
 */
@Singleton
class LuminaRepositoryImpl @Inject constructor(
    private val gemmaDataSource: AiDataSource,
    private val objectDetectorDataSource: ObjectDetectorDataSource
) : LuminaRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    override val initializationState: StateFlow<InitializationState> =
        gemmaDataSource.initializationState

    /** Job managing the proactive navigation pipeline */
    private var navigationJob: Job? = null

    /**
     * Shared flow broadcasting camera frames to multiple collectors.
     * Uses replay=1 to ensure late subscribers get the latest frame.
     */
    private val frameFlow = MutableSharedFlow<Pair<Bitmap, Long>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Flow emitting scene descriptions to the UI layer */
    private val descriptionFlow = MutableSharedFlow<SceneDescription>()

    override fun getProactiveNavigationCues(): Flow<SceneDescription> {
        // Start the pipeline if it's not already running
        if (navigationJob == null || navigationJob?.isActive == false) {
            startDirectorPipeline()
        }
        return descriptionFlow
    }

    override suspend fun processNewFrame(image: ImageInput) {
        // Convert domain model to Bitmap and send to the channel
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        frameFlow.tryEmit(Pair(bitmap, System.currentTimeMillis()))
    }

    override fun stopProactiveNavigation() {
        navigationJob?.cancel()
        objectDetectorDataSource.close()
        gemmaDataSource.resetSession()
    }

    override fun describeScene(
        image: ImageInput,
        prompt: String
    ): Flow<SceneDescription> {
        TODO("Manual scene description not yet implemented")
    }

    /**
     * Starts the Director Pipeline that coordinates object detection and AI descriptions.
     *
     * The pipeline:
     * 1. Continuously tracks the latest camera frame
     * 2. Monitors object detection changes
     * 3. Intelligently triggers AI descriptions when significant changes occur
     * 4. Implements a 5-second cooldown to prevent spam
     */
    private fun startDirectorPipeline() {
        navigationJob = repositoryScope.launch {
            var lastSeenObjects = emptySet<String>()
            var lastBrainTriggerTime = 0L
            var currentFrame: Bitmap? = null

            // Launch a collector to keep track of the latest frame
            launch {
                frameFlow.collect { (bitmap, _) ->
                    currentFrame = bitmap
                }
            }

            // Get the stream of detected objects from our Scout.
            objectDetectorDataSource.getDetectionStream(frameFlow)
                .collect { currentObjects ->
                    val currentObjectSet = currentObjects.toSet()
                    val currentTime = System.currentTimeMillis()

                    val newObjects = currentObjectSet - lastSeenObjects
                    val isSignificantChange = newObjects.isNotEmpty()
                    val isCooldownOver =
                        (currentTime - lastBrainTriggerTime) > 5000 // 5 sec cooldown

                    if (isSignificantChange && isCooldownOver) {
                        lastBrainTriggerTime = currentTime

                        currentFrame?.let { frame ->
                            //TODO: Use a more descriptive, context-aware prompts based on detected objects
                            val prompt = "fast describe image, just most important for a blind"
                            gemmaDataSource.generateResponse(prompt, frame)
                                .collect { (partialResponse, isDone) ->
                                    descriptionFlow.emit(SceneDescription(partialResponse, isDone))
                                }
                        }
                    }
                    lastSeenObjects = currentObjectSet
                }
        }
    }
}