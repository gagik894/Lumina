package com.lumina.app.ui.explorer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumina.app.ui.camera.CameraScreen
import com.lumina.app.ui.common.HandleCameraPermission
import com.lumina.domain.model.InitializationState

/**
 * Main screen for the Scene Explorer feature that provides real-time scene descriptions
 * for visually impaired users.
 *
 * This composable manages the camera feed and displays AI-generated descriptions based
 * on the current initialization state of the AI model. It handles three main states:
 * - Loading: Shows a progress indicator while the AI model initializes
 * - Error: Displays error messages if initialization fails
 * - Ready: Shows the camera feed with overlaid scene descriptions
 *
 * The screen also handles camera permissions gracefully, showing appropriate messages
 * when permissions are denied.
 *
 * @param viewModel The ViewModel managing the scene exploration logic and state
 */
@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun SceneExplorerScreen(
    viewModel: SceneExplorerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    HandleCameraPermission(
        onPermissionGranted = {
            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState.initializationState) {
                    is InitializationState.NotInitialized, is InitializationState.Initializing -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Initializing AI model...",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    is InitializationState.Initialized -> {
                        CameraScreen(onFrame = viewModel::onFrameReceived)
                        Text(
                            text = uiState.description,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        )
                    }

                    is InitializationState.Error -> {
                        Text(
                            text = uiState.initializationState.message,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        },
        onPermissionDenied = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission is required to use Lumina.")
            }
        }
    )
}