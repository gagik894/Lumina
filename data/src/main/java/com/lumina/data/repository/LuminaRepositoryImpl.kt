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
 * The primary long-running modes are:
 * - **NAVIGATION**: The default mode, providing continuous environmental awareness through the
 *   intelligent "Director Pattern".
 * - **CROSSING**: A specialized mode for providing street crossing guidance.
 *
 * Transient, user-initiated tasks like `findObject` or `askQuestion` will temporarily pause any
 * active long-running mode, execute exclusively, and then resume the previous mode upon completion.
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
     * Defines the exclusive, long-running operational modes of the repository.
     * Only one of these modes can be active at a time.
     */
    private enum class OperatingMode {
        NAVIGATION,
        CROSSING,
        DETECTOR
    }

    /** The currently active long-running mode. */
    private var activeMode: OperatingMode? = null

    /** The mode that was paused by a transient operation (e.g., findObject). */
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
     * Pauses the currently active long-running mode (e.g., NAVIGATION, CROSSING) to allow
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
            OperatingMode.CROSSING -> startCrossingMode()
            OperatingMode.DETECTOR -> startDetectorMode()
            null -> {
                // If no mode was paused, default to starting the main navigation pipeline.
                startDirectorPipeline()
            }
        }
        pausedMode = null
    }

    private fun startDetectorMode() {
        if (activeMode == OperatingMode.DETECTOR) return
        Log.d(TAG, "Starting DETECTOR mode")

        activeJob?.cancel()
        activeMode = OperatingMode.DETECTOR
        activeJob = repositoryScope.launch {
            objectDetectorDataSource.getDetectionStream(frameFlow)
                .collectLatest { detectedObjects ->
                    if (!isActive) return@collectLatest

                    Log.i(TAG, "Detected objects: $detectedObjects")
                    val currentTime = System.currentTimeMillis()
                    val currentObjectLabels = detectedObjects.toSet()

                    if (checkCriticalThreats(detectedObjects, currentTime)) {
                        navigationCueFlow.tryEmit(
                            NavigationCue.CriticalAlert(
                                "Critical threat detected!",
                                true
                            )
                        )
                        lastSeenObjects = currentObjectLabels
                        return@collectLatest
                    }

                    val newImportantObjects = findNewImportantObjects(currentObjectLabels)
                    if (newImportantObjects.isNotEmpty() && (currentTime - lastInformationalAlertTime) > 5000) {
                        navigationCueFlow.tryEmit(
                            NavigationCue.InformationalAlert(
                                "New object: ${newImportantObjects.first()}",
                                true
                            )
                        )
                        lastInformationalAlertTime = currentTime
                        lastSeenObjects = currentObjectLabels
                        return@collectLatest
                    }

                    lastSeenObjects = currentObjectLabels
                }
        }
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

    /**
     * Starts the Director Pipeline, the primary navigation mode.
     * This mode is responsible for providing continuous environmental awareness.
     * If another mode is active, it will be cancelled.
     */
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

                    // Rule 1: Check for CRITICAL threats with motion analysis
                    if (checkCriticalThreats(detectedObjects, currentTime)) {
                        if (motionFrames.isNotEmpty()) triggerCriticalAlert(motionFrames)
                        lastSeenObjects = currentObjectLabels
                        return@collectLatest
                    }

                    // Rule 2: Check for IMPORTANT new objects with movement context
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

                    // Rule 4: STABLE state - do nothing, just update tracking
                    lastSeenObjects = currentObjectLabels
                }
        }
    }

    /**
     * Updates the frame buffer with new timestamped frames.
     * This method is synchronized to ensure thread-safe access to the buffer.
     */
    private fun updateFrameBuffer(bitmap: Bitmap, timestamp: Long) {
        synchronized(frameBuffer) {
            // Add the new frame and remove the oldest if the buffer exceeds its capacity.
            frameBuffer.add(TimestampedFrame(bitmap, timestamp))
            if (frameBuffer.size > 35) {
                frameBuffer.removeFirst()
            }
        }
    }

    /**
     * Gets a list of frames suitable for motion analysis.
     * This method is synchronized to ensure thread-safe access to the buffer.
     */
    private fun getMotionAnalysisFrames(): List<TimestampedFrame> {
        synchronized(frameBuffer) {
            return FrameSelector.selectMotionFrames(frameBuffer.toList())
        }
    }

    /**
     * Rule 1: Checks for critical threats requiring immediate attention.
     */
    private fun checkCriticalThreats(detectedObjects: List<String>, currentTime: Long): Boolean {
        val criticalObjectsPresent = detectedObjects.any { it in criticalObjects }

        if (criticalObjectsPresent && (currentTime - lastCriticalAlertTime) > 3000) { // 3-second cooldown
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

    /**
     * Triggers an ambient update with scene change detection.
     */
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

    override fun startCrossingMode() {
        if (activeMode == OperatingMode.CROSSING) return
        Log.d(TAG, "Starting CROSSING mode")

        activeJob?.cancel()
        activeMode = OperatingMode.CROSSING
        activeJob = repositoryScope.launch {
            frameFlow.collectLatest { // Use collectLatest to process only the newest frame
                val frames = getMotionAnalysisFrames()
                if (frames.isEmpty()) return@collectLatest

                val prompt =
                    "You are in CROSSING MODE. Guide the user with WAIT, CROSS, or ADJUST LEFT/RIGHT in <=3 words.";

                generateSerializedResponse(prompt, frames)
                    .collect { (chunk, done) ->
                        if (chunk.isNotBlank())
                            navigationCueFlow.emit(NavigationCue.CriticalAlert(chunk, done))
                    }
            }
        }
    }

    override fun stopCrossingMode() {
        if (activeMode == OperatingMode.CROSSING) {
            activeJob?.cancel()
            activeJob = null
            activeMode = null
        }
        // As per original logic, default back to navigation mode.
        Log.d(TAG, "Stopping CROSSING mode, returning to NAVIGATION")
        startDirectorPipeline()
    }

    /**
     * A wrapper around the AI data source's `generateResponse` function that ensures all calls
     * are serialized via the `aiMutex`. This prevents concurrency issues with the underlying
     * AI library.
     */
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
