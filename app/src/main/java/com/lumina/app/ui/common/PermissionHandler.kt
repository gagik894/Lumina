package com.lumina.app.ui.common

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Handles runtime requests for both camera and microphone permissions, providing
 * callbacks for granted / denied states. The camera is needed for vision tasks
 * and the microphone for voice-commands (Find mode).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HandleCameraPermission(
    onPermissionGranted: @Composable () -> Unit,
    onPermissionDenied: @Composable () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val allGranted = cameraPermissionState.status.isGranted && audioPermissionState.status.isGranted

    if (allGranted) {
        onPermissionGranted()
    } else {
        // Request permissions sequentially to avoid Android permission dialog conflicts
        LaunchedEffect(cameraPermissionState.status.isGranted) {
            if (!cameraPermissionState.status.isGranted) {
                cameraPermissionState.launchPermissionRequest()
            } else if (!audioPermissionState.status.isGranted) {
                // Only request audio permission after camera permission is granted
                audioPermissionState.launchPermissionRequest()
            }
        }

        if (cameraPermissionState.status.shouldShowRationale || audioPermissionState.status.shouldShowRationale) {
            // Provide a simple explanation UI for missing permissions.
            Column(modifier = Modifier.padding(16.dp)) {
                val missingPermissions = mutableListOf<String>()
                if (!cameraPermissionState.status.isGranted) missingPermissions.add("Camera")
                if (!audioPermissionState.status.isGranted) missingPermissions.add("Microphone")

                Text("${missingPermissions.joinToString(" and ")} permission${if (missingPermissions.size > 1) "s are" else " is"} required for Lumina to work.")
                Button(onClick = {
                    // Request permissions sequentially
                    if (!cameraPermissionState.status.isGranted) {
                        cameraPermissionState.launchPermissionRequest()
                    } else if (!audioPermissionState.status.isGranted) {
                        audioPermissionState.launchPermissionRequest()
                    }
                }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Grant permissions")
                }
            }
        } else {
            onPermissionDenied()
        }
    }
}