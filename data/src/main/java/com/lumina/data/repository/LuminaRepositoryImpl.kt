package com.lumina.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.lumina.data.datasource.AiDataSource
import com.lumina.data.datasource.ObjectDetectorDataSource
import com.lumina.data.datasource.TimestampedFrame
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LuminaRepositoryImpl"

/**
 * Implementation of [LuminaRepository] that coordinates between AI data sources and object detection
 * to provide intelligent navigation assistance for visually impaired users.
 *
 * This repository implements an intelligent "Director Pattern" with optimized motion analysis:
 * - Rule 1: Critical threats (immediate obstacles with motion context)
 * - Rule 2: Important new objects appearing (with movement tracking)
 * - Rule 3: Periodic ambient updates (with scene changes)
 * - Rule 4: Stable state (power saving mode)
 *
 * Motion Analysis Features:
 * - Collects 3-5 frames over 200-300ms windows
 * - Provides timing context to AI for movement detection
 * - Optimized for fast response times while maintaining accuracy
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

    /**
     * Circular buffer for timestamped frames optimized for motion analysis.
     * Maintains frames to allow sampling 2 frames ~30 frames apart for optimal motion detection.
     */
    private val frameBuffer = ArrayDeque<TimestampedFrame>(35) // Increased to hold more frames

    /** Frame sampling interval for motion analysis (every 30th frame) */
    private val motionSamplingInterval = 30

    /** Counter to track frame sampling for motion analysis */
    private var frameCounter = 0

    /** Maximum time span for frame sequences (in milliseconds) */
    private val maxFrameSequenceTimeMs = 1000L // Increased to allow for 30-frame spacing

    override fun getNavigationCues(): Flow<NavigationCue> {
        if (navigationJob == null || navigationJob?.isActive == false) {
            startDirectorPipeline()
        }
        return navigationCueFlow
    }

    override suspend fun processNewFrame(image: ImageInput) {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        val timestamp = System.currentTimeMillis()
        frameFlow.tryEmit(Pair(bitmap, timestamp))
    }

    override fun stopNavigation() {
        navigationJob?.cancel()
        objectDetectorDataSource.close()
        gemmaDataSource.resetSession()
        frameBuffer.clear()
    }

    override fun describeScene(
        image: ImageInput,
        prompt: String
    ): Flow<NavigationCue> {
        // Converts the encoded image bytes to a Bitmap required by GemmaAI.
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)

        /*
         * Uses GemmaAiDataSource’s single-image pipeline to obtain a detailed
         * description.  The response stream is mapped to an InformationalAlert so
         * it can be consumed by the UI and TTS layers without special-case logic.
         */
        return gemmaDataSource.generateResponse(prompt, bitmap)
            .map { (partialResult, done) ->
                NavigationCue.InformationalAlert(partialResult, done)
            }
            .onCompletion {
                // Releases native memory once streaming completes.
                bitmap.recycle()
            }
    }

    /**
     * Starts the Director Pipeline with intelligent decision tree and motion analysis.
     */
    private fun startDirectorPipeline() {
        navigationJob = repositoryScope.launch {
            launch {
                frameFlow.collect { (bitmap, timestamp) ->
                    updateFrameBuffer(bitmap, timestamp)
                }
            }

            objectDetectorDataSource.getDetectionStream(frameFlow)
                .collect { detectedObjects ->
                    Log.i(TAG, "Detected objects: $detectedObjects")
                    val currentTime = System.currentTimeMillis()
                    val currentObjectLabels = detectedObjects.toSet()
                    val motionFrames = getMotionAnalysisFrames()

                    // Rule 1: Check for CRITICAL threats with motion analysis
                    if (checkCriticalThreats(detectedObjects, currentTime)) {
                        if (motionFrames.isNotEmpty()) {
                            triggerCriticalAlert(motionFrames)
                        }
                        lastSeenObjects = currentObjectLabels
                        return@collect
                    }

                    // Rule 2: Check for IMPORTANT new objects with movement context
                    val newImportantObjects = findNewImportantObjects(currentObjectLabels)
                    if (newImportantObjects.isNotEmpty() &&
                        (currentTime - lastInformationalAlertTime) > 5000
                    ) {
                        if (motionFrames.isNotEmpty()) {
                            triggerInformationalAlert(motionFrames, newImportantObjects)
                        }
                        lastInformationalAlertTime = currentTime
                        lastSeenObjects = currentObjectLabels
                        return@collect
                    }

                    // Rule 3: Check for AMBIENT update timing with scene context
                    if ((currentTime - lastAmbientUpdateTime) > 300000) { // 30 seconds
                        if (motionFrames.isNotEmpty()) {
                            triggerAmbientUpdate(motionFrames)
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
     * Updates the frame buffer with new timestamped frames.
     * Maintains optimal frame count and timing for motion analysis with 30-frame sampling.
     */
    private fun updateFrameBuffer(bitmap: Bitmap, timestamp: Long) {
        synchronized(frameBuffer) {
            frameCounter++

            // Only add every frame to maintain continuous sampling capability
            frameBuffer.add(TimestampedFrame(bitmap, timestamp))

            // Remove old frames that exceed buffer size
            while (frameBuffer.size > 35) {
                frameBuffer.removeFirst()
            }

            // Remove frames that are too old for motion analysis
            val cutoffTime = timestamp - maxFrameSequenceTimeMs
            while (frameBuffer.isNotEmpty() && frameBuffer.first().timestampMs < cutoffTime) {
                frameBuffer.removeFirst()
            }
        }
    }

    /**
     * Gets 2 frames spaced approximately 30 frames apart for optimal motion detection.
     * Returns the most recent frame and a frame from ~30 frames ago.
     */
    private fun getMotionAnalysisFrames(): List<TimestampedFrame> {
        synchronized(frameBuffer) {
            if (frameBuffer.size < 2) {
                return frameBuffer.toList()
            }

            val latestFrame = frameBuffer.last()

            // Try to get a frame that's approximately 30 frames back
            val targetIndex = maxOf(0, frameBuffer.size - 1 - motionSamplingInterval)
            val olderFrame = if (targetIndex < frameBuffer.size) {
                frameBuffer[targetIndex]
            } else {
                frameBuffer.first() // Fallback to oldest available frame
            }

            // Return in chronological order (older frame first, then latest)
            return if (olderFrame != latestFrame) {
                listOf(olderFrame, latestFrame)
            } else {
                listOf(latestFrame) // Only one frame available
            }
        }
    }

    /**
     * Rule 1: Checks for critical threats requiring immediate attention.
     */
    private fun checkCriticalThreats(detectedObjects: List<String>, currentTime: Long): Boolean {
        val criticalObjectsPresent = detectedObjects.filter { it in criticalObjects }

        if (criticalObjectsPresent.isNotEmpty() &&
            (currentTime - lastCriticalAlertTime) > 3000 // 3 second cooldown
        ) {
            lastCriticalAlertTime = currentTime
            return true
        }

        return false
    }

    /**
     * Rule 2: Finds new important objects that warrant informational alerts.
     */
    private fun findNewImportantObjects(currentObjects: Set<String>): List<String> {
        val newObjects = currentObjects - lastSeenObjects
        return newObjects.filter { it in importantObjects }
    }

    /**
     * Triggers a critical alert with motion analysis for immediate threats.
     */
    private suspend fun triggerCriticalAlert(frames: List<TimestampedFrame>) {
        val prompt = "IMMEDIATE OBSTACLE. NAME IT IN 3 WORDS."
        Log.i(TAG, "triggerCriticalAlert: ")
        gemmaDataSource.generateResponse(prompt, frames)
            .collect { (partialResponse, isDone) ->
                val criticalAlert = NavigationCue.CriticalAlert(partialResponse, isDone)
                navigationCueFlow.emit(criticalAlert)
            }
    }

    /**
     * Triggers an informational alert with movement context for new important objects.
     */
    private suspend fun triggerInformationalAlert(
        frames: List<TimestampedFrame>,
        newObjects: List<String>
    ) {
        Log.i(TAG, "triggerInformationalAlert: ")
        val objectContext = if (newObjects.isNotEmpty()) {
            " A new ${newObjects.first()} has appeared."
        } else {
            ""
        }

        val prompt = "fast describe image, just most important for a blind."

        gemmaDataSource.generateResponse(prompt, frames)
            .collect { (partialResponse, isDone) ->
                val informationalAlert = NavigationCue.InformationalAlert(partialResponse, isDone)
                navigationCueFlow.emit(informationalAlert)
            }
    }

    /**
     * Triggers an ambient update with scene change detection.
     */
    private suspend fun triggerAmbientUpdate(frames: List<TimestampedFrame>) {
        val prompt = "Briefly describe the general surroundings for a blind user."
        Log.i(TAG, "triggerAmbientUpdate: ")
        gemmaDataSource.generateResponse(prompt, frames)
            .collect { (partialResponse, isDone) ->
                val ambientUpdate = NavigationCue.AmbientUpdate(partialResponse, isDone)
                navigationCueFlow.emit(ambientUpdate)
            }
    }
}