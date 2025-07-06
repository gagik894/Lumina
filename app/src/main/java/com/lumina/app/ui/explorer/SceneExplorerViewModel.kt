package com.lumina.app.ui.explorer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import com.lumina.domain.usecase.GetInitializationStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 */
data class SceneExplorerUiState(
    val description: String = "",
    val initializationState: InitializationState = InitializationState.NotInitialized,
    val alertType: NavigationCueType = NavigationCueType.NONE
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
 * This ViewModel coordinates between the camera input and the AI processing pipeline,
 * providing intelligent navigation cues with different urgency levels for visually impaired users.
 * It handles:
 * - Processing camera frames from the UI with proper throttling
 * - Monitoring AI model initialization state
 * - Streaming different types of navigation cues to the UI
 * - Resource cleanup on destruction
 *
 * @param luminaRepository Repository providing access to AI and object detection services
 * @param getInitializationState Use case for monitoring AI model initialization
 */
@HiltViewModel
class SceneExplorerViewModel @Inject constructor(
    private val luminaRepository: LuminaRepository,
    getInitializationState: GetInitializationStateUseCase
) : ViewModel() {

    private var lastFrameProcessedTime = 0L
    private val frameThrottleInterval = 100L // Process max 10 frames per second
    private var isProcessingFrame = false

    private val navigationCueFlow = luminaRepository.getNavigationCues()
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
     * UI state flow combining initialization status and navigation cues.
     * This flow provides a single source of truth for the UI layer.
     */
    val uiState: StateFlow<SceneExplorerUiState> =
        combine(
            getInitializationState(),
            navigationCueFlow
        ) { initState, (description, alertType) ->
            SceneExplorerUiState(
                initializationState = initState,
                description = description,
                alertType = alertType
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SceneExplorerUiState()
        )

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
                image.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                luminaRepository.processNewFrame(ImageInput(stream.toByteArray()))
            } catch (e: Exception) {
                // Log the error but don't crash the app
                android.util.Log.e("SceneExplorerViewModel", "Error processing frame", e)
            } finally {
                image.recycle()
                isProcessingFrame = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        luminaRepository.stopNavigation()
    }
}