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
import com.lumina.domain.usecase.AskQuestionUseCase
import com.lumina.domain.usecase.DescribeSceneUseCase
import com.lumina.domain.usecase.FindObjectUseCase
import com.lumina.domain.usecase.GetInitializationStateUseCase
import com.lumina.domain.usecase.HandleTtsUseCase
import com.lumina.domain.usecase.IdentifyCurrencyUseCase
import com.lumina.domain.usecase.ManageCameraOperationsUseCase
import com.lumina.domain.usecase.ManageFrameThrottlingUseCase
import com.lumina.domain.usecase.ProcessFrameUseCase
import com.lumina.domain.usecase.ProcessNavigationCueFlowUseCase
import com.lumina.domain.usecase.ProcessVoiceCommandUseCase
import com.lumina.domain.usecase.ReadReceiptUseCase
import com.lumina.domain.usecase.ReadTextUseCase
import com.lumina.domain.usecase.StartCrossingModeUseCase
import com.lumina.domain.usecase.StartNavigationUseCase
import com.lumina.domain.usecase.StopAllOperationsUseCase
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Data class representing the UI state for the Scene Explorer screen.
 */
data class SceneExplorerUiState(
    val description: String = "",
    val initializationState: InitializationState = InitializationState.NotInitialized,
    val alertType: NavigationCueType = NavigationCueType.NONE,
    val isTtsInitialized: Boolean = false
)

/**
 * Refactored ViewModel with business logic extracted to domain use cases.
 *
 * This ViewModel now focuses on:
 * - UI state management
 * - Coordinating use cases
 * - Handling UI events
 * - Managing coroutine scopes
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

    private var isTtsInitialized = false
    private val _ttsState = MutableStateFlow(false)

    // Frame management
    private var lastFrameBytes: ByteArray? = null

    // Manual cue flow for user-initiated actions
    private val manualCueFlow = MutableSharedFlow<NavigationCue>()

    // Job management
    private var findJob: Job? = null
    private var crossingJob: Job? = null
    private var questionJob: Job? = null
    private var navigationJob: Job? = null

    // Camera state management
    val cameraMode = cameraStateService.currentMode
    val isCameraActive = cameraStateService.isActive

    init {
        initializeTextToSpeech()
    }

    /**
     * Main navigation cue flow with TTS processing handled by use case.
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
    ).let { flow ->
        handleTts.processNavigationCues(flow, _ttsState.value)
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
     * Generates scene description using domain use case.
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
     * Handles voice commands using domain use case.
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
     * Optimistically activates camera for voice commands.
     */
    fun optimisticallyActivateCamera() {
        manageCameraOperations.optimisticallyActivateCamera()
        Log.d(TAG, "🎤📸 Optimistically activated camera for voice command")
    }

    // Simplified operation methods that delegate to domain use cases
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

    fun stopNavigationAndCamera() {
        navigationJob?.cancel()
        navigationJob = null
        stopAllOperations()
        manageCameraOperations.deactivateCamera()
        handleTts.speak("Navigation stopped", _ttsState.value)
    }

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

    private fun askQuestion(question: String) {
        questionJob?.cancel()
        questionJob = viewModelScope.launch {
            askQuestionUseCase(question).collect { cue ->
                manualCueFlow.emit(cue)
            }
        }
    }

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

    private fun deactivateTextReadingMode() {
        manageCameraOperations.deactivateCamera()
        stopAllOperations()
    }

    private fun deactivateCameraAndClearBuffer() {
        manageCameraOperations.deactivateCamera()
        stopAllOperations()
        Log.d(TAG, "🎤🔴 Deactivated optimistic camera and cleared buffer")
    }

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

    // Simple delegation methods
    fun repeatCurrentDescription() {
        val currentDescription = uiState.value.description
        if (currentDescription.isNotEmpty() && isTtsInitialized) {
            textToSpeechService.speak(currentDescription)
        }
    }

    fun stopSpeech() = handleTts.stopSpeech()
    fun setSpeechRate(rate: Float) = handleTts.setSpeechRate(rate)
    fun speak(text: String) = handleTts.speak(text, _ttsState.value)
    fun isSpeaking(): Boolean = handleTts.isSpeaking()

    override fun onCleared() {
        super.onCleared()
        stopAllActiveOperations()
        textToSpeechService.shutdown()
        manageCameraOperations.deactivateCamera()
    }
}
