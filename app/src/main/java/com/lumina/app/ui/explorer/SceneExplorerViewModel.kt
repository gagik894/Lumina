package com.lumina.app.ui.explorer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.domain.model.ImageInput
import com.lumina.domain.usecase.DescribeSceneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Defines the immutable state for the SceneExplorerScreen.
 */
data class SceneExplorerUiState(
    val isLoading: Boolean = false,
    val description: String = "Point your camera and take a photo to explore the scene."
)

@HiltViewModel
class SceneExplorerViewModel @Inject constructor(
    private val describeSceneUseCase: DescribeSceneUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SceneExplorerUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Called by the UI when a photo is taken.
     * This function launches a coroutine to process the image and collect the streaming response from the AI.
     */
    fun onTakePhoto(image: Bitmap) {
        viewModelScope.launch {
            var fullResponse = ""

            val imageInput = withContext(Dispatchers.IO) {
                val stream = ByteArrayOutputStream()
                image.compress(
                    Bitmap.CompressFormat.JPEG,
                    90,
                    stream
                ) // 90% quality is a good balance
                ImageInput(stream.toByteArray())
            }

            describeSceneUseCase(imageInput)
                .onStart {
                    _uiState.update { it.copy(isLoading = true, description = "") }
                }
                .catch { error ->

                    _uiState.update {
                        it.copy(isLoading = false, description = "Error: ${error.localizedMessage}")
                    }
                }
                .collect { sceneDescription ->
                    fullResponse += sceneDescription.partialResponse
                    _uiState.update {
                        it.copy(
                            isLoading = !sceneDescription.isDone,
                            description = fullResponse
                        )
                    }
                }
        }
    }
}