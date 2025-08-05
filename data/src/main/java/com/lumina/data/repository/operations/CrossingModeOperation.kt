package com.lumina.data.repository.operations

import android.util.Log
import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.AlertCoordinator
import com.lumina.data.repository.FrameBufferManager
import com.lumina.data.repository.TransientOperationCoordinator
import com.lumina.data.service.TrafficLightTimerService
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.PromptGenerationService
import com.lumina.domain.service.TwoPhasePromptManager
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

class CrossingModeOperation @Inject constructor(
    private val transientOperationCoordinator: TransientOperationCoordinator,
    private val frameBufferManager: FrameBufferManager,
    private val alertCoordinator: AlertCoordinator,
    private val aiOperationHelper: AiOperationHelper,
    private val trafficLightTimerService: TrafficLightTimerService,
    private val twoPhasePromptManager: TwoPhasePromptManager
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default)
    private var currentCrossingJob: Job? = null

    // Throttling mechanism for crossing mode
    @Volatile
    private var lastAiRequestTime = 0L
    private val aiRequestCooldownMs = 2000L // 5 seconds between requests

    companion object {
        private const val TAG = "CrossingModeOperation"
        private const val AI_REQUEST_COOLDOWN_SECONDS = 2
    }

    fun execute(): Flow<NavigationCue> {
        return callbackFlow {
            try {
                transientOperationCoordinator.executeTransientOperation("crossing_mode") {
                    if (!isActive) return@executeTransientOperation

                    Log.d(
                        TAG,
                        "Starting enhanced CROSSING operation with two-phase prompting"
                    )

                    // Create unique session ID for this crossing session
                    val sessionId = "crossing_${System.currentTimeMillis()}"

                    // Start two-phase prompting session
                    twoPhasePromptManager.startSession(
                        sessionId,
                        PromptGenerationService.OperationMode.CROSSING_GUIDANCE
                    )

                    // Reset throttling for immediate first frame processing
                    lastAiRequestTime = 0L

                    // Add a safety timeout to prevent crossing mode from running indefinitely
                    val timeoutJob = repositoryScope.launch {
                        delay(300_000) // 5 minutes timeout
                        Log.w(TAG, "Crossing mode timeout reached - automatically ending session")
                        trySend(
                            NavigationCue.InformationalAlert(
                                "Crossing mode ended due to timeout. Please restart if needed.",
                                true
                            )
                        )
                        this@callbackFlow.close()
                    }

                    // Listen for timer events
                    val timerEventJob = repositoryScope.launch {
                        trafficLightTimerService.timerEvents.collect { event ->
                            when (event) {
                                is TrafficLightTimerService.TimerEvent.Announcement -> {
                                    // Provide countdown feedback to user
                                    val message = if (event.remaining <= 5) {
                                        "${event.remaining}"
                                    } else {
                                        "${event.remaining} seconds remaining"
                                    }
                                    trySend(NavigationCue.InformationalAlert(message, true))
                                }

                                is TrafficLightTimerService.TimerEvent.Completed -> {
                                    trySend(
                                        NavigationCue.InformationalAlert(
                                            "Timer complete, checking crossing conditions",
                                            true
                                        )
                                    )
                                }

                                else -> {
                                    // Other timer events don't need user notification
                                }
                            }
                        }
                    }

                    // Listen for NavigationCues from AlertCoordinator and forward them to the UI
                    val alertCueCollectorJob = repositoryScope.launch {
                        alertCoordinator.getNavigationCueFlow().collect { cue ->
                            Log.d(
                                TAG,
                                "Forwarding NavigationCue from AlertCoordinator: ${cue.javaClass.simpleName}"
                            )
                            trySend(cue)
                        }
                    }

                    currentCrossingJob = repositoryScope.launch {
                        frameBufferManager.getFrameFlow().collect {
                            // Skip processing if timer is running (AI is paused)
                            if (trafficLightTimerService.isTimerRunning()) {
                                Log.d(TAG, "AI processing paused due to traffic light timer")
                                return@collect
                            }

                            // Throttle AI requests - wait for cooldown period after last request
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastRequest = currentTime - lastAiRequestTime
                            if (timeSinceLastRequest < aiRequestCooldownMs) {
                                val remainingCooldown = aiRequestCooldownMs - timeSinceLastRequest
                                Log.d(
                                    TAG,
                                    "Throttling AI request - ${remainingCooldown}ms remaining in cooldown"
                                )
                                return@collect
                            }

                            // Use best quality single frame instead of motion analysis frames
                            val bestFrame = frameBufferManager.getBestQualityFrame()
                            if (bestFrame == null) {
                                Log.d(TAG, "No quality frame available")
                                Log.d(TAG, "Buffer status: ${frameBufferManager.getBufferStatus()}")
                                return@collect
                            }

                            try {
                                lastAiRequestTime = currentTime
                                Log.i(
                                    TAG,
                                    "Sending single best quality frame to AI (${AI_REQUEST_COOLDOWN_SECONDS}s throttled)"
                                )
                                Log.d(TAG, "Buffer status: ${frameBufferManager.getBufferStatus()}")
                                
                                aiOperationHelper.withAiOperation {
                                    alertCoordinator.coordinateEnhancedCrossingGuidance(
                                        sessionId = sessionId,
                                        frames = listOf(bestFrame), // Single frame
                                        aiResponseGenerator = aiOperationHelper::generateResponse,
                                        onCrossingComplete = {
                                            // Crossing complete callback
                                            Log.i(TAG, "Crossing complete signal received")
                                            this@callbackFlow.close()
                                        },
                                        onTimerDetected = { seconds ->
                                            // Timer detected callback - start traffic light timer
                                            startTrafficLightTimer(seconds)
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error during crossing guidance", e)
                                this@callbackFlow.close(e)
                            }
                        }
                    }

                    awaitClose {
                        Log.d(TAG, "Closing enhanced CROSSING operation.")
                        currentCrossingJob?.cancel()
                        timerEventJob.cancel()
                        alertCueCollectorJob.cancel()
                        timeoutJob.cancel()
                        trafficLightTimerService.stopTimer()
                        // End the two-phase prompting session
                        repositoryScope.launch {
                            twoPhasePromptManager.endSession(sessionId)
                        }
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

    /**
     * Starts a traffic light countdown timer that pauses AI processing.
     * This prevents unnecessary AI processing when we know the user must wait.
     */
    private fun startTrafficLightTimer(seconds: Int) {
        Log.i(TAG, "Starting traffic light timer for $seconds seconds")
        repositoryScope.launch {
            trafficLightTimerService.startTimer(seconds)
        }
    }
}
