package com.lumina.app.ui.explorer

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.model.NavigationCue.InformationalAlert
import com.lumina.domain.model.NavigationCueType
import com.lumina.domain.model.VoiceCommand
import com.lumina.domain.service.CameraStateService
import com.lumina.domain.service.HapticFeedbackService
import com.lumina.domain.usecase.camera.ManageCameraOperationsUseCase
import com.lumina.domain.usecase.camera.ManageFrameThrottlingUseCase
import com.lumina.domain.usecase.camera.ProcessFrameUseCase
import com.lumina.domain.usecase.navigation.StartCrossingModeUseCase
import com.lumina.domain.usecase.navigation.StartNavigationUseCase
import com.lumina.domain.usecase.scene.AskQuestionUseCase
import com.lumina.domain.usecase.scene.DescribeSceneUseCase
import com.lumina.domain.usecase.scene.FindObjectUseCase
import com.lumina.domain.usecase.system.GetInitializationStateUseCase
import com.lumina.domain.usecase.system.NavigationOrchestrator
import com.lumina.domain.usecase.system.StopAllOperationsUseCase
import com.lumina.domain.usecase.system.StopGenerationUseCase
import com.lumina.domain.usecase.text.IdentifyCurrencyUseCase
import com.lumina.domain.usecase.text.ReadReceiptUseCase
import com.lumina.domain.usecase.text.ReadTextUseCase
import com.lumina.domain.usecase.voice.HandleTtsUseCase
import com.lumina.domain.usecase.voice.ProcessVoiceCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    private val navigationOrchestrator: NavigationOrchestrator,
    private val handleTts: HandleTtsUseCase,
    private val manageCameraOperations: ManageCameraOperationsUseCase,
    private val describeScene: DescribeSceneUseCase,
    private val startCrossingModeUseCase: StartCrossingModeUseCase,
    private val findObject: FindObjectUseCase,
    private val askQuestionUseCase: AskQuestionUseCase,
    private val stopAllOperations: StopAllOperationsUseCase,
    private val stopGeneration: StopGenerationUseCase,
    private val startNavigation: StartNavigationUseCase,
    private val identifyCurrency: IdentifyCurrencyUseCase,
    private val readReceipt: ReadReceiptUseCase,
    private val readText: ReadTextUseCase,
    private val cameraStateService: CameraStateService,
    private val hapticFeedbackService: HapticFeedbackService
) : ViewModel() {

    companion object {
        private const val TAG = "SceneExplorerViewModel"
    }

    // TTS state tracking - separate from service to handle initialization lifecycle
    private val _ttsState = MutableStateFlow(false)

    // Track whether we've shown the one-time messages to prevent repetition
    private var hasShownWelcomeMessage = false
    private var hasShownInitializingMessage = false

    // Cached frame for immediate reuse in on-demand operations (investigate, voice commands)
    // Avoids waiting for next camera callback when user requests immediate analysis
    private var lastFrameBytes: ByteArray? = null

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
        initializeTtsStateOnly()
    }

    /**
     * Initializes TTS state tracking for UI purposes.
     */
    private fun initializeTtsStateOnly() {
        // TTS is now ready; no UI action needed yet
        _ttsState.value = true
        Log.d(TAG, "TTS initialized for UI")
    }

    /**
     * UI state flow combining initialization status, navigation cues, and TTS state.
     */
    val uiState: StateFlow<SceneExplorerUiState> =
        combine(
            getInitializationState(),
            navigationOrchestrator.createNavigationFlow(
                automaticCueFlow = flowOf(), // Empty flow - navigation starts only on explicit command
                isTtsEnabled = _ttsState
            ),
            _ttsState
        ) { initState, (description, alertType), ttsInitialized ->
            if (ttsInitialized && !hasShownInitializingMessage) {
                hasShownInitializingMessage = true
                val message = "Initializing, please wait..."
                navigationOrchestrator.emitNavigationCue(
                    InformationalAlert(
                        message = message,
                        isDone = true
                    )
                )
            }
            if (initState is InitializationState.Initialized && !hasShownWelcomeMessage) {
                Log.d(TAG, "Initialization complete")
                hasShownWelcomeMessage = true
                val welcomeMessage = "Lumina is ready! " +
                        "Long press to talk, double tap to explore. " +
                        "Say 'help' for more commands."
                navigationOrchestrator.emitNavigationCue(
                    InformationalAlert(
                        message = welcomeMessage,
                        isDone = true
                    )
                )
            }

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
                val quality = if (cameraMode.value == CameraStateService.CameraMode.TEXT_READING) {
                    100 // High quality for reading tasks
                } else {
                    75 // Standard quality for navigation and other tasks
                }
                image.compress(Bitmap.CompressFormat.JPEG, quality, stream)
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
     * Triggers on-demand scene analysis with camera activation and frame buffer delay.
     * Activates camera, waits for frame buffer to populate, then processes scene description.
     */
    fun investigateScene() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stopGeneration()
                handleTts.stopSpeaking()
                // Activate camera and give time for frame buffer to populate
                manageCameraOperations.activateNavigationMode()
                kotlinx.coroutines.delay(500) // 0.5 second delay for frame buffer

                describeScene().collect { cue ->
                    navigationOrchestrator.emitNavigationCue(cue)
                    // Deactivate camera after scene description is complete
                    if (cue is NavigationCue.InformationalAlert) {
                        manageCameraOperations.deactivateCamera()
                    }
                }
            } catch (e: Exception) {
                manageCameraOperations.deactivateCamera()
                Log.e(TAG, "Error during scene investigation", e)
                navigationOrchestrator.emitNavigationCue(
                    NavigationCue.InformationalAlert(
                        message = "Error analyzing scene. Please try again.",
                        isDone = true
                    )
                )
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

            is VoiceCommand.ToggleHaptic -> {
                toggleHapticFeedback()
            }

            is VoiceCommand.TestHaptic -> {
                testHapticFeedback()
            }

            is VoiceCommand.Unknown -> {
                handleTts.speak("Command not recognized", _ttsState.value)
                hapticFeedbackService.triggerHaptic(HapticFeedbackService.HapticPattern.ERROR)
            }

            is VoiceCommand.Help -> {
                provideHelp()
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
        viewModelScope.launch(Dispatchers.IO) {
            stopGeneration()
            handleTts.stopSpeaking()
        }
        manageCameraOperations.optimisticallyActivateCamera()
        hapticFeedbackService.triggerHaptic(HapticFeedbackService.HapticPattern.LISTENING)
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
                    navigationOrchestrator.emitNavigationCue(cue)
                }
            } catch (e: Exception) {
                manageCameraOperations.deactivateCamera()
                navigationOrchestrator.emitNavigationCue(
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
                    navigationOrchestrator.emitNavigationCue(cue)
                    if (cue is NavigationCue.InformationalAlert && cue.isDone) {
                        manageCameraOperations.deactivateCamera()
                    }
                }
            } catch (e: Exception) {
                manageCameraOperations.deactivateCamera()
                navigationOrchestrator.emitNavigationCue(
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
                    navigationOrchestrator.emitNavigationCue(cue)
                    if (cue is NavigationCue.InformationalAlert && cue.isDone) {
                        manageCameraOperations.deactivateCamera()
                    }
                }
            } catch (e: Exception) {
                manageCameraOperations.deactivateCamera()
                navigationOrchestrator.emitNavigationCue(
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
                navigationOrchestrator.emitNavigationCue(cue)
                if (cue is NavigationCue.InformationalAlert) {
                    // Deactivate camera if it was activated for this question
                    manageCameraOperations.deactivateCamera()
                }
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

                identifyCurrency.identifyMultiFrame().collect { cue ->
                    navigationOrchestrator.emitNavigationCue(cue)
                    if (cue is NavigationCue.InformationalAlert) {
                        deactivateTextReadingMode()
                    }
                }

            } catch (e: Exception) {
                deactivateTextReadingMode()
                handleTts.speak("Error identifying currency. Please try again.", _ttsState.value)
                hapticFeedbackService.triggerHaptic(HapticFeedbackService.HapticPattern.ERROR)
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

                readReceipt.readMultiFrame().collect { cue ->
                    navigationOrchestrator.emitNavigationCue(cue)
                    if (cue is NavigationCue.InformationalAlert) {
                        deactivateTextReadingMode()
                    }
                }

            } catch (e: Exception) {
                deactivateTextReadingMode()
                handleTts.speak("Error reading receipt. Please try again.", _ttsState.value)
                hapticFeedbackService.triggerHaptic(HapticFeedbackService.HapticPattern.ERROR)
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

                readText.readMultiFrame().collect { cue ->
                    navigationOrchestrator.emitNavigationCue(cue)
                    if (cue is NavigationCue.InformationalAlert) {
                        deactivateTextReadingMode()
                    }
                }

            } catch (e: Exception) {
                deactivateTextReadingMode()
                handleTts.speak("Error reading text. Please try again.", _ttsState.value)
                hapticFeedbackService.triggerHaptic(HapticFeedbackService.HapticPattern.ERROR)
            }
        }
    }

    /**
     * Provides interactive help for available voice commands.
     */
    private fun provideHelp() {
        val helpMessage = """
            You can ask me to do many things. For example:
            'Start navigation' to begin guidance.
            'Read text' to read signs or documents.
            'Find my keys' to locate an object.
            'What's this currency?' to identify money.
            Or, just ask a question about your surroundings.
            What would you like to do?
        """.trimIndent()
        handleTts.speak(helpMessage, _ttsState.value)
    }

    // Utility methods

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
        viewModelScope.launch {
            stopAllOperations()
        }
        deactivateCameraAndClearBuffer()
    }

    fun speak(text: String) = handleTts.speak(text, _ttsState.value)
    fun isSpeaking(): Boolean = handleTts.isSpeaking()

    /**
     * Stops all operations including AI generation and cancels all active jobs.
     * This is called from the UI when user long-presses to interrupt everything.
     */
    fun stopAllOperationsAndGeneration() {
        viewModelScope.launch {
            try {
                stopGeneration()
                handleTts.stopSpeaking()
                stopAllActiveOperations()
                Log.d(TAG, "🛑 All operations and generation stopped by user")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping operations", e)
            }
        }
    }

    /**
     * Haptic feedback controls
     */
    fun toggleHapticFeedback() {
        val newState = !hapticFeedbackService.isHapticEnabled()
        hapticFeedbackService.setHapticEnabled(newState)
        val message = if (newState) "Haptic feedback enabled" else "Haptic feedback disabled"
        speak(message)
    }

    fun testHapticFeedback() {
        if (hapticFeedbackService.isHapticEnabled()) {
            hapticFeedbackService.triggerHaptic(HapticFeedbackService.HapticPattern.NOTIFICATION)
            speak("Haptic test")
        } else {
            speak("Haptic feedback is disabled")
        }
    }

    /**
     * Called when the ViewModel is no longer used and will be destroyed.
     * This is the place to clean up resources, cancel jobs, and release services.
     *
     * Actions performed:
     * - Stops all active operations (find, crossing, navigation).
     * - Deactivates the camera.
     */
    override fun onCleared() {
        super.onCleared()
        stopAllActiveOperations()
        manageCameraOperations.deactivateCamera()
    }
}
