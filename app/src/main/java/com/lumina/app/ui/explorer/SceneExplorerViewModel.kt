package com.lumina.app.ui.explorer

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.model.NavigationCueType
import com.lumina.domain.model.VoiceCommand
import com.lumina.domain.service.CameraStateService
import com.lumina.domain.service.TextToSpeechService
import com.lumina.domain.usecase.camera.ManageCameraOperationsUseCase
import com.lumina.domain.usecase.camera.ManageFrameThrottlingUseCase
import com.lumina.domain.usecase.camera.ProcessFrameUseCase
import com.lumina.domain.usecase.navigation.StartCrossingModeUseCase
import com.lumina.domain.usecase.navigation.StartNavigationUseCase
import com.lumina.domain.usecase.scene.AskQuestionUseCase
import com.lumina.domain.usecase.scene.DescribeSceneUseCase
import com.lumina.domain.usecase.scene.FindObjectUseCase
import com.lumina.domain.usecase.system.GetInitializationStateUseCase
import com.lumina.domain.usecase.system.ProcessNavigationCueFlowUseCase
import com.lumina.domain.usecase.system.StopAllOperationsUseCase
import com.lumina.domain.usecase.text.IdentifyCurrencyUseCase
import com.lumina.domain.usecase.text.ReadReceiptUseCase
import com.lumina.domain.usecase.text.ReadTextUseCase
import com.lumina.domain.usecase.voice.HandleTtsUseCase
import com.lumina.domain.usecase.voice.ProcessVoiceCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Encapsulates all UI state data for the Scene Explorer screen.
 * Combines initialization status, dynamic content, and feature availability.
 */
data class SceneExplorerUiState(
    val description: String = "",
    val initializationState: InitializationState = InitializationState.NotInitialized,
    val alertType: NavigationCueType = NavigationCueType.NONE,
    val isTtsInitialized: Boolean = false
)

/**
 * Presentation layer coordinator for Scene Explorer functionality.
 *
 * Responsibilities:
 * - Manages UI state transitions and reactive flows
 * - Coordinates between domain use cases and UI layer
 * - Handles coroutine lifecycle and cancellation
 * - Maintains frame buffer for on-demand operations
 * - Bridges voice commands to appropriate domain actions
 *
 * Architecture Note: Business logic extracted to domain use cases,
 * leaving this ViewModel focused on presentation concerns only.
 */
@HiltViewModel
class SceneExplorerViewModel @Inject constructor(
    getInitializationState: GetInitializationStateUseCase,
    private val processFrame: ProcessFrameUseCase,
    private val manageFrameThrottling: ManageFrameThrottlingUseCase,
    private val processVoiceCommand: ProcessVoiceCommandUseCase,
    private val processNavigationCueFlow: ProcessNavigationCueFlowUseCase,
    private val handleTts: HandleTtsUseCase,
    private val manageCameraOperations: ManageCameraOperationsUseCase,
    private val describeScene: DescribeSceneUseCase,
    private val startCrossingModeUseCase: StartCrossingModeUseCase,
    private val findObject: FindObjectUseCase,
    private val askQuestionUseCase: AskQuestionUseCase,
    private val stopAllOperations: StopAllOperationsUseCase,
    private val startNavigation: StartNavigationUseCase,
    private val identifyCurrency: IdentifyCurrencyUseCase,
    private val readReceipt: ReadReceiptUseCase,
    private val readText: ReadTextUseCase,
    private val cameraStateService: CameraStateService,
    private val textToSpeechService: TextToSpeechService
) : ViewModel() {

    companion object {
        private const val TAG = "SceneExplorerViewModel"
    }

    // TTS state tracking - separate from service to handle initialization lifecycle
    private var isTtsInitialized = false
    private val _ttsState = MutableStateFlow(false)

    // Cached frame for immediate reuse in on-demand operations (investigate, voice commands)
    // Avoids waiting for next camera callback when user requests immediate analysis
    private var lastFrameBytes: ByteArray? = null

    // Bridges user-initiated actions (voice, gestures) into the main navigation pipeline
    // Allows manual cues to be processed alongside automatic camera-driven cues
    private val manualCueFlow = MutableSharedFlow<NavigationCue>()

    // Coroutine job lifecycle management for concurrent operations
    // Each operation type gets its own job to allow selective cancellation
    private var findJob: Job? = null
    private var crossingJob: Job? = null
    private var questionJob: Job? = null
    private var navigationJob: Job? = null

    // Camera state observation - exposed as StateFlow for UI binding
    val cameraMode = cameraStateService.currentMode
    val isCameraActive = cameraStateService.isActive

    init {
        initializeTextToSpeech()
    }

    /**
     * Main navigation cue flow with reactive TTS processing.
     */
    private val navigationCueFlow = merge(
        cameraStateService.currentMode.flatMapLatest { mode ->
            if (mode == CameraStateService.CameraMode.NAVIGATION && navigationJob?.isActive == true) {
                emptyFlow()
            } else {
                emptyFlow()
            }
        },
        manualCueFlow
    ).onEach { navigationCue ->
        // Handle TTS reactively based on current state
        Log.d(
            TAG,
            "🔊 Processing NavigationCue: ${navigationCue.javaClass.simpleName}, TTS enabled: ${_ttsState.value}"
        )

        if (_ttsState.value) {
            val message = when (navigationCue) {
                is NavigationCue.CriticalAlert -> navigationCue.message
                is NavigationCue.InformationalAlert -> navigationCue.message
                is NavigationCue.AmbientUpdate -> navigationCue.message
            }

            // Skip empty strings generated while the model is thinking
            if (message.isNotBlank()) {
                Log.d(TAG, "🎤 Speaking message: '$message'")
                textToSpeechService.speak(navigationCue)
            } else {
                Log.d(TAG, "🤐 Skipping empty message")
            }
        } else {
            Log.d(TAG, "🔇 TTS disabled, not speaking")
        }
    }

    /**
     * UI state flow using domain use case for processing.
     */
    val uiState: StateFlow<SceneExplorerUiState> =
        combine(
            getInitializationState(),
            processNavigationCueFlow.processFlow(navigationCueFlow),
            _ttsState
        ) { initState, (description, alertType), ttsInitialized ->
            SceneExplorerUiState(
                initializationState = initState,
                description = description,
                alertType = alertType,
                isTtsInitialized = ttsInitialized
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SceneExplorerUiState()
        )

    /**
     * Processes camera frames with throttling handled by domain use case.
     */
    fun onFrameReceived(image: Bitmap) {
        // Check throttling using domain use case
        if (!manageFrameThrottling.shouldProcessFrame()) {
            image.recycle() // Important: recycle dropped frames to prevent memory leaks
            return
        }

        manageFrameThrottling.startFrameProcessing()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = ByteArrayOutputStream()
                image.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                val bytes = stream.toByteArray()
                lastFrameBytes = bytes
                processFrame(ImageInput(bytes))
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            } finally {
                image.recycle()
                manageFrameThrottling.endFrameProcessing()
            }
        }
    }

    /**
     * Triggers on-demand scene analysis using the most recently captured frame.
     *
     * Provides detailed environmental description for user orientation and context.
     * Uses cached frame data to avoid camera activation delays during user interaction.
     * Results are delivered through the standard NavigationCue pipeline for consistent
     * UI presentation and TTS integration.
     */
    fun investigateScene() {
        val bytes = lastFrameBytes ?: return
        viewModelScope.launch(Dispatchers.IO) {
            describeScene(ImageInput(bytes)).collect { cue ->
                manualCueFlow.emit(cue)
            }
        }
    }

    /**
     * Processes raw voice input and executes corresponding system operations.
     *
     * Delegates command parsing to domain layer for consistent interpretation,
     * then coordinates appropriate use case execution. Provides immediate audio
     * feedback for command acknowledgment and error states. Manages camera
     * resources optimistically for commands requiring visual input.
     *
     * @param command Raw voice input string from speech recognition
     */
    fun handleVoiceCommand(command: String) {
        val voiceCommand = processVoiceCommand.processCommand(command)
        Log.d("VoiceCommand", "🎤 Processing voice command: $voiceCommand")

        when (voiceCommand) {
            is VoiceCommand.FindObject -> {
                handleTts.speak("Searching for ${voiceCommand.target}", _ttsState.value)
                startFindMode(voiceCommand.target)
            }

            is VoiceCommand.Stop -> {
                stopAllActiveOperations()
                handleTts.speak("Operation cancelled", _ttsState.value)
            }

            is VoiceCommand.StartNavigation -> {
                startNavigation()
            }

            is VoiceCommand.StopNavigation -> {
                stopNavigationAndCamera()
            }

            is VoiceCommand.CrossStreet -> {
                handleTts.speak(
                    "Starting crossing mode. Please wait for instructions.",
                    _ttsState.value
                )
                startCrossingMode()
            }

            is VoiceCommand.IdentifyCurrency -> {
                handleTts.speak("Identifying currency", _ttsState.value)
                identifyCurrencyFromFrame()
            }

            is VoiceCommand.ReadReceipt -> {
                handleTts.speak("Reading receipt", _ttsState.value)
                readReceiptFromFrame()
            }

            is VoiceCommand.ReadText -> {
                handleTts.speak("Reading text", _ttsState.value)
                readTextFromFrame()
            }

            is VoiceCommand.AskQuestion -> {
                if (voiceCommand.question.isNotEmpty()) {
                    askQuestion(voiceCommand.question)
                } else {
                    deactivateCameraAndClearBuffer()
                    handleTts.speak("What is your question?", _ttsState.value)
                }
            }

            is VoiceCommand.Unknown -> {
                handleTts.speak("Command not recognized", _ttsState.value)
            }
        }
    }

    /**
     * Preemptively activates camera hardware for anticipated voice commands.
     *
     * Reduces response latency for visual operations by starting camera initialization
     * during speech recognition. Uses TEXT_READING mode for optimal resolution across
     * multiple operation types. Camera is automatically deactivated if the command
     * doesn't require visual input.
     */
    fun optimisticallyActivateCamera() {
        manageCameraOperations.optimisticallyActivateCamera()
        Log.d(TAG, "🎤📸 Optimistically activated camera for voice command")
    }

    /**
     * Initiates continuous environmental navigation assistance.
     *
     * Activates real-time scene analysis for mobility guidance, obstacle detection,
     * and path optimization. Manages camera in NAVIGATION mode for optimal field
     * of view and processing frequency. Automatically handles resource cleanup
     * and error recovery with user feedback.
     */
    fun startNavigation() {
        navigationJob?.cancel()
        navigationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                manageCameraOperations.activateNavigationMode()
                startNavigation.invoke().collect { cue ->
                    manualCueFlow.emit(cue)
                }
            } catch (e: Exception) {
                manageCameraOperations.deactivateCamera()
                manualCueFlow.emit(
                    NavigationCue.InformationalAlert(
                        message = "Error during navigation. Please try again.",
                        isDone = true
                    )
                )
            }
        }
        handleTts.speak("Navigation started", _ttsState.value)
    }

    /**
     * Terminates active navigation session and releases camera resources.
     *
     * Safely cancels ongoing navigation operations, stops all background processing,
     * and deactivates camera hardware for power conservation. Provides audio
     * confirmation of operation termination.
     */
    fun stopNavigationAndCamera() {
        navigationJob?.cancel()
        navigationJob = null
        stopAllOperations()
        manageCameraOperations.deactivateCamera()
        handleTts.speak("Navigation stopped", _ttsState.value)
    }

    /**
     * Initiates targeted object detection and location guidance.
     *
     * Activates AI-powered visual search for specified objects with real-time
     * directional feedback. Uses navigation camera mode for optimal detection
     * accuracy. Session automatically terminates upon successful object location
     * or user cancellation.
     *
     * @param target Object description or name to locate
     */
    fun startFindMode(target: String) {
        findJob?.cancel()
        findJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                manageCameraOperations.activateNavigationMode()
                findObject(target).collect { cue ->
                    manualCueFlow.emit(cue)
                    if (cue is NavigationCue.InformationalAlert && cue.isDone) {
                        manageCameraOperations.deactivateCamera()
                    }
                }
            } catch (e: Exception) {
                manageCameraOperations.deactivateCamera()
                manualCueFlow.emit(
                    NavigationCue.InformationalAlert(
                        message = "Error finding object. Please try again.",
                        isDone = true
                    )
                )
            }
        }
    }

    /**
     * Initiates specialized street crossing assistance mode.
     *
     * Provides real-time guidance for safe street traversal including traffic
     * light detection, pedestrian signal monitoring, and vehicle approach warnings.
     * Automatically manages session lifecycle with completion detection and
     * resource cleanup.
     */
    private fun startCrossingMode() {
        crossingJob?.cancel()
        crossingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                manageCameraOperations.activateNavigationMode()
                startCrossingModeUseCase().collect { cue ->
                    manualCueFlow.emit(cue)
                    if (cue is NavigationCue.InformationalAlert && cue.isDone) {
                        manageCameraOperations.deactivateCamera()
                    }
                }
            } catch (e: Exception) {
                manageCameraOperations.deactivateCamera()
                manualCueFlow.emit(
                    NavigationCue.InformationalAlert(
                        message = "Error during crossing session. Please try again.",
                        isDone = true
                    )
                )
            }
        }
    }

    /**
     * Processes natural language questions with optional visual context.
     *
     * Handles both text-based queries and visual questions that require current
     * scene analysis. Results are delivered through the NavigationCue pipeline
     * for consistent presentation and TTS integration.
     *
     * @param question User's natural language query
     */
    private fun askQuestion(question: String) {
        questionJob?.cancel()
        questionJob = viewModelScope.launch {
            askQuestionUseCase(question).collect { cue ->
                manualCueFlow.emit(cue)
            }
        }
    }

    /**
     * Performs currency identification using current camera frame.
     *
     * Activates high-resolution camera mode for optimal text and symbol recognition.
     * Provides detailed analysis of bills, coins, and denomination values.
     * Automatically manages camera lifecycle and provides user feedback for
     * missing frames or processing errors.
     */
    private fun identifyCurrencyFromFrame() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                manageCameraOperations.activateTextReadingMode()
                val frameBytes = manageCameraOperations.waitForFrame { lastFrameBytes }

                if (frameBytes != null) {
                    val imageInput = ImageInput(frameBytes)
                    identifyCurrency(imageInput).collect { cue ->
                        manualCueFlow.emit(cue)
                        if (cue is NavigationCue.InformationalAlert && cue.isDone) {
                            deactivateTextReadingMode()
                        }
                    }
                } else {
                    deactivateTextReadingMode()
                    handleTts.speak(
                        "No image available. Please ensure camera is working and try again.",
                        _ttsState.value
                    )
                }
            } catch (e: Exception) {
                deactivateTextReadingMode()
                handleTts.speak("Error identifying currency. Please try again.", _ttsState.value)
            }
        }
    }

    /**
     * Performs receipt and document text extraction using current camera frame.
     *
     * Utilizes specialized OCR processing optimized for structured document layouts.
     * Extracts itemized information, totals, and merchant details with high accuracy.
     * Manages camera activation and provides user feedback for processing status.
     */
    private fun readReceiptFromFrame() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                manageCameraOperations.activateTextReadingMode()
                val frameBytes = manageCameraOperations.waitForFrame { lastFrameBytes }

                if (frameBytes != null) {
                    val imageInput = ImageInput(frameBytes)
                    readReceipt(imageInput).collect { cue ->
                        manualCueFlow.emit(cue)
                        if (cue is NavigationCue.InformationalAlert && cue.isDone) {
                            deactivateTextReadingMode()
                        }
                    }
                } else {
                    deactivateTextReadingMode()
                    handleTts.speak(
                        "No image available. Please ensure camera is working and try again.",
                        _ttsState.value
                    )
                }
            } catch (e: Exception) {
                deactivateTextReadingMode()
                handleTts.speak("Error reading receipt. Please try again.", _ttsState.value)
            }
        }
    }

    /**
     * Performs general text recognition using current camera frame.
     *
     * Provides OCR capabilities for signs, labels, books, and other text content.
     * Optimized for various text sizes, fonts, and lighting conditions.
     * Includes automatic camera management and error handling with user feedback.
     */
    private fun readTextFromFrame() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                manageCameraOperations.activateTextReadingMode()
                val frameBytes = manageCameraOperations.waitForFrame { lastFrameBytes }

                if (frameBytes != null) {
                    val imageInput = ImageInput(frameBytes)
                    readText(imageInput).collect { cue ->
                        manualCueFlow.emit(cue)
                        if (cue is NavigationCue.InformationalAlert && cue.isDone) {
                            deactivateTextReadingMode()
                        }
                    }
                } else {
                    deactivateTextReadingMode()
                    handleTts.speak(
                        "No image available. Please ensure camera is working and try again.",
                        _ttsState.value
                    )
                }
            } catch (e: Exception) {
                deactivateTextReadingMode()
                handleTts.speak("Error reading text. Please try again.", _ttsState.value)
            }
        }
    }

    // Utility methods

    /**
     * Initializes Text-to-Speech service with callback handling for success and error states.
     *
     * Manages TTS state transitions and provides logging for debugging initialization issues.
     * Updates internal state flow to enable reactive TTS processing throughout the application.
     */
    private fun initializeTextToSpeech() {
        textToSpeechService.initialize(
            onInitialized = {
                _ttsState.value = true
                Log.d(TAG, "TTS initialized successfully")
            },
            onError = { error ->
                Log.e(TAG, "TTS initialization failed: $error")
                _ttsState.value = false
            }
        )
    }

    /**
     * Deactivates camera and clears processing buffers after text reading operations.
     *
     * Ensures proper resource cleanup and power conservation when text analysis
     * operations are complete. Stops all background processing to free memory.
     */
    private fun deactivateTextReadingMode() {
        manageCameraOperations.deactivateCamera()
        stopAllOperations()
    }

    /**
     * Deactivates the camera if it was optimistically activated and clears the frame buffer.
     *
     * This function is typically called when a voice command that doesn't require visual input
     * is processed, or when an operation that might have optimistically activated the camera
     * completes or is cancelled. It ensures that camera resources are released and the last
     * captured frame is cleared to prevent outdated data from being used.
     */
    private fun deactivateCameraAndClearBuffer() {
        manageCameraOperations.deactivateCamera()
        stopAllOperations()
        Log.d(TAG, "🎤🔴 Deactivated optimistic camera and cleared buffer")
    }

    /**
     * Cancels all active operations and performs comprehensive resource cleanup.
     *
     * Safely terminates concurrent jobs, releases camera hardware, and clears
     * processing buffers. Used for emergency stops and application shutdown.
     */
    private fun stopAllActiveOperations() {
        findJob?.cancel()
        findJob = null
        crossingJob?.cancel()
        crossingJob = null
        navigationJob?.cancel()
        navigationJob = null
        stopAllOperations()
        deactivateCameraAndClearBuffer()
    }


    fun speak(text: String) = handleTts.speak(text, _ttsState.value)
    fun isSpeaking(): Boolean = handleTts.isSpeaking()

    /**
     * Called when the ViewModel is no longer used and will be destroyed.
     * This is the place to clean up resources, cancel jobs, and release services.
     *
     * Actions performed:
     * - Stops all active operations (find, crossing, navigation).
     * - Shuts down the Text-to-Speech service.
     * - Deactivates the camera.
     */
    override fun onCleared() {
        super.onCleared()
        stopAllActiveOperations()
        textToSpeechService.shutdown()
        manageCameraOperations.deactivateCamera()
    }
}
