package com.lumina.app.ui.explorer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
 * different urgency levels and styling. It also provides text-to-speech functionality
 * with manual controls for accessibility:
 * - Critical alerts: Red background, bold text, immediate speech for threats
 * - Informational alerts: Blue background, normal speech for new objects
 * - Ambient updates: Gray background, calm speech for general context
 * - Manual controls: Repeat and stop speech buttons
 * - Loading: Shows progress indicators for both AI model and TTS initialization
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { viewModel.investigateScene() })
                    }
            ) {
                when (uiState.initializationState) {
                    is InitializationState.NotInitialized, is InitializationState.Initializing -> {
                        InitializationScreen(uiState.isTtsInitialized)
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

                        // TTS Controls
                        TtsControlPanel(
                            onRepeat = { viewModel.repeatCurrentDescription() },
                            onStop = { viewModel.stopSpeech() },
                            isTtsInitialized = uiState.isTtsInitialized,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        )
                    }
                    is InitializationState.Error -> {
                        Text(
                            text = (uiState.initializationState as InitializationState.Error).message,
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
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
 * Shows initialization progress for both AI model and TTS.
 */
@Composable
private fun InitializationScreen(isTtsInitialized: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Initializing Lumina",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // AI Model initialization
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = "AI Model...",
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        // TTS initialization
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            if (isTtsInitialized) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "TTS Ready",
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            Text(
                text = if (isTtsInitialized) "Text-to-Speech Ready" else "Text-to-Speech...",
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

/**
 * Control panel for text-to-speech functionality.
 */
@Composable
private fun TtsControlPanel(
    onRepeat: () -> Unit,
    onStop: () -> Unit,
    isTtsInitialized: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isTtsInitialized) return

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onRepeat,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Repeat description",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onStop,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop speech",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
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
