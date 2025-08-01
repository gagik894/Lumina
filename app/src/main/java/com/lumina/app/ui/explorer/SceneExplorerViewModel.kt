package com.lumina.app.ui.explorer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.TextToSpeechService
import com.lumina.domain.usecase.AskQuestionUseCase
import com.lumina.domain.usecase.DescribeSceneUseCase
import com.lumina.domain.usecase.FindObjectUseCase
import com.lumina.domain.usecase.GetInitializationStateUseCase
import com.lumina.domain.usecase.GetNavigationCuesUseCase
import com.lumina.domain.usecase.IdentifyCurrencyUseCase
import com.lumina.domain.usecase.ProcessFrameUseCase
import com.lumina.domain.usecase.ReadReceiptUseCase
import com.lumina.domain.usecase.ReadTextUseCase
import com.lumina.domain.usecase.StartCrossingModeUseCase
import com.lumina.domain.usecase.StopNavigationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Data class representing the UI state for the Scene Explorer screen.
 *
 * @property description Current scene description text being displayed to the user
 * @property initializationState Current state of the AI model initialization
 * @property alertType Type of the current navigation cue for UI styling
 * @property isTtsInitialized Whether text-to-speech is ready for use
 */
data class SceneExplorerUiState(
    val description: String = "",
    val initializationState: InitializationState = InitializationState.NotInitialized,
    val alertType: NavigationCueType = NavigationCueType.NONE,
    val isTtsInitialized: Boolean = false
)

/**
 * Enum representing different types of navigation cues for UI styling.
 */
enum class NavigationCueType {
    NONE,
    CRITICAL,
    INFORMATIONAL,
    AMBIENT
}

/**
 * ViewModel for the Scene Explorer feature that manages camera frames and AI-generated navigation cues.
 *
 * @param getNavigationCues Use case for getting continuous navigation cues
 * @param getInitializationState Use case for monitoring AI model initialization
 * @param processFrame Use case for processing camera frames
 * @param describeScene Use case for describing scenes
 * @param startCrossingMode Use case for starting crossing mode
 * @param findObject Use case for finding objects
 * @param askQuestionUseCase Use case for asking questions
 * @param stopNavigation Use case for stopping navigation
 * @param identifyCurrency Use case for identifying currency
 * @param readReceipt Use case for reading receipts
 * @param readText Use case for reading general text
 * @param textToSpeechService Service for converting navigation cues to speech
 */
@HiltViewModel
class SceneExplorerViewModel @Inject constructor(
    private val getNavigationCues: GetNavigationCuesUseCase,
    getInitializationState: GetInitializationStateUseCase,
    private val processFrame: ProcessFrameUseCase,
    private val describeScene: DescribeSceneUseCase,
    private val startCrossingModeUseCase: StartCrossingModeUseCase,
    private val findObject: FindObjectUseCase,
    private val askQuestionUseCase: AskQuestionUseCase,
    private val stopNavigation: StopNavigationUseCase,
    private val identifyCurrency: IdentifyCurrencyUseCase,
    private val readReceipt: ReadReceiptUseCase,
    private val readText: ReadTextUseCase,
    private val textToSpeechService: TextToSpeechService
) : ViewModel() {

    private var lastFrameProcessedTime = 0L
    private val frameThrottleInterval = 100L // Process max 10 frames per second
    private var isProcessingFrame = false
    private var isTtsInitialized = false
    private val _ttsState = MutableStateFlow(false)

    // Holds the most recent compressed frame so it can be reused for on-demand
    // "investigate" requests without waiting for the next camera callback.
    private var lastFrameBytes: ByteArray? = null

    // Emits user-initiated cues (e.g., from investigateScene) into the primary
    // navigation cue pipeline.
    private val manualCueFlow = MutableSharedFlow<NavigationCue>()

    private var findJob: Job? = null
    private var crossingJob: Job? = null
    private var questionJob: Job? = null

    init {
        initializeTextToSpeech()
    }

    private val navigationCueFlow = merge(
        getNavigationCues(),
        manualCueFlow
    )
        .onEach { navigationCue ->
            // Vocalize each chunk as soon as it arrives for real-time feedback.
            if (_ttsState.value) {
                val message = when (navigationCue) {
                    is NavigationCue.CriticalAlert -> navigationCue.message
                    is NavigationCue.InformationalAlert -> navigationCue.message
                    is NavigationCue.AmbientUpdate -> navigationCue.message
                }

                // Skip empty strings generated while the model is thinking.
                if (message.isNotBlank()) {
                    textToSpeechService.speak(navigationCue)
                }
            }
        }
        .scan(Pair("", NavigationCueType.NONE)) { (accumulator, _), navigationCue ->
            when (navigationCue) {
                is NavigationCue.CriticalAlert -> {
                    val text = if (navigationCue.isDone) "" else accumulator + navigationCue.message
                    Pair(text, NavigationCueType.CRITICAL)
                }

                is NavigationCue.InformationalAlert -> {
                    val text = if (navigationCue.isDone) "" else accumulator + navigationCue.message
                    Pair(text, NavigationCueType.INFORMATIONAL)
                }

                is NavigationCue.AmbientUpdate -> {
                    val text = if (navigationCue.isDone) "" else accumulator + navigationCue.message
                    Pair(text, NavigationCueType.AMBIENT)
                }
            }
        }

    /**
     * UI state flow combining initialization status, navigation cues, and TTS status.
     * This flow provides a single source of truth for the UI layer.
     */
    val uiState: StateFlow<SceneExplorerUiState> =
        combine(
            getInitializationState(),
            navigationCueFlow,
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
     * Initializes the text-to-speech service.
     */
    private fun initializeTextToSpeech() {
        textToSpeechService.initialize(
            onInitialized = {
                _ttsState.value = true
                android.util.Log.d("SceneExplorerViewModel", "TTS initialized successfully")
            },
            onError = { error ->
                android.util.Log.e("SceneExplorerViewModel", "TTS initialization failed: $error")
                _ttsState.value = false
            }
        )
    }

    /**
     * Processes a new camera frame for AI analysis with proper throttling.
     *
     * This method implements frame throttling to prevent MediaPipe timestamp conflicts
     * by ensuring frames are processed at a controlled rate and preventing overlapping
     * processing operations.
     *
     * @param image Camera frame bitmap to be processed
     */
    fun onFrameReceived(image: Bitmap) {
        val currentTime = System.currentTimeMillis()

        // Throttle frame processing to prevent MediaPipe timestamp issues
        if (isProcessingFrame ||
            (currentTime - lastFrameProcessedTime) < frameThrottleInterval
        ) {
            image.recycle() // Important: recycle dropped frames to prevent memory leaks
            return
        }

        isProcessingFrame = true
        lastFrameProcessedTime = currentTime

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = ByteArrayOutputStream()
                image.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                val bytes = stream.toByteArray()
                lastFrameBytes = bytes
                processFrame(ImageInput(bytes))
            } catch (e: Exception) {
                // Record the error; the UI continues to function.
                android.util.Log.e("SceneExplorerViewModel", "Error processing frame", e)
            } finally {
                image.recycle()
                isProcessingFrame = false
            }
        }
    }

    /**
     * Generates a detailed, one-off description of the current scene.  Called
     * when the user performs the designated gesture (double-tap).
     */
    fun investigateScene() {
        val bytes = lastFrameBytes ?: return
        viewModelScope.launch(Dispatchers.IO) {
            describeScene(ImageInput(bytes)).collect { cue ->
                // Forward the cue into the shared flow so that UI and TTS treat
                // it the same way as continuous navigation cues.
                manualCueFlow.emit(cue)
            }
        }
    }

    /**
     * Starts a street crossing session.
     * The session completes automatically when the AI determines the crossing is finished.
     */
    private fun startCrossingMode() {
        crossingJob?.cancel()
        crossingJob = viewModelScope.launch(Dispatchers.IO) {
            startCrossingModeUseCase()
                .collect { cue -> manualCueFlow.emit(cue) }
        }
    }

    /**
     * Manually stops an active crossing session, if any.
     */
    private fun stopCrossingMode() {
        crossingJob?.cancel()
        crossingJob = null
    }

    /**
     * Starts an object-finding session. The session completes automatically
     * when the requested object is detected.
     */
    fun startFindMode(target: String) {
        findJob?.cancel()
        findJob = viewModelScope.launch(Dispatchers.IO) {
            findObject(target)
                .collect { cue -> manualCueFlow.emit(cue) }
        }
    }

    /** Stops an active find session, if any. */
    fun stopFindMode() {
        findJob?.cancel()
        findJob = null
    }

    /** Entry point from voice commands. */
    fun handleVoiceCommand(command: String) {
        val lower = command.trim().lowercase()

        when {
            lower.startsWith("find ") -> {
                val target = lower.removePrefix("find ").trim()
                if (target.isNotEmpty()) {
                    speak("Searching for $target")
                    startFindMode(target)
                }
            }

            lower == "cancel" || lower == "stop" -> {
                stopFindMode()
                stopCrossingMode()
                speak("Operation cancelled")
            }

            lower == "cross street" -> {
                speak("Starting crossing mode. Please wait for instructions.")
                startCrossingMode()
            }

            lower == "read money" || lower == "identify currency" -> {
                speak("Identifying currency")
                identifyCurrencyFromFrame()
            }

            lower == "read receipt" -> {
                speak("Reading receipt")
                readReceiptFromFrame()
            }

            lower == "read text" -> {
                speak("Reading text")
                readTextFromFrame()
            }

            lower.startsWith("question") -> {
                val question = lower.removePrefix("question").trim()
                if (question.isNotEmpty()) {
                    askQuestion(question)
                } else {
                    speak("What is your question?")
                }
            }
            else -> {
                val question = lower.trim()
                askQuestion(question)
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

    /**
     * Manually triggers speech for the current description.
     * Useful for repeat functionality.
     */
    fun repeatCurrentDescription() {
        val currentDescription = uiState.value.description
        if (currentDescription.isNotEmpty() && isTtsInitialized) {
            textToSpeechService.speak(currentDescription)
        }
    }

    /**
     * Stops current speech output.
     */
    fun stopSpeech() {
        textToSpeechService.stop()
    }

    /**
     * Adjusts speech rate for user preference.
     *
     * @param rate Speech rate (0.1 to 3.0, where 1.0 is normal)
     */
    fun setSpeechRate(rate: Float) {
        textToSpeechService.setSpeechRate(rate)
    }

    /** Speaks a short prompt through the shared text-to-speech service. */
    fun speak(text: String) {
        if (_ttsState.value && text.isNotBlank()) {
            textToSpeechService.speak(text)
        }
    }

    /** Returns true if TTS is currently speaking. */
    fun isSpeaking(): Boolean = textToSpeechService.isSpeaking()

    /**
     * Identifies currency from the current camera frame.
     * Uses the most recent captured frame for analysis.
     */
    fun identifyCurrencyFromFrame() {
        lastFrameBytes?.let { frameBytes ->
            viewModelScope.launch(Dispatchers.IO) {
                val imageInput = ImageInput(frameBytes)
                identifyCurrency(imageInput)
                    .collect { cue -> manualCueFlow.emit(cue) }
            }
        } ?: run {
            speak("No image available. Please point camera at currency and try again.")
        }
    }

    /**
     * Reads receipt or document from the current camera frame.
     * Uses the most recent captured frame for analysis.
     */
    fun readReceiptFromFrame() {
        lastFrameBytes?.let { frameBytes ->
            viewModelScope.launch(Dispatchers.IO) {
                val imageInput = ImageInput(frameBytes)
                readReceipt(imageInput)
                    .collect { cue -> manualCueFlow.emit(cue) }
            }
        } ?: run {
            speak("No image available. Please point camera at receipt and try again.")
        }
    }

    /**
     * Reads any text from the current camera frame.
     * Uses the most recent captured frame for analysis.
     */
    fun readTextFromFrame() {
        lastFrameBytes?.let { frameBytes ->
            viewModelScope.launch(Dispatchers.IO) {
                val imageInput = ImageInput(frameBytes)
                readText(imageInput)
                    .collect { cue -> manualCueFlow.emit(cue) }
            }
        } ?: run {
            speak("No image available. Please point camera at text and try again.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        questionJob?.cancel()
        findJob?.cancel()
        crossingJob?.cancel()
        textToSpeechService.shutdown()
        stopNavigation()
    }
}