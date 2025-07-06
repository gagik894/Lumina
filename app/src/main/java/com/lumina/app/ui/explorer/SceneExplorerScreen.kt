package com.lumina.app.ui.explorer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumina.app.ui.camera.CameraScreen
import com.lumina.app.ui.common.HandleCameraPermission
import com.lumina.domain.model.InitializationState

/**
 * Main screen for the Scene Explorer feature that provides intelligent navigation cues
 * for visually impaired users.
 *
 * This composable manages the camera feed and displays AI-generated navigation cues with
 * different urgency levels and styling:
 * - Critical alerts: Red background, bold text for immediate threats
 * - Informational alerts: Blue background for new important objects
 * - Ambient updates: Subtle gray background for general environmental context
 * - Loading: Shows a progress indicator while the AI model initializes
 * - Error: Displays error messages if initialization fails
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

                        if (uiState.description.isNotEmpty()) {
                            NavigationCueDisplay(
                                text = uiState.description,
                                alertType = uiState.alertType,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            )
                        }
                    }
                    is InitializationState.Error -> {
                        Text(
                            text = (uiState.initializationState as InitializationState.Error).message,
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

/**
 * Displays navigation cues with appropriate styling based on alert type.
 *
 * @param text The navigation cue text to display
 * @param alertType The type of alert for styling purposes
 * @param modifier Modifier for the composable
 */
@Composable
private fun NavigationCueDisplay(
    text: String,
    alertType: NavigationCueType,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, fontWeight) = when (alertType) {
        NavigationCueType.CRITICAL -> Triple(
            Color.Red.copy(alpha = 0.9f),
            Color.White,
            FontWeight.Bold
        )

        NavigationCueType.INFORMATIONAL -> Triple(
            Color.Blue.copy(alpha = 0.8f),
            Color.White,
            FontWeight.Medium
        )

        NavigationCueType.AMBIENT -> Triple(
            Color.Gray.copy(alpha = 0.7f),
            Color.White,
            FontWeight.Normal
        )

        NavigationCueType.NONE -> Triple(
            Color.Black.copy(alpha = 0.6f),
            Color.White,
            FontWeight.Normal
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = fontWeight,
            color = textColor
        ),
        modifier = modifier
            .background(backgroundColor, MaterialTheme.shapes.medium)
            .padding(12.dp)
    )
}
