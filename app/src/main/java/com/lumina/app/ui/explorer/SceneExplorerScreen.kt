package com.lumina.app.ui.explorer

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SceneExplorerScreen(
    viewModel: SceneExplorerViewModel = hiltViewModel()
) {
    // Observe the UI state from the ViewModel. Compose will automatically
    // recompose this screen whenever the state changes.
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Scene Explorer",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Box to display the description from the AI
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            if (uiState.isLoading) {
                // Show a loading indicator in the center when processing
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                // Show the description text, making it scrollable if it's long
                Text(
                    text = uiState.description,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // The button to trigger the process
        Button(
            onClick = {
                // TODO: In the real app, this will be replaced by a call from CameraX
                // after a picture is successfully taken.
                // For now, we create a dummy bitmap to test the entire pipeline.
                val dummyBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.DarkGray.hashCode()) // Make it not just black
                }
                viewModel.onTakePhoto(dummyBitmap)
            },
            // The button is disabled while the AI is thinking, preventing multiple clicks.
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "Capture Scene (Test)")
        }
    }
}