package com.lumina.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lumina.data.datasource.AiDataSource
import com.lumina.data.datasource.ObjectDetectorDataSource
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.NavigationCue
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
 * to provide intelligent navigation assistance for visually impaired users.
 *
 * This repository implements an intelligent "Director Pattern" with a sophisticated decision tree:
 * - Rule 1: Critical threats (immediate obstacles)
 * - Rule 2: Important new objects appearing
 * - Rule 3: Periodic ambient updates
 * - Rule 4: Stable state (power saving mode)
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

    /** Job managing the navigation pipeline */
    private var navigationJob: Job? = null

    /**
     * Shared flow broadcasting camera frames to multiple collectors.
     * Uses replay=1 to ensure late subscribers get the latest frame.
     */
    private val frameFlow = MutableSharedFlow<Pair<Bitmap, Long>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Flow emitting navigation cues to the UI layer */
    private val navigationCueFlow = MutableSharedFlow<NavigationCue>()

    /** Critical threat objects that require immediate attention */
    private val criticalObjects = setOf("car", "truck", "bus", "bicycle", "motorcycle", "train")

    /** Important objects that warrant informational alerts when new */
    private val importantObjects =
        setOf("person", "car", "dog", "cat", "bicycle", "motorcycle", "truck", "bus")

    /** Timestamps for preventing alert spam */
    private var lastCriticalAlertTime = 0L
    private var lastInformationalAlertTime = 0L
    private var lastAmbientUpdateTime = 0L

    /** Object tracking for change detection */
    private var lastSeenObjects = emptySet<String>()

    override fun getNavigationCues(): Flow<NavigationCue> {
        if (navigationJob == null || navigationJob?.isActive == false) {
            startDirectorPipeline()
        }
        return navigationCueFlow
    }

    override suspend fun processNewFrame(image: ImageInput) {
        // Convert domain model to Bitmap and send to the channel
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        frameFlow.tryEmit(Pair(bitmap, System.currentTimeMillis()))
    }

    override fun stopNavigation() {
        navigationJob?.cancel()
        objectDetectorDataSource.close()
        gemmaDataSource.resetSession()
    }

    override fun describeScene(
        image: ImageInput,
        prompt: String
    ): Flow<NavigationCue> {
        TODO("Manual scene description not yet implemented")
    }

    /**
     * Starts the Director Pipeline with intelligent decision tree for navigation cues.
     *
     * Implements a 4-rule decision system:
     * 1. Critical threats (immediate obstacles)
     * 2. Important new objects
     * 3. Periodic ambient updates
     * 4. Stable state (power saving)
     */
    private fun startDirectorPipeline() {
        navigationJob = repositoryScope.launch {
            var currentFrame: Bitmap? = null

            launch {
                frameFlow.collect { (bitmap, _) ->
                    currentFrame = bitmap
                }
            }

            objectDetectorDataSource.getDetectionStream(frameFlow)
                .collect { detectedObjects ->
                    val currentTime = System.currentTimeMillis()
                    val currentObjectLabels = detectedObjects.toSet()

                    // Rule 1: Check for CRITICAL threats
                    if (checkCriticalThreats(detectedObjects, currentTime)) {
                        currentFrame?.let { frame ->
                            triggerCriticalAlert(frame)
                        }
                        lastSeenObjects = currentObjectLabels
                        return@collect
                    }

                    // Rule 2: Check for IMPORTANT new objects
                    val newImportantObjects = findNewImportantObjects(currentObjectLabels)
                    if (newImportantObjects.isNotEmpty() &&
                        (currentTime - lastInformationalAlertTime) > 5000
                    ) {

                        currentFrame?.let { frame ->
                            triggerInformationalAlert(frame, newImportantObjects)
                        }
                        lastInformationalAlertTime = currentTime
                        lastSeenObjects = currentObjectLabels
                        return@collect
                    }

                    // Rule 3: Check for AMBIENT update timing
                    if ((currentTime - lastAmbientUpdateTime) > 30000) { // 30 seconds
                        currentFrame?.let { frame ->
                            triggerAmbientUpdate(frame)
                        }
                        lastAmbientUpdateTime = currentTime
                        lastSeenObjects = currentObjectLabels
                        return@collect
                    }

                    // Rule 4: STABLE state - do nothing, just update tracking
                    lastSeenObjects = currentObjectLabels
                }
        }
    }

    /**
     * Rule 1: Checks for critical threats requiring immediate attention.
     *
     * @param detectedObjects Current frame's detected objects with bounding boxes
     * @param currentTime Current timestamp
     * @return true if critical threat detected
     */
    private fun checkCriticalThreats(detectedObjects: List<String>, currentTime: Long): Boolean {
        // For now, using simplified logic until we get bounding box data from ObjectDetectorDataSource
        val criticalObjectsPresent = detectedObjects.filter { it in criticalObjects }

        if (criticalObjectsPresent.isNotEmpty() &&
            (currentTime - lastCriticalAlertTime) > 3000
        ) { // 3 second cooldown for critical
            lastCriticalAlertTime = currentTime
            return true
        }

        return false
    }

    /**
     * Rule 2: Finds new important objects that warrant informational alerts.
     *
     * @param currentObjects Set of currently detected object labels
     * @return List of new important objects
     */
    private fun findNewImportantObjects(currentObjects: Set<String>): List<String> {
        val newObjects = currentObjects - lastSeenObjects
        return newObjects.filter { it in importantObjects }
    }

    /**
     * Triggers a critical alert for immediate threats.
     */
    private suspend fun triggerCriticalAlert(frame: Bitmap) {
        val prompt = "IMMEDIATE OBSTACLE. NAME IT IN 3 WORDS."

        gemmaDataSource.generateResponse(prompt, frame)
            .collect { (partialResponse, isDone) ->
                val criticalAlert = NavigationCue.CriticalAlert(partialResponse, isDone)
                navigationCueFlow.emit(criticalAlert)
            }
    }

    /**
     * Triggers an informational alert for new important objects.
     */
    private suspend fun triggerInformationalAlert(frame: Bitmap, newObjects: List<String>) {
        val objectContext = if (newObjects.isNotEmpty()) {
            " A new ${newObjects.first()} has appeared."
        } else {
            ""
        }

        val prompt = "fast describe image, just most important for a blind.$objectContext"

        gemmaDataSource.generateResponse(prompt, frame)
            .collect { (partialResponse, isDone) ->
                val informationalAlert = NavigationCue.InformationalAlert(partialResponse, isDone)
                navigationCueFlow.emit(informationalAlert)
            }
    }

    /**
     * Triggers an ambient update for general environmental context.
     */
    private suspend fun triggerAmbientUpdate(frame: Bitmap) {
        val prompt = "Briefly describe the general surroundings for a blind user."

        gemmaDataSource.generateResponse(prompt, frame)
            .collect { (partialResponse, isDone) ->
                val ambientUpdate = NavigationCue.AmbientUpdate(partialResponse, isDone)
                navigationCueFlow.emit(ambientUpdate)
            }
    }
}