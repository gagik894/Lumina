package com.lumina.data.repository.operations

import android.util.Log
import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.AlertCoordinator
import com.lumina.data.repository.FrameBufferManager
import com.lumina.data.repository.NavigationModeManager
import com.lumina.data.repository.TransientOperationCoordinator
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.NavigationModeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NavigationOperations"

@Singleton
class NavigationOperations @Inject constructor(
    private val transientOperationCoordinator: TransientOperationCoordinator,
    private val frameBufferManager: FrameBufferManager,
    private val navigationModeManager: NavigationModeManager,
    private val alertCoordinator: AlertCoordinator,
    private val aiOperationHelper: AiOperationHelper
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default)
    private var ambientUpdateJob: Job? = null

    fun execute(): Flow<NavigationCue> = callbackFlow {
        try {
            transientOperationCoordinator.executeTransientOperation("navigation") {
                if (!isActive) return@executeTransientOperation

                Log.d(TAG, "Starting simplified navigation with 20-second ambient updates")

                // Start the 20-second ambient update timer
                ambientUpdateJob = repositoryScope.launch {
                    while (isActive) {
                        delay(20_000) // 20 seconds

                        if (!isActive) break

                        Log.d(TAG, "Triggering 20-second ambient update")

                        val frames = frameBufferManager.getMotionAnalysisFrames()
                        if (frames.isNotEmpty()) {
                            try {
                                aiOperationHelper.withAiOperation {
                                    alertCoordinator.coordinateAmbientUpdate(
                                        frames,
                                        aiOperationHelper::generateResponse
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error during ambient update", e)
                                // Continue the loop - don't break on individual update failures
                            }
                        } else {
                            Log.d(TAG, "No frames available for ambient update")
                        }
                    }
                }

                // Start navigation mode management
                navigationModeManager.startMode(
                    NavigationModeService.OperatingMode.NAVIGATION,
                    ambientUpdateJob!!
                )

                // Collect and forward navigation cues from AlertCoordinator
                val cueCollectorJob = launch {
                    alertCoordinator.getNavigationCueFlow().collect { cue ->
                        send(cue)
                    }
                }

                // Wait for operation completion
                ambientUpdateJob?.join()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Navigation operation failed", e)
            trySend(
                NavigationCue.InformationalAlert(
                    "Navigation temporarily unavailable. Please try again.",
                    true
                )
            )
        }

        awaitClose {
            Log.d(TAG, "Navigation operation closing")
            ambientUpdateJob?.cancel()
            navigationModeManager.stopAllModes()
        }
    }
}
