package com.lumina.app.ui.explorer

import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumina.app.ui.camera.CameraScreen
import com.lumina.app.ui.common.HandleCameraPermission
import com.lumina.domain.model.InitializationState
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "SceneExplorerScreen"

/**
 * Main screen for the Scene Explorer feature that provides intelligent navigation cues
 * for visually impaired users.
 *
 * This composable manages the camera feed and provides text-to-speech functionality
 * with voice controls for accessibility. The UI is simplified with:
 * - Black background for minimal visual distraction
 * - No visual navigation cue display (audio-only)
 * - No debug controls or camera preview toggle
 * - Voice commands: Long-press to talk, double-tap to explore scene
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
    val context = LocalContext.current
    HandleCameraPermission(
        onPermissionGranted = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black) // Always black background
                    .combinedClickable(
                        onLongClick = {
                            viewModel.stopAllOperationsAndGeneration(); startVoiceCommand(
                            context,
                            viewModel
                        )
                        },
                        onDoubleClick = { viewModel.investigateScene() },
                        onClick = {}
                    )
            ) {
                when (uiState.initializationState) {
                    is InitializationState.NotInitialized, is InitializationState.Initializing -> {
                        InitializationScreen(uiState.isTtsInitialized)
                    }
                    is InitializationState.Initialized -> {
                        val cameraMode by viewModel.cameraMode.collectAsState()
                        val isCameraActive by viewModel.isCameraActive.collectAsState()

                        // Log camera state changes for debugging
                        androidx.compose.runtime.LaunchedEffect(cameraMode, isCameraActive) {
                            Log.d(
                                TAG,
                                "📱 UI Camera State: mode=$cameraMode, active=$isCameraActive"
                            )
                        }

                        // Configure camera based on current mode
                        val cameraConfig = when (cameraMode) {
                            com.lumina.domain.service.CameraStateService.CameraMode.NAVIGATION ->
                                com.lumina.app.ui.camera.CameraConfig.NAVIGATION

                            com.lumina.domain.service.CameraStateService.CameraMode.TEXT_READING ->
                                com.lumina.app.ui.camera.CameraConfig.TEXT_READING

                            com.lumina.domain.service.CameraStateService.CameraMode.PHOTO_CAPTURE ->
                                com.lumina.app.ui.camera.CameraConfig.PHOTO_CAPTURE

                            com.lumina.domain.service.CameraStateService.CameraMode.INACTIVE ->
                                com.lumina.app.ui.camera.CameraConfig.NAVIGATION // Default config when inactive
                        }
                        
                        CameraScreen(
                            onFrame = viewModel::onFrameReceived,
                            showPreview = false, // Always hidden for simplified UI
                            config = cameraConfig,
                            isActive = isCameraActive
                        )

                        // Long-press anywhere to start voice-based Find mode.
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

@OptIn(DelicateCoroutinesApi::class)
private fun startVoiceCommand(context: android.content.Context, viewModel: SceneExplorerViewModel) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return

    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "What object are you looking for?")
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
    }

    // Optimistically activate camera for voice commands that might need it
    // This provides faster response time for text reading and object finding commands
    viewModel.optimisticallyActivateCamera()

    // Audible prompt for the user.
    viewModel.speak("Listening.")

    var provisional: String? = null

    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: android.os.Bundle) {
            Log.d(TAG, "onResults: ${results}")
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val query = matches?.firstOrNull()?.trim() ?: provisional
            if (query.isNullOrBlank()) {
                viewModel.speak("Sorry, I didn't catch that.")
                return
            }
            viewModel.handleVoiceCommand(query)
            recognizer.destroy()
        }

        override fun onError(error: Int) {
            Log.d(TAG, "onError:  $error")
            viewModel.speak("Sorry, I didn't catch that.")
            recognizer.destroy()
        }


        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech: ")
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            provisional = list?.firstOrNull()?.trim()
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    })

    // Wait for TTS prompt to finish, then play a start beep and begin listening.
    GlobalScope.launch(Dispatchers.Main) {
        while (viewModel.isSpeaking()) {
            delay(100)
        }
        recognizer.startListening(intent)
    }
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
