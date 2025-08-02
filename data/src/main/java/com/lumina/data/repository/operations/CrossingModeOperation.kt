package com.lumina.data.repository.operations

import android.util.Log
import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.AlertCoordinator
import com.lumina.data.repository.FrameBufferManager
import com.lumina.data.repository.TransientOperationCoordinator
import com.lumina.domain.model.NavigationCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CrossingModeOperation"

class CrossingModeOperation @Inject constructor(
    private val transientOperationCoordinator: TransientOperationCoordinator,
    private val frameBufferManager: FrameBufferManager,
    private val alertCoordinator: AlertCoordinator,
    private val aiOperationHelper: AiOperationHelper,
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    fun execute(): Flow<NavigationCue> {
        return callbackFlow {
            try {
                transientOperationCoordinator.executeTransientOperation("crossing_mode") {
                    if (!isActive) return@executeTransientOperation

                    Log.d(TAG, "Starting transient CROSSING operation")
                    val crossingJob = repositoryScope.launch {
                        frameBufferManager.getFrameFlow().collect {
                            val frames = frameBufferManager.getMotionAnalysisFrames()
                            if (frames.isEmpty()) return@collect

                            try {
                                aiOperationHelper.withAiOperation {
                                    alertCoordinator.coordinateCrossingGuidance(
                                        frames,
                                        aiOperationHelper::generateResponse
                                    ) {
                                        // Crossing complete callback
                                        Log.i(TAG, "Crossing complete signal received")
                                        this@callbackFlow.close()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error during crossing guidance", e)
                                this@callbackFlow.close(e)
                            }
                        }
                    }
                    awaitClose {
                        Log.d(TAG, "Closing transient CROSSING operation.")
                        crossingJob.cancel()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Crossing mode failed", e)
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to start crossing mode. Please try again.",
                        true
                    )
                )
                close()
            }
        }
    }
}
