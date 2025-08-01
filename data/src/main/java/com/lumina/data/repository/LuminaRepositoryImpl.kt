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
import com.lumina.domain.service.NavigationModeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
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
     * Flag to indicate if an AI operation is currently in progress.
     * This helps coordinate between navigation pipeline and transient operations.
     */
    @Volatile
    private var isAiOperationInProgress = false

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
        // Note: Navigation is now purely transient - only runs when explicitly started
        // No automatic startup - must call startNavigationPipeline() first
        return alertCoordinator.getNavigationCueFlow()
    }

    /**
     * Starts the navigation director pipeline explicitly for transient navigation sessions.
     * Navigation will run until explicitly stopped with stopNavigation().
     * No automatic resumption after other operations.
     */
    override fun startNavigationPipeline() {
        startDirectorPipeline()
    }

    override suspend fun processNewFrame(image: ImageInput) {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        val timestamp = System.currentTimeMillis()
        frameFlow.tryEmit(Pair(bitmap, timestamp))
    }

    override fun stopNavigation() {
        Log.d(TAG, "🛑 Explicitly stopping navigation pipeline")
        navigationModeManager.stopAllModes()
        objectDetectorDataSource.setPaused(true)
        gemmaDataSource.resetSession()
        frameBufferManager.clear()
        threatAssessmentManager.reset()
    }

    /**
     * NOTE: Pause/resume navigation functionality has been simplified.
     * Navigation is now purely transient - it starts when explicitly requested
     * and stops when explicitly requested. No automatic coordination between operations.
     */
    private suspend fun pauseNavigation() {
        // Simplified: Just wait for ongoing AI operations to complete
        // No navigation mode management needed since operations are independent
        waitForAiOperationToComplete()
    }

    /**
     * Waits for any ongoing AI operation to complete before proceeding.
     * This prevents concurrent AI requests that cause IllegalStateException.
     */
    private suspend fun waitForAiOperationToComplete() {
        if (isAiOperationInProgress) {
            Log.d(TAG, "Waiting for ongoing AI operation to complete...")
        }

        // Wait up to 10 seconds for ongoing AI operations to complete
        var attempts = 0
        while (isAiOperationInProgress && attempts < 100) {
            kotlinx.coroutines.delay(100) // Wait 100ms
            attempts++
        }

        if (isAiOperationInProgress) {
            Log.w(TAG, "AI operation still in progress after waiting 10 seconds, proceeding anyway")
            // Force reset the flag to prevent permanent blocking
            isAiOperationInProgress = false
        } else if (attempts > 0) {
            Log.d(TAG, "AI operation completed after waiting ${attempts * 100}ms")
        }
    }

    /**
     * Starts the main navigation pipeline (Director Pattern) that provides continuous
     * environmental awareness through object detection and intelligent alert generation.
     *
     * This method integrates all the specialized components to create a coordinated
     * navigation experience that adapts to environmental changes and threat levels.
     */
    private fun startDirectorPipeline() {
        if (navigationModeManager.isActive(NavigationModeService.OperatingMode.NAVIGATION)) {
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
            NavigationModeService.OperatingMode.NAVIGATION,
            navigationJob
        )
    }

    override fun describeScene(
        image: ImageInput,
        prompt: String
    ): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            // Pause navigation first to prevent AI conflicts
            pauseNavigation()

            try {
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
            } finally {
            }
            awaitClose { }
        }
    }


    override fun findObject(target: String): Flow<NavigationCue> {
        val normalizedTarget = target.trim().lowercase()

        return callbackFlow<NavigationCue> {
            // Pause navigation first to prevent AI conflicts
            pauseNavigation()

            try {
                transientOperationCoordinator.executeTransientOperation("find_object_$target") {
                    if (!isActive) return@executeTransientOperation

                    if (normalizedTarget in COCO_LABELS) {
                        // Use fast object detector for known COCO categories
                        objectDetectorDataSource.setPaused(false)

                        val detectionsJob = repositoryScope.launch {
                            objectDetectorDataSource.getDetectionStream(frameFlow)
                                .collect { detections ->
                                    // Skip if AI is already processing to avoid conflicts
                                    if (isAiOperationInProgress) {
                                        Log.d(TAG, "Skipping detection - AI operation in progress")
                                        return@collect
                                    }

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

                                        val bestFrame = frameBufferManager.getBestQualityFrame()
                                        if (bestFrame == null) return@collect

                                        val locationPrompt =
                                            promptGenerator.generateObjectLocationPrompt(target)
                                        objectDetectorDataSource.setPaused(true)

                                        try {
                                            generateSerializedResponse(
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
                            // Process frames sequentially, not with collectLatest to avoid cancellation
                            frameFlow.collect { (_, _) ->
                                // Skip if AI is already processing to avoid conflicts
                                if (isAiOperationInProgress) {
                                    Log.d(TAG, "Skipping frame - AI operation in progress")
                                    return@collect
                                }

                                // Get the best quality frame for AI analysis instead of potentially blurred frame
                                val bestFrame = frameBufferManager.getBestQualityFrame()
                                if (bestFrame == null) {
                                    Log.d(TAG, "No quality frame available for analysis")
                                    return@collect
                                }

                                try {
                                    // Wait for the complete detection response before proceeding
                                    var fullResponse = ""
                                    var objectDetected = false

                                    // First: Complete the detection phase
                                    generateSerializedResponse(detectionPrompt, bestFrame.bitmap)
                                        .collect { (chunk, isDone) ->
                                            fullResponse += chunk
                                            if (isDone) {
                                                objectDetected = fullResponse
                                                    .trim()
                                                    .lowercase()
                                                    .startsWith("y")

                                                Log.d(
                                                    TAG,
                                                    "Detection phase complete. Object detected: $objectDetected"
                                                )
                                            }
                                        }

                                    // Second: If detected, proceed with location description
                                    if (objectDetected) {
                                        trySend(
                                            NavigationCue.InformationalAlert(
                                                "$target detected",
                                                false
                                            )
                                        )
                                        Log.d(
                                            TAG,
                                            "Object detected! Starting location description..."
                                        )

                                        generateSerializedResponse(locationPrompt, bestFrame.bitmap)
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
                                                    Log.d(
                                                        TAG,
                                                        "Location description complete, closing flow"
                                                    )
                                                    this@callbackFlow.close()
                                                }
                                            }
                                    } else {
                                        Log.d(TAG, "Object not detected in frame, continuing...")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error during object detection", e)
                                    // Continue processing instead of failing completely
                                }
                            }
                        }
                        awaitClose { collectorJob.cancel() }
                    }
                }
            } finally {
                // Note: No automatic navigation resumption - camera lifecycle is managed externally
                // Object finding is transient and camera should be deactivated when complete
            }
        }
    }

    override fun askQuestion(question: String): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            try {
                transientOperationCoordinator.executeTransientOperation("ask_question") {
                    if (!isActive) return@executeTransientOperation

                    val bestFrame = frameBufferManager.getBestQualityFrame()
                    if (bestFrame == null) {
                        trySend(
                            NavigationCue.InformationalAlert(
                                "Camera frame not available.",
                                true
                            )
                        )
                        close()
                        return@executeTransientOperation
                    }

                    val prompt = promptGenerator.generateQuestionAnsweringPrompt(question)
                    generateSerializedResponse(prompt, bestFrame.bitmap)
                        .collect { (chunk, done) ->
                            trySend(NavigationCue.InformationalAlert(chunk, done))
                            if (done) close()
                        }
                }
            } catch (e: Exception) {
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to answer question. Please try again.",
                        true
                    )
                )
                close()
            }
            // Note: No navigation pause/resume - operations are independent
            awaitClose { }
        }
    }

    override fun startCrossingMode(): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            try {
                transientOperationCoordinator.executeTransientOperation("crossing_mode") {
                    if (!isActive) return@executeTransientOperation

                    Log.d(TAG, "Starting transient CROSSING operation")
                    val crossingJob = repositoryScope.launch {
                        frameFlow.collect {
                            // Skip if AI is already processing to avoid conflicts
                            if (isAiOperationInProgress) {
                                Log.d(TAG, "Skipping frame - AI operation in progress for crossing")
                                return@collect
                            }

                            val frames = frameBufferManager.getMotionAnalysisFrames()
                            if (frames.isEmpty()) return@collect

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
            } catch (e: Exception) {
                Log.e(TAG, "Crossing mode failed", e)
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to start crossing mode. Please try again.",
                        true
                    )
                )
                close()
            }
            // Note: No navigation pause/resume - operations are independent
        }
    }


    /**
     * NOTE: Resume navigation functionality has been removed.
     * Navigation is now purely transient - it starts when explicitly requested
     * and stops when explicitly requested. No automatic resumption needed.
     */

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
    ): Flow<Pair<String, Boolean>> = kotlinx.coroutines.flow.flow {
        aiMutex.withLock {
            Log.d(TAG, "Starting AI operation with prompt: ${prompt.take(50)}...")
            isAiOperationInProgress = true
            try {
                gemmaDataSource.generateResponse(
                    prompt,
                    listOf(TimestampedFrame(bitmap, System.currentTimeMillis()))
                ).collect { (chunk, isDone) ->
                    emit(Pair(chunk, isDone))
                    if (isDone) {
                        Log.d(TAG, "AI operation completed")
                        isAiOperationInProgress = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI operation failed", e)
                isAiOperationInProgress = false
                throw e
            }
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
    ): Flow<Pair<String, Boolean>> = kotlinx.coroutines.flow.flow {
        aiMutex.withLock {
            Log.d(TAG, "Starting AI operation with prompt: ${prompt.take(50)}...")
            isAiOperationInProgress = true
            try {
                gemmaDataSource.generateResponse(prompt, frames).collect { (chunk, isDone) ->
                    emit(Pair(chunk, isDone))
                    if (isDone) {
                        Log.d(TAG, "AI operation completed")
                        isAiOperationInProgress = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI operation failed", e)
                isAiOperationInProgress = false
                throw e
            }
        }
    }

    override fun identifyCurrency(image: ImageInput): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            // Pause navigation first to prevent AI conflicts
            pauseNavigation()

            try {
                transientOperationCoordinator.executeTransientOperation("identify_currency") {
                    if (!isActive) return@executeTransientOperation

                    val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                    val prompt = promptGenerator.generateCurrencyIdentificationPrompt()

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
            } catch (e: Exception) {
                Log.e(TAG, "Currency identification failed", e)
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to identify currency. Please try again.",
                        true
                    )
                )
                close()
            }
            // Note: No automatic navigation resumption - camera lifecycle is managed externally

            awaitClose {
                // Note: No automatic navigation resumption - camera lifecycle is managed externally
            }
        }
    }

    override fun readReceipt(image: ImageInput): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            // Pause navigation first to prevent AI conflicts
            pauseNavigation()

            try {
                transientOperationCoordinator.executeTransientOperation("read_receipt") {
                    if (!isActive) return@executeTransientOperation

                    val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                    val prompt = promptGenerator.generateReceiptReadingPrompt()

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
            } catch (e: Exception) {
                Log.e(TAG, "Receipt reading failed", e)
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to read receipt. Please try again.",
                        true
                    )
                )
                close()
            }
            // Note: No automatic navigation resumption - camera lifecycle is managed externally

            awaitClose {
                // Note: No automatic navigation resumption - camera lifecycle is managed externally
            }
        }
    }

    override fun readText(image: ImageInput): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            // Pause navigation first to prevent AI conflicts
            pauseNavigation()

            try {
                transientOperationCoordinator.executeTransientOperation("read_text") {
                    if (!isActive) return@executeTransientOperation

                    val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                    val prompt = promptGenerator.generateTextReadingPrompt()

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
            } catch (e: Exception) {
                Log.e(TAG, "Text reading failed", e)
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to read text. Please try again.",
                        true
                    )
                )
                close()
            }
            // Note: No automatic navigation resumption - camera lifecycle is managed externally

            awaitClose {
                // Note: No automatic navigation resumption - camera lifecycle is managed externally
            }
        }
    }

    override fun identifyCurrencyMultiFrame(images: List<ImageInput>): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            try {
                transientOperationCoordinator.executeTransientOperation("identify_currency_multi") {
                    if (!isActive) return@executeTransientOperation

                    // Process input images through frame buffer for quality selection
                    images.forEach { image ->
                        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                        frameBufferManager.addFrame(bitmap, System.currentTimeMillis())
                    }

                    // Wait a moment for frame buffer to process
                    kotlinx.coroutines.delay(200)

                    // Get multiple high-quality, non-blurred frames from frame buffer
                    val qualityFrames =
                        frameBufferManager.getMotionAnalysisFrames() // Get up to 3 best frames
                    println("Quality frames selected: ${qualityFrames.size}")
                    if (qualityFrames.isEmpty()) {
                        trySend(
                            NavigationCue.InformationalAlert(
                                "No quality frames available. Please try again.",
                                true
                            )
                        )
                        close()
                        return@executeTransientOperation
                    }

                    try {
                        val prompt = promptGenerator.generateMultiFrameCurrencyPrompt()

                        // Use frame buffer's high-quality frames
                        generateSerializedResponse(prompt, qualityFrames)
                            .collect { (chunk, done) ->
                                trySend(NavigationCue.InformationalAlert(chunk, done))
                                if (done) close()
                            }
                    } finally {
                        // Frames are managed by frame buffer, no need to recycle manually
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multi-frame currency identification failed", e)
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to identify currency. Please try again.",
                        true
                    )
                )
                close()
            }

            awaitClose { }
        }
    }

    override fun readReceiptMultiFrame(images: List<ImageInput>): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            try {
                transientOperationCoordinator.executeTransientOperation("read_receipt_multi") {
                    if (!isActive) return@executeTransientOperation

                    // Process input images through frame buffer for quality selection
                    images.forEach { image ->
                        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                        frameBufferManager.addFrame(bitmap, System.currentTimeMillis())
                    }

                    // Wait a moment for frame buffer to process
                    kotlinx.coroutines.delay(200)

                    // Get multiple high-quality, non-blurred frames from frame buffer
                    val qualityFrames =
                        frameBufferManager.getMotionAnalysisFrames() // Get up to 3 best frames

                    if (qualityFrames.isEmpty()) {
                        trySend(
                            NavigationCue.InformationalAlert(
                                "No quality frames available. Please try again.",
                                true
                            )
                        )
                        close()
                        return@executeTransientOperation
                    }

                    try {
                        val prompt = promptGenerator.generateMultiFrameReceiptPrompt()

                        // Use frame buffer's high-quality frames
                        generateSerializedResponse(prompt, qualityFrames)
                            .collect { (chunk, done) ->
                                trySend(NavigationCue.InformationalAlert(chunk, done))
                                if (done) close()
                            }
                    } finally {
                        // Frames are managed by frame buffer, no need to recycle manually
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multi-frame receipt reading failed", e)
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to read receipt. Please try again.",
                        true
                    )
                )
                close()
            }

            awaitClose { }
        }
    }

    override fun readTextMultiFrame(images: List<ImageInput>): Flow<NavigationCue> {
        return callbackFlow<NavigationCue> {
            try {
                transientOperationCoordinator.executeTransientOperation("read_text_multi") {
                    if (!isActive) return@executeTransientOperation

                    // Process input images through frame buffer for quality selection
                    images.forEach { image ->
                        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                        frameBufferManager.addFrame(bitmap, System.currentTimeMillis())
                    }

                    // Wait a moment for frame buffer to process
                    kotlinx.coroutines.delay(200)

                    // Get multiple high-quality, non-blurred frames from frame buffer
                    val qualityFrames =
                        frameBufferManager.getMotionAnalysisFrames() // Get up to 3 best frames

                    if (qualityFrames.isEmpty()) {
                        trySend(
                            NavigationCue.InformationalAlert(
                                "No quality frames available. Please try again.",
                                true
                            )
                        )
                        close()
                        return@executeTransientOperation
                    }

                    try {
                        val prompt = promptGenerator.generateMultiFrameTextPrompt()

                        // Use frame buffer's high-quality frames
                        generateSerializedResponse(prompt, qualityFrames)
                            .collect { (chunk, done) ->
                                trySend(NavigationCue.InformationalAlert(chunk, done))
                                if (done) close()
                            }
                    } finally {
                        // Frames are managed by frame buffer, no need to recycle manually
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multi-frame text reading failed", e)
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to read text. Please try again.",
                        true
                    )
                )
                close()
            }

            awaitClose { }
        }
    }
}