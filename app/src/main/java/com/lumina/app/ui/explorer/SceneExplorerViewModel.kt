package com.lumina.app.ui.explorer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
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
 */
data class SceneExplorerUiState(
    val description: String = "",
    val initializationState: InitializationState = InitializationState.NotInitialized
)

/**
 * ViewModel for the Scene Explorer feature that manages camera frames and AI-generated descriptions.
 *
 * This ViewModel coordinates between the camera input and the AI processing pipeline,
 * providing real-time scene descriptions for visually impaired users. It handles:
 * - Processing camera frames from the UI
 * - Monitoring AI model initialization state
 * - Streaming scene descriptions to the UI
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

    private val descriptionFlow = luminaRepository.getProactiveNavigationCues()
        .scan("") { accumulator, value ->
            if (value.isDone) "" else accumulator + value.partialResponse
        }

    /**
     * UI state flow combining initialization status and scene descriptions.
     * This flow provides a single source of truth for the UI layer.
     */
    val uiState: StateFlow<SceneExplorerUiState> =
        combine(
            getInitializationState(),
            descriptionFlow
        ) { initState, description ->
            SceneExplorerUiState(
                initializationState = initState,
                description = description
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SceneExplorerUiState()
        )

    /**
     * Processes a new camera frame for AI analysis.
     *
     * This method converts the bitmap to the required format and forwards it to the
     * repository for object detection and potential AI description generation.
     * The processing is performed on the IO dispatcher to avoid blocking the UI.
     *
     * @param image Camera frame bitmap to be processed
     */
    fun onFrameReceived(image: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val stream = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            luminaRepository.processNewFrame(ImageInput(stream.toByteArray()))
            image.recycle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        luminaRepository.stopProactiveNavigation()
    }
}