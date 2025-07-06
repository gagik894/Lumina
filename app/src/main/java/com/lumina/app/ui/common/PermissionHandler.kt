package com.lumina.app.ui.common

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/** * Composable function to handle camera permission requests.
 *
 * @param onPermissionGranted Composable to display when permission is granted.
 * @param onPermissionDenied Composable to display when permission is denied.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HandleCameraPermission(
    onPermissionGranted: @Composable () -> Unit,
    onPermissionDenied: @Composable () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        onPermissionGranted()
    } else {
        LaunchedEffect(Unit) {
            cameraPermissionState.launchPermissionRequest()
        }
        if (cameraPermissionState.status.shouldShowRationale) {
            // Show a UI explaining why the permission is needed
            onPermissionDenied()
        } else {
            // The user has permanently denied the permission
            onPermissionDenied()
        }
    }
}