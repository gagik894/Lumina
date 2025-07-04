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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

// Defines the state of our UI. It's an immutable data class.
data class SceneExplorerUiState(
    val isLoading: Boolean = false,
    val description: String = "Point your camera and take a photo to explore the scene."
)

@HiltViewModel
class SceneExplorerViewModel @Inject constructor(
    private val describeSceneUseCase: DescribeSceneUseCase // Hilt provides this Use Case
) : ViewModel() {

    private val _uiState = MutableStateFlow(SceneExplorerUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Called by the UI when a photo is taken.
     * This function handles the entire logic flow in a background coroutine.
     */
    fun onTakePhoto(image: Bitmap) {
        // Set loading state to true immediately
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                // Perform the bitmap-to-byte-array conversion on a background thread
                val imageInput = withContext(Dispatchers.IO) {
                    val stream = ByteArrayOutputStream()
                    image.compress(
                        Bitmap.CompressFormat.JPEG,
                        90,
                        stream
                    ) // 90% quality is a good balance
                    ImageInput(stream.toByteArray())
                }

                // Call our pure domain use case with the domain-specific model
                val sceneDescription = describeSceneUseCase(imageInput)

                // Update the UI with the successful result
                _uiState.update {
                    it.copy(isLoading = false, description = sceneDescription.fullText)
                }
            } catch (e: Exception) {
                // Update the UI with a user-friendly error message
                _uiState.update {
                    it.copy(isLoading = false, description = "Error: ${e.localizedMessage}")
                }
            }
        }
    }
}