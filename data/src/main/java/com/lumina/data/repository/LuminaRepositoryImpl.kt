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
 * This refactored implementation follows the single responsibility principle by delegating
 * specific concerns to specialized helper classes:
 * - [FrameBufferManager]: Manages camera frame buffering and motion analysis
 * - [NavigationModeManager]: Handles navigation mode lifecycle and state transitions
 * - [ThreatAssessmentManager]: Analyzes detected objects for threat classification
 * - [PromptGenerator]: Generates contextually appropriate AI prompts
 * - [AlertCoordinator]: Coordinates alert generation and emission
 * - [TransientOperationCoordinator]: Manages exclusive execution of user-initiated operations
 *
 * The repository now serves as a lightweight orchestrator that maintains the public API
 * contract while delegating complex logic to specialized components. This design improves
 * testability, maintainability, and follows Google's recommended architecture patterns.
 *
 * @param gemmaDataSource The AI data source responsible for generating scene descriptions
 * @param objectDetectorDataSource The data source for detecting objects in camera frames
 * @param frameBufferManager Manages the circular buffer of camera frames
 * @param navigationModeManager Handles navigation mode lifecycle and transitions
 * @param threatAssessmentManager Analyzes threats and determines alert priorities
 * @param promptGenerator Generates contextually appropriate AI prompts
 * @param alertCoordinator Coordinates alert generation and emission
 * @param transientOperationCoordinator Manages exclusive execution of transient operations
 */
@Singleton
class LuminaRepositoryImpl @Inject constructor(
    private val gemmaDataSource: AiDataSource,
    private val objectDetectorDataSource: ObjectDetectorDataSource,
    private val frameBufferManager: FrameBufferManager,
    private val navigationModeManager: NavigationModeManager,
    private val threatAssessmentManager: ThreatAssessmentManager,
    private val promptGenerator: PromptGenerator,
    private val alertCoordinator: AlertCoordinator,
    private val transientOperationCoordinator: TransientOperationCoordinator
) : LuminaRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    override val initializationState: StateFlow<InitializationState> =
        gemmaDataSource.initializationState

    /**
     * A mutex to ensure that all AI generation requests are serialized, preventing the
     * `IllegalStateException` from the underlying AI library.
     */
    private val aiMutex = Mutex()

    /**
     * Shared flow broadcasting camera frames to multiple collectors.
     * Uses replay=1 to ensure late subscribers get the latest frame.
     */
    private val frameFlow = MutableSharedFlow<Pair<Bitmap, Long>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Known COCO object detection labels for fast object detection fallback */
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
                frameBufferManager.addFrame(bitmap, timestamp)
            }
        }
    }

    override fun getNavigationCues(): Flow<NavigationCue> {
        startDirectorPipeline()
        return alertCoordinator.getNavigationCueFlow()
    }

    override suspend fun processNewFrame(image: ImageInput) {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        val timestamp = System.currentTimeMillis()
        frameFlow.tryEmit(Pair(bitmap, timestamp))
    }

    override fun stopNavigation() {
        navigationModeManager.stopAllModes()
        objectDetectorDataSource.setPaused(true)
        gemmaDataSource.resetSession()
        frameBufferManager.clear()
        threatAssessmentManager.reset()
    }

    /**
     * Pauses the active navigation mode to allow transient operations to run exclusively.
     *
     * This is a simplified wrapper around the navigation mode manager that also
     * pauses object detection to prevent resource conflicts.
     */
    private fun pauseNavigation() {
        navigationModeManager.pauseActiveMode()
        objectDetectorDataSource.setPaused(true)
    }

    /**
     * Starts the main navigation pipeline (Director Pattern) that provides continuous
     * environmental awareness through object detection and intelligent alert generation.
     *
     * This method integrates all the specialized components to create a coordinated
     * navigation experience that adapts to environmental changes and threat levels.
     */
    private fun startDirectorPipeline() {
        if (navigationModeManager.isActive(NavigationModeManager.OperatingMode.NAVIGATION)) {
            return // Already running
        }

        Log.d(TAG, "Starting Director Pipeline (NAVIGATION mode)")

        val navigationJob = repositoryScope.launch {
            objectDetectorDataSource.getDetectionStream(frameFlow)
                .collectLatest { detectedObjects ->
                    if (!isActive) return@collectLatest

                    Log.i(TAG, "Detected objects: $detectedObjects")
                    val currentTime = System.currentTimeMillis()

                    // Assess the threat level and determine appropriate response
                    val assessment = threatAssessmentManager.assessThreatLevel(
                        detectedObjects,
                        currentTime
                    )

                    // Get motion analysis frames for AI processing
                    val motionFrames = frameBufferManager.getMotionAnalysisFrames()

                    // Handle the assessment result with appropriate alert coordination
                    when (assessment) {
                        is ThreatAssessmentManager.AssessmentResult.CriticalAlert -> {
                            if (motionFrames.isNotEmpty()) {
                                alertCoordinator.coordinateCriticalAlert(
                                    assessment.detectedObjects,
                                    motionFrames,
                                    ::generateSerializedResponse
                                )
                            }
                        }

                        is ThreatAssessmentManager.AssessmentResult.InformationalAlert -> {
                            if (motionFrames.isNotEmpty()) {
                                alertCoordinator.coordinateInformationalAlert(
                                    assessment.newObjects,
                                    motionFrames,
                                    ::generateSerializedResponse
                                )
                            }
                        }

                        is ThreatAssessmentManager.AssessmentResult.AmbientUpdate -> {
                            if (motionFrames.isNotEmpty()) {
                                alertCoordinator.coordinateAmbientUpdate(
                                    motionFrames,
                                    ::generateSerializedResponse
                                )
                            }
                        }

                        ThreatAssessmentManager.AssessmentResult.NoAlert -> {
                            // Continue monitoring without generating alerts
                        }
                    }
                }
        }

        navigationModeManager.startMode(
            NavigationModeManager.OperatingMode.NAVIGATION,
            navigationJob
        )
    }

    override fun describeScene(
        image: ImageInput,
        prompt: String
    ): Flow<NavigationCue> {
        val flow = callbackFlow<NavigationCue> {
            transientOperationCoordinator.executeTransientOperation("describe_scene") {
                if (!isActive) return@executeTransientOperation

                val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                val contextualPrompt = promptGenerator.generateSceneDescriptionPrompt(prompt)

                try {
                    generateSerializedResponse(contextualPrompt, bitmap)
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
            .onStart { pauseNavigation() }
            .onCompletion { resumeNavigation() }
    }


    override fun findObject(target: String): Flow<NavigationCue> {
        val normalizedTarget = target.trim().lowercase()

        val baseFlow = callbackFlow<NavigationCue> {
            transientOperationCoordinator.executeTransientOperation("find_object_$target") {
                if (!isActive) return@executeTransientOperation

                if (normalizedTarget in COCO_LABELS) {
                    // Use fast object detector for known COCO categories
                    objectDetectorDataSource.setPaused(false)
                    
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

                                    val latestFrame = frameBufferManager.getLatestFrame()
                                    if (latestFrame == null) return@collectLatest

                                    val locationPrompt =
                                        promptGenerator.generateObjectLocationPrompt(target)
                                    objectDetectorDataSource.setPaused(true)

                                    try {
                                        generateSerializedResponse(
                                            locationPrompt,
                                            latestFrame.bitmap
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
                                    } finally {
                                        objectDetectorDataSource.setPaused(false)
                                    }
                                }
                            }
                    }
                    awaitClose { detectionsJob.cancel() }
                } else {
                    // Fallback to Gemma vision capabilities for arbitrary objects
                    val detectionPrompt = promptGenerator.generateObjectDetectionPrompt(target)
                    val locationPrompt = promptGenerator.generateObjectLocationPrompt(target)
                    
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
            .onStart { pauseNavigation() }
            .onCompletion { resumeNavigation() }
    }

    override fun askQuestion(question: String): Flow<NavigationCue> {
        val flow = callbackFlow<NavigationCue> {
            transientOperationCoordinator.executeTransientOperation("ask_question") {
                if (!isActive) return@executeTransientOperation

                val latestFrame = frameBufferManager.getLatestFrame()?.bitmap
                if (latestFrame == null) {
                    trySend(NavigationCue.InformationalAlert("Camera frame not available.", true))
                    close()
                    return@executeTransientOperation
                }

                val prompt = promptGenerator.generateQuestionAnsweringPrompt(question)
                generateSerializedResponse(prompt, latestFrame)
                    .collect { (chunk, done) ->
                        trySend(NavigationCue.InformationalAlert(chunk, done))
                        if (done) close()
                    }
            }
            awaitClose { }
        }

        return flow
            .onStart { pauseNavigation() }
            .onCompletion { resumeNavigation() }
    }

    override fun startCrossingMode(): Flow<NavigationCue> {
        val flow = callbackFlow<NavigationCue> {
            transientOperationCoordinator.executeTransientOperation("crossing_mode") {
                if (!isActive) return@executeTransientOperation

                Log.d(TAG, "Starting transient CROSSING operation")
                val crossingJob = repositoryScope.launch {
                    frameFlow.collectLatest {
                        val frames = frameBufferManager.getMotionAnalysisFrames()
                        if (frames.isEmpty()) return@collectLatest

                        try {
                            alertCoordinator.coordinateCrossingGuidance(
                                frames,
                                ::generateSerializedResponse
                            ) {
                                // Crossing complete callback
                                Log.i(TAG, "Crossing complete signal received")
                                this@callbackFlow.close()
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
            .onStart { pauseNavigation() }
            .onCompletion { resumeNavigation() }
    }


    /**
     * Resumes the previously paused navigation mode after a transient operation completes.
     *
     * This method determines what mode should be resumed and restarts it appropriately.
     */
    private fun resumeNavigation() {
        val pausedMode = navigationModeManager.getPausedMode()

        when (pausedMode) {
            NavigationModeManager.OperatingMode.NAVIGATION -> {
                startDirectorPipeline()
            }

            null -> {
                // If no mode was paused, default to starting the main navigation pipeline
                Log.d(TAG, "No paused mode found, starting default navigation pipeline")
                startDirectorPipeline()
            }
        }

        navigationModeManager.clearPausedMode()
    }

    /**
     * Generates AI responses with proper serialization to prevent concurrent access issues.
     *
     * This method wraps the AI data source call with mutex protection and converts
     * single frames to the expected list format.
     *
     * @param prompt The prompt for AI generation
     * @param bitmap Single frame for analysis
     * @return Flow of partial responses with completion status
     */
    private suspend fun generateSerializedResponse(
        prompt: String,
        bitmap: Bitmap
    ): Flow<Pair<String, Boolean>> {
        return aiMutex.withLock {
            gemmaDataSource.generateResponse(
                prompt,
                listOf(TimestampedFrame(bitmap, System.currentTimeMillis()))
            )
        }
    }

    /**
     * Generates AI responses with proper serialization for multiple frames.
     *
     * This method wraps the AI data source call with mutex protection for
     * motion analysis scenarios that require multiple frames.
     *
     * @param prompt The prompt for AI generation
     * @param frames Multiple frames for motion analysis
     * @return Flow of partial responses with completion status
     */
    private suspend fun generateSerializedResponse(
        prompt: String,
        frames: List<TimestampedFrame>
    ): Flow<Pair<String, Boolean>> {
        return aiMutex.withLock {
            gemmaDataSource.generateResponse(prompt, frames)
        }
    }
}