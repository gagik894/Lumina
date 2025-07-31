package com.lumina.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.lumina.data.datasource.AiDataSource
import com.lumina.data.datasource.ObjectDetectorDataSource
import com.lumina.data.datasource.TimestampedFrame
import com.lumina.data.util.FrameSelector
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LuminaRepositoryImpl"

/**
 * Implementation of [LuminaRepository] that coordinates between AI data sources and object detection
 * to provide intelligent navigation assistance for visually impaired users.
 *
 * This repository implements a state machine to manage different operating modes, ensuring that
 * only one major function (e.g., general navigation, finding an object) is active at any given time.
 * This prevents conflicting operations and resource contention.
 *
 * The primary long-running mode is:
 * - **NAVIGATION**: The default mode, providing continuous environmental awareness through the
 *   intelligent "Director Pattern".
 *
 * Transient, user-initiated tasks like `findObject`, `askQuestion`, or `startCrossingMode` will
 * temporarily pause the NAVIGATION mode, execute exclusively, and then resume it upon completion.
 *
 * @param gemmaDataSource The AI data source responsible for generating scene descriptions.
 * @param objectDetectorDataSource The data source for detecting objects in camera frames.
 */
@Singleton
class LuminaRepositoryImpl @Inject constructor(
    private val gemmaDataSource: AiDataSource,
    private val objectDetectorDataSource: ObjectDetectorDataSource
) : LuminaRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    override val initializationState: StateFlow<InitializationState> =
        gemmaDataSource.initializationState

    /**
     * Defines the exclusive, long-running operational mode of the repository.
     * NAVIGATION is the only long-running mode, which integrates the object detector.
     */
    private enum class OperatingMode {
        NAVIGATION
    }

    /** The currently active long-running mode. */
    private var activeMode: OperatingMode? = null

    /** The mode that was paused by a transient operation. */
    private var pausedMode: OperatingMode? = null

    /** The [Job] associated with the currently active long-running mode. */
    private var activeJob: Job? = null

    /**
     * A mutex to ensure that all AI generation requests are serialized, preventing the
     * `IllegalStateException` from the underlying AI library.
     */
    private val aiMutex = Mutex()

    /**
     * A mutex to ensure that transient, user-initiated operations like `findObject` or `askQuestion`
     * run exclusively and do not conflict with each other. An operation must acquire the lock
     * before executing and release it upon completion.
     */
    private val transientOperationMutex = Mutex()


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

    /** Object tracking for change detection */
    private var lastSeenObjects = emptySet<String>()

    /**
     * Circular buffer for timestamped frames optimized for motion analysis.
     * Maintains frames to allow sampling 2 frames ~30 frames apart for optimal motion detection.
     */
    private val frameBuffer = ArrayDeque<TimestampedFrame>(35) // Increased to hold more frames

    companion object {
        private val COCO_LABELS = setOf(
            "person",
            "bicycle",
            "car",
            "motorcycle",
            "airplane",
            "bus",
            "train",
            "truck",
            "boat",
            "traffic light",
            "fire hydrant",
            "stop sign",
            "parking meter",
            "bench",
            "bird",
            "cat",
            "dog",
            "horse",
            "sheep",
            "cow",
            "elephant",
            "bear",
            "zebra",
            "giraffe",
            "backpack",
            "umbrella",
            "handbag",
            "tie",
            "suitcase",
            "frisbee",
            "skis",
            "snowboard",
            "sports ball",
            "kite",
            "baseball bat",
            "baseball glove",
            "skateboard",
            "surfboard",
            "tennis racket",
            "bottle",
            "wine glass",
            "cup",
            "fork",
            "knife",
            "spoon",
            "bowl",
            "banana",
            "apple",
            "sandwich",
            "orange",
            "broccoli",
            "carrot",
            "hot dog",
            "pizza",
            "donut",
            "cake",
            "chair",
            "couch",
            "potted plant",
            "bed",
            "dining table",
            "toilet",
            "tv",
            "laptop",
            "mouse",
            "remote",
            "keyboard",
            "cell phone",
            "microwave",
            "oven",
            "toaster",
            "sink",
            "refrigerator",
            "book",
            "clock",
            "vase",
            "scissors",
            "teddy bear",
            "hair drier",
            "toothbrush"
        )
    }

    init {
        // Start a long-running job to continuously populate the frame buffer.
        // This ensures that up-to-date frames are always available for any operation.
        repositoryScope.launch {
            frameFlow.collect { (bitmap, timestamp) ->
                updateFrameBuffer(bitmap, timestamp)
            }
        }
    }

    override fun getNavigationCues(): Flow<NavigationCue> {
        startDirectorPipeline()
        return navigationCueFlow
    }

    override suspend fun processNewFrame(image: ImageInput) {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        val timestamp = System.currentTimeMillis()
        frameFlow.tryEmit(Pair(bitmap, timestamp))
    }

    override fun stopNavigation() {
        activeJob?.cancel()
        activeJob = null
        activeMode = null
        pausedMode = null
        objectDetectorDataSource.close()
        gemmaDataSource.resetSession()
        frameBuffer.clear()
    }

    /**
     * Pauses the currently active long-running mode (e.g., NAVIGATION) to allow
     * a transient operation to run. The paused mode is recorded so it can be resumed later.
     */
    private fun pauseActiveMode() {
        if (activeJob?.isActive == true) {
            Log.d(TAG, "Pausing active mode: $activeMode")
            pausedMode = activeMode
            activeJob?.cancel()
        } else {
            pausedMode = null
        }
        activeJob = null
        activeMode = null
        objectDetectorDataSource.close() // Always close detector when no continuous mode is active
    }

    /**
     * Resumes the previously paused long-running mode.
     */
    private fun resumePausedMode() {
        if (activeJob?.isActive == true) return // A mode is already running

        Log.d(TAG, "Resuming paused mode: $pausedMode")
        when (pausedMode) {
            OperatingMode.NAVIGATION -> startDirectorPipeline()
            null -> {
                // If no mode was paused, default to starting the main navigation pipeline.
                Log.d(TAG, "No paused mode found, starting default navigation pipeline.")
                startDirectorPipeline()
            }
        }
        pausedMode = null
    }

    /**
     * CORRECTED: This now correctly pauses navigation and runs as an exclusive transient operation.
     */
    override fun describeScene(
        image: ImageInput,
        prompt: String
    ): Flow<NavigationCue> {
        val flow = callbackFlow {
            transientOperationMutex.withLock {
                if (!isActive) return@withLock

                val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)

                try {
                    generateSerializedResponse(prompt, bitmap)
                        .collect { (chunk, done) ->
                            trySend(NavigationCue.InformationalAlert(chunk, done))
                            if (done) close()
                        }
                } finally {
                    // Ensure bitmap is recycled even on error or cancellation
                    bitmap.recycle()
                }
            }
            awaitClose { }
        }

        return flow
            .onStart { pauseActiveMode() }
            .onCompletion { resumePausedMode() }
    }


    override fun findObject(target: String): Flow<NavigationCue> {
        val normalizedTarget = target.trim().lowercase()

        val baseFlow = callbackFlow {
            transientOperationMutex.withLock {
                if (!isActive) return@withLock

                if (normalizedTarget in COCO_LABELS) {
                    // Use fast object detector for known COCO categories
                    val detectionsJob = repositoryScope.launch {
                        objectDetectorDataSource.getDetectionStream(frameFlow)
                            .collectLatest { detections ->
                                if (detections.any {
                                        it.equals(
                                            normalizedTarget,
                                            ignoreCase = true
                                        )
                                    }) {
                                    trySend(
                                        NavigationCue.InformationalAlert(
                                            "$target detected",
                                            false
                                        )
                                    )
                                    val latestFrame =
                                        synchronized(frameBuffer) { frameBuffer.lastOrNull() }
                                    if (latestFrame == null) return@collectLatest

                                    val prompt =
                                        "Describe the location of the $target relative to the user."
                                    objectDetectorDataSource.setPaused(true)
                                    try {
                                        generateSerializedResponse(prompt, latestFrame.bitmap)
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
                                    } finally {
                                        objectDetectorDataSource.setPaused(false)
                                    }
                                }
                            }
                    }
                    awaitClose { detectionsJob.cancel() }
                } else {
                    // Fallback to Gemma vision capabilities for arbitrary objects
                    val detectionPrompt =
                        "Answer yes or no only. Do you see a $target in the image?"
                    val locationPrompt =
                        "Describe the location of the $target relative to the user."
                    val collectorJob = repositoryScope.launch {
                        frameFlow.collectLatest { (bitmap, _) ->
                            val affirmative = generateSerializedResponse(detectionPrompt, bitmap)
                                .map { it.first }
                                .first()
                                .trim()
                                .lowercase()
                                .startsWith("y")

                            if (affirmative) {
                                trySend(NavigationCue.InformationalAlert("$target detected", false))
                                generateSerializedResponse(locationPrompt, bitmap)
                                    .collect { (chunk, done) ->
                                        if (chunk.isNotBlank()) {
                                            trySend(NavigationCue.InformationalAlert(chunk, done))
                                        }
                                        if (done) this@callbackFlow.close()
                                    }
                            }
                        }
                    }
                    awaitClose { collectorJob.cancel() }
                }
            }
        }

        return baseFlow
            .onStart { pauseActiveMode() }
            .onCompletion { resumePausedMode() }
    }

    override fun askQuestion(question: String): Flow<NavigationCue> {
        val flow = callbackFlow {
            transientOperationMutex.withLock {
                if (!isActive) return@withLock

                val latestFrame = synchronized(frameBuffer) { frameBuffer.lastOrNull()?.bitmap }
                if (latestFrame == null) {
                    trySend(NavigationCue.InformationalAlert("Camera frame not available.", true))
                    close()
                    return@withLock
                }

                val prompt = "Based on the image, answer the user's question: $question"
                generateSerializedResponse(prompt, latestFrame)
                    .collect { (chunk, done) ->
                        trySend(NavigationCue.InformationalAlert(chunk, done))
                        if (done) close()
                    }
            }
            awaitClose { }
        }

        return flow
            .onStart { pauseActiveMode() }
            .onCompletion { resumePausedMode() }
    }

    private fun startDirectorPipeline() {
        if (activeMode == OperatingMode.NAVIGATION) return // Already in this mode
        Log.d(TAG, "Starting Director Pipeline (NAVIGATION mode)")

        activeJob?.cancel() // Cancel any other active mode
        activeMode = OperatingMode.NAVIGATION
        activeJob = repositoryScope.launch {
            objectDetectorDataSource.getDetectionStream(frameFlow)
                .collectLatest { detectedObjects ->
                    if (!isActive) return@collectLatest

                    Log.i(TAG, "Detected objects: $detectedObjects")
                    val currentTime = System.currentTimeMillis()
                    val currentObjectLabels = detectedObjects.toSet()
                    val motionFrames = getMotionAnalysisFrames()

                    if (checkCriticalThreats(detectedObjects, currentTime)) {
                        if (motionFrames.isNotEmpty()) triggerCriticalAlert(motionFrames)
                        lastSeenObjects = currentObjectLabels
                        return@collectLatest
                    }

                    val newImportantObjects = findNewImportantObjects(currentObjectLabels)
                    if (newImportantObjects.isNotEmpty() && (currentTime - lastInformationalAlertTime) > 5000) {
                        if (motionFrames.isNotEmpty()) triggerInformationalAlert(
                            motionFrames,
                            newImportantObjects
                        )
                        lastInformationalAlertTime = currentTime
                        lastSeenObjects = currentObjectLabels
                        return@collectLatest
                    }

                    lastSeenObjects = currentObjectLabels
                }
        }
    }

    private fun updateFrameBuffer(bitmap: Bitmap, timestamp: Long) {
        synchronized(frameBuffer) {
            frameBuffer.add(TimestampedFrame(bitmap, timestamp))
            if (frameBuffer.size > 35) {
                frameBuffer.removeFirst()
            }
        }
    }

    private fun getMotionAnalysisFrames(): List<TimestampedFrame> {
        synchronized(frameBuffer) {
            return FrameSelector.selectMotionFrames(frameBuffer.toList())
        }
    }

    private fun checkCriticalThreats(detectedObjects: List<String>, currentTime: Long): Boolean {
        val criticalObjectsPresent = detectedObjects.any { it in criticalObjects }

        if (criticalObjectsPresent && (currentTime - lastCriticalAlertTime) > 3000) { // 3-second cooldown
            lastCriticalAlertTime = currentTime
            return true
        }

        return false
    }

    private fun findNewImportantObjects(currentObjects: Set<String>): List<String> {
        val newObjects = currentObjects - lastSeenObjects
        return newObjects.filter { it in importantObjects }
    }

    private suspend fun triggerCriticalAlert(frames: List<TimestampedFrame>) {
        val prompt = "IMMEDIATE OBSTACLE. NAME IT IN 3 WORDS."
        Log.i(TAG, "triggerCriticalAlert: ")
        objectDetectorDataSource.setPaused(true)
        try {
            generateSerializedResponse(prompt, frames)
                .collect { (partialResponse, isDone) ->
                    val criticalAlert = NavigationCue.CriticalAlert(partialResponse, isDone)
                    navigationCueFlow.emit(criticalAlert)
                }
        } finally {
            objectDetectorDataSource.setPaused(false)
        }
    }

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

        val prompt = "fast describe image, just most important for a blind.$objectContext"

        objectDetectorDataSource.setPaused(true)
        try {
            generateSerializedResponse(prompt, frames)
                .collect { (partialResponse, isDone) ->
                    val informationalAlert =
                        NavigationCue.InformationalAlert(partialResponse, isDone)
                    navigationCueFlow.emit(informationalAlert)
                }
        } finally {
            objectDetectorDataSource.setPaused(false)
        }
    }

    private suspend fun triggerAmbientUpdate(frames: List<TimestampedFrame>) {
        val prompt = "Briefly describe the general surroundings for a blind user."
        Log.i(TAG, "triggerAmbientUpdate: ")
        objectDetectorDataSource.setPaused(true)
        try {
            generateSerializedResponse(prompt, frames)
                .collect { (partialResponse, isDone) ->
                    val ambientUpdate = NavigationCue.AmbientUpdate(partialResponse, isDone)
                    navigationCueFlow.emit(ambientUpdate)
                }
        } finally {
            objectDetectorDataSource.setPaused(false)
        }

    }

    override fun startCrossingMode(): Flow<NavigationCue> {
        val flow = callbackFlow {
            transientOperationMutex.withLock {
                if (!isActive) return@withLock

                Log.d(TAG, "Starting transient CROSSING operation")
                val crossingJob = repositoryScope.launch {
                    frameFlow.collectLatest {
                        val frames = getMotionAnalysisFrames()
                        if (frames.isEmpty()) return@collectLatest

                        val prompt = """
                        You are in CROSSING MODE. Guide the user with WAIT, CROSS, or ADJUST LEFT/RIGHT in <=3 words.
                        IMPORTANT: Once you determine the user has safely crossed the street, you MUST respond with the exact phrase 'CROSSING COMPLETE' and nothing else.
                        """.trimIndent()

                        try {
                            generateSerializedResponse(prompt, frames)
                                .collect { (chunk, done) ->
                                    if (chunk.contains("CROSSING COMPLETE", ignoreCase = true)) {
                                        Log.i(TAG, "Crossing complete signal received from AI.")
                                        trySend(
                                            NavigationCue.InformationalAlert(
                                                "Crossing complete.",
                                                true
                                            )
                                        )
                                        this@callbackFlow.close() // Close the flow to stop the operation.
                                    } else if (chunk.isNotBlank()) {
                                        trySend(NavigationCue.CriticalAlert(chunk, done))
                                    }
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during crossing guidance", e)
                            this@callbackFlow.close(e)
                        }
                    }
                }
                awaitClose {
                    Log.d(TAG, "Closing transient CROSSING operation.")
                    crossingJob.cancel()
                }
            }
        }

        return flow
            .onStart { pauseActiveMode() }
            .onCompletion { resumePausedMode() }
    }

    private suspend fun generateSerializedResponse(
        prompt: String,
        frames: List<TimestampedFrame>
    ): Flow<Pair<String, Boolean>> {
        return aiMutex.withLock {
            gemmaDataSource.generateResponse(prompt, frames)
        }
    }

    private suspend fun generateSerializedResponse(
        prompt: String,
        bitmap: Bitmap
    ): Flow<Pair<String, Boolean>> {
        return aiMutex.withLock {
            gemmaDataSource.generateResponse(prompt, bitmap)
        }
    }
}