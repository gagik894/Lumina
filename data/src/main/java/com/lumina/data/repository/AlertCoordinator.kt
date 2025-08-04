package com.lumina.data.repository

import android.util.Log
import com.lumina.data.datasource.TimestampedFrame
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.TwoPhasePromptManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertCoordinator"

/**
 * Coordinates the generation and emission of navigation alerts based on threat assessments.
 *
 * This component acts as the central hub for alert management, orchestrating the interaction
 * between threat assessment results, AI response generation, and alert emission to the UI.
 * It ensures that alerts are:
 * - Generated with appropriate prompts for each scenario
 * - Properly typed according to urgency and context
 * - Efficiently streamed to the user interface
 * - Logged for debugging and monitoring purposes
 *
 * The coordinator maintains separation of concerns by delegating specific responsibilities
 * to specialized components while orchestrating the overall alert workflow.
 */
@Singleton
class AlertCoordinator @Inject constructor(
    private val promptGenerator: PromptGenerator,
    private val twoPhasePromptManager: TwoPhasePromptManager
) {

    /** Shared flow for broadcasting navigation cues to subscribers */
    private val navigationCueFlow = MutableSharedFlow<NavigationCue>()

    /**
     * Provides access to the navigation cue flow for external subscribers.
     *
     * @return Flow of navigation cues that can be collected by the UI layer
     */
    fun getNavigationCueFlow(): Flow<NavigationCue> = navigationCueFlow

    /**
     * Coordinates a critical alert based on detected threats.
     *
     * This method handles the highest-priority alerts for immediate dangers,
     * generating appropriate prompts and ensuring rapid response delivery.
     *
     * @param detectedObjects List of critical objects that triggered the alert
     * @param frames Current frames for AI analysis
     * @param aiResponseGenerator Function that generates AI responses given a prompt and frames
     */
    suspend fun coordinateCriticalAlert(
        detectedObjects: List<String>,
        frames: List<TimestampedFrame>,
        aiResponseGenerator: suspend (String, List<TimestampedFrame>) -> Flow<Pair<String, Boolean>>
    ) {
        Log.i(TAG, "Coordinating critical alert for objects: $detectedObjects")

        val prompt = promptGenerator.generateCriticalThreatPrompt()

        try {
            aiResponseGenerator(prompt, frames)
                .collect { (partialResponse, isDone) ->
                    val criticalAlert = NavigationCue.CriticalAlert(partialResponse, isDone)
                    navigationCueFlow.emit(criticalAlert)

                    if (isDone) {
                        Log.d(TAG, "Critical alert completed: $partialResponse")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating critical alert", e)
            // Emit a fallback alert to ensure user safety
            navigationCueFlow.emit(
                NavigationCue.CriticalAlert("Obstacle detected", true)
            )
        }
    }

    /**
     * Coordinates an informational alert for newly detected important objects.
     *
     * This method handles medium-priority alerts that inform users about significant
     * changes in their environment without indicating immediate danger.
     *
     * @param newObjects List of newly detected important objects
     * @param frames Current frames for AI analysis
     * @param aiResponseGenerator Function that generates AI responses given a prompt and frames
     */
    suspend fun coordinateInformationalAlert(
        newObjects: List<String>,
        frames: List<TimestampedFrame>,
        aiResponseGenerator: suspend (String, List<TimestampedFrame>) -> Flow<Pair<String, Boolean>>
    ) {
        Log.i(TAG, "Coordinating informational alert for new objects: $newObjects")

        val prompt = promptGenerator.generateInformationalAlertPrompt(newObjects)

        try {
            aiResponseGenerator(prompt, frames)
                .collect { (partialResponse, isDone) ->
                    val informationalAlert =
                        NavigationCue.InformationalAlert(partialResponse, isDone)
                    navigationCueFlow.emit(informationalAlert)

                    if (isDone) {
                        Log.d(TAG, "Informational alert completed: $partialResponse")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating informational alert", e)
            // Emit a fallback alert with basic information
            navigationCueFlow.emit(
                NavigationCue.InformationalAlert(
                    "New ${newObjects.firstOrNull() ?: "object"} detected",
                    true
                )
            )
        }
    }

    /**
     * Coordinates an ambient update for general environmental awareness.
     *
     * This method handles low-priority updates that provide general context about
     * the user's surroundings when no immediate threats or changes are detected.
     *
     * @param frames Current frames for AI analysis
     * @param aiResponseGenerator Function that generates AI responses given a prompt and frames
     */
    suspend fun coordinateAmbientUpdate(
        frames: List<TimestampedFrame>,
        aiResponseGenerator: suspend (String, List<TimestampedFrame>) -> Flow<Pair<String, Boolean>>
    ) {
        Log.i(TAG, "Coordinating ambient update")

        val prompt = promptGenerator.generateAmbientUpdatePrompt()

        try {
            aiResponseGenerator(prompt, frames)
                .collect { (partialResponse, isDone) ->
                    val ambientUpdate = NavigationCue.AmbientUpdate(partialResponse, isDone)
                    navigationCueFlow.emit(ambientUpdate)

                    if (isDone) {
                        Log.d(TAG, "Ambient update completed: $partialResponse")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating ambient update", e)
            // For ambient updates, we can fail silently as they're not critical
        }
    }

    /**
     * Coordinates crossing guidance alerts with automatic termination detection.
     *
     * This method handles the specialized crossing mode, monitoring for completion
     * signals and managing the alert flow lifecycle accordingly.
     *
     * @param frames Current frames for AI analysis
     * @param aiResponseGenerator Function that generates AI responses given a prompt and frames
     * @param onCrossingComplete Callback invoked when crossing is determined to be complete
     */
    suspend fun coordinateCrossingGuidance(
        frames: List<TimestampedFrame>,
        aiResponseGenerator: suspend (String, List<TimestampedFrame>) -> Flow<Pair<String, Boolean>>,
        onCrossingComplete: () -> Unit
    ) {
        Log.i(TAG, "Coordinating crossing guidance")

        val prompt = promptGenerator.generateCrossingGuidancePrompt()

        try {
            aiResponseGenerator(prompt, frames)
                .collect { (chunk, done) ->
                    if (chunk.contains("CROSSING COMPLETE", ignoreCase = true)) {
                        Log.i(TAG, "Crossing complete signal received from AI")
                        navigationCueFlow.emit(
                            NavigationCue.InformationalAlert("Crossing complete.", true)
                        )
                        onCrossingComplete()
                    } else if (chunk.isNotBlank()) {
                        navigationCueFlow.emit(NavigationCue.CriticalAlert(chunk, done))
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error during crossing guidance", e)
            // Emit a safety-oriented fallback message
            navigationCueFlow.emit(
                NavigationCue.CriticalAlert("Continue with caution", true)
            )
        }
    }

    /**
     * Enhanced crossing guidance with traffic light timing detection.
     *
     * This method provides advanced crossing guidance that can:
     * - Detect traffic light countdown timers and pause processing accordingly
     * - Automatically resume processing after countdown expires
     *
     * @param frames Current frames for AI analysis
     * @param aiResponseGenerator Function that generates AI responses given a prompt and frames
     * @param onCrossingComplete Callback invoked when crossing is determined to be complete
     * @param onTimerDetected Callback invoked when a traffic light timer is detected (seconds remaining)
     */
    suspend fun coordinateEnhancedCrossingGuidance(
        sessionId: String,
        frames: List<TimestampedFrame>,
        aiResponseGenerator: suspend (String, List<TimestampedFrame>) -> Flow<Pair<String, Boolean>>,
        onCrossingComplete: () -> Unit,
        onTimerDetected: suspend (Int) -> Unit = {}
    ) {
        Log.i(TAG, "Coordinating enhanced crossing guidance with session: $sessionId")

        // Get appropriate prompt using two-phase system
        val prompt = twoPhasePromptManager.getPrompt(sessionId)
            ?: promptGenerator.generateCrossingGuidancePrompt() // Fallback to old system
            
        val responseBuffer = StringBuilder()

        try {
            aiResponseGenerator(prompt, frames)
                .collect { (chunk, done) ->
                    // Accumulate chunks for complete response analysis
                    responseBuffer.append(chunk)
                    val fullResponse = responseBuffer.toString()

                    when {
                        fullResponse.contains("CROSSING COMPLETE", ignoreCase = true) -> {
                            Log.i(TAG, "Crossing complete signal received from AI")
                            navigationCueFlow.emit(
                                NavigationCue.InformationalAlert("Crossing complete.", true)
                            )
                            // End the session when crossing is complete
                            twoPhasePromptManager.endSession(sessionId)
                            onCrossingComplete()
                        }

                        done && fullResponse.contains("WAIT") && fullResponse.contains("SECONDS") -> {
                            // Only check for timer when response is complete to avoid partial matches
                            val timerMatch =
                                Regex("WAIT\\s+(\\d+)\\s+SECONDS", RegexOption.IGNORE_CASE).find(
                                    fullResponse
                                )
                            if (timerMatch != null) {
                                val seconds = timerMatch.groupValues[1].toIntOrNull()
                                if (seconds != null && seconds > 0) {
                                    Log.i(TAG, "Traffic light timer detected: $seconds seconds")
                                    navigationCueFlow.emit(
                                        NavigationCue.CriticalAlert(
                                            fullResponse.trim(),
                                            done
                                        )
                                    )

                                    // Notify about the timer detection
                                    onTimerDetected(seconds)
                                    return@collect
                                }
                            }
                            // Fallback to regular WAIT if timer extraction failed
                            navigationCueFlow.emit(
                                NavigationCue.CriticalAlert(
                                    fullResponse.trim(),
                                    done
                                )
                            )
                        }

                        chunk.isNotBlank() -> {
                            // Emit chunks as they come for real-time feedback, but don't parse timers yet
                            Log.d(TAG, "Emitting chunk NavigationCue: '$chunk', isDone: $done")
                            navigationCueFlow.emit(NavigationCue.CriticalAlert(chunk, done))
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error during enhanced crossing guidance", e)
            // Emit a safety-oriented fallback message
            navigationCueFlow.emit(
                NavigationCue.CriticalAlert("Continue with caution", true)
            )
        }
    }

    /**
     * Coordinates custom scene description or question answering alerts.
     *
     * This method handles user-initiated queries and scene descriptions,
     * using appropriate prompts and alert types for the response.
     *
     * @param prompt The custom prompt for AI processing
     * @param frames Current frames for AI analysis (optional for some use cases)
     * @param aiResponseGenerator Function that generates AI responses
     * @param isQuestion Whether this is a question (affects response handling)
     */
    suspend fun coordinateCustomAlert(
        prompt: String,
        frames: List<TimestampedFrame>?,
        aiResponseGenerator: suspend (String, List<TimestampedFrame>) -> Flow<Pair<String, Boolean>>,
        isQuestion: Boolean = false
    ) {
        Log.i(TAG, "Coordinating custom alert - Question: $isQuestion")

        try {
            val framesToUse = frames ?: emptyList()
            aiResponseGenerator(prompt, framesToUse)
                .collect { (chunk, done) ->
                    // Custom alerts are typically informational
                    val alert = NavigationCue.InformationalAlert(chunk, done)
                    navigationCueFlow.emit(alert)

                    if (done) {
                        Log.d(TAG, "Custom alert completed")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating custom alert", e)
            navigationCueFlow.emit(
                NavigationCue.InformationalAlert("Unable to process request", true)
            )
        }
    }

    /**
     * Coordinates navigation guidance with two-phase prompting system.
     *
     * @param sessionId Unique identifier for this navigation session
     * @param frames Timestamped frames containing navigation scene
     * @param aiResponseGenerator Function that generates AI responses
     */
    suspend fun coordinateNavigationGuidance(
        sessionId: String,
        frames: List<TimestampedFrame>,
        aiResponseGenerator: suspend (String, List<TimestampedFrame>) -> Flow<Pair<String, Boolean>>
    ) {
        Log.i(TAG, "Coordinating navigation guidance with session: $sessionId")

        // Get appropriate prompt using two-phase system
        val prompt = twoPhasePromptManager.getPrompt(sessionId)
            ?: promptGenerator.generateContextualNavigationPrompt("urban") // Fallback to old system

        try {
            aiResponseGenerator(prompt, frames)
                .collect { (chunk, done) ->
                    if (done && chunk.isNotBlank()) {
                        // Emit navigation guidance as ambient updates
                        navigationCueFlow.emit(
                            NavigationCue.AmbientUpdate(chunk.trim(), done)
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error during navigation guidance: ${e.message}")
            navigationCueFlow.emit(
                NavigationCue.InformationalAlert(
                    "Navigation guidance temporarily unavailable",
                    true
                )
            )
        }
    }

    /**
     * Coordinates object finding with two-phase prompting system.
     *
     * @param sessionId Unique identifier for this object finding session
     * @param target The object to search for
     * @param frames Timestamped frames containing scene
     * @param aiResponseGenerator Function that generates AI responses
     * @param onObjectFound Callback when object is found
     */
    suspend fun coordinateObjectFinding(
        sessionId: String,
        target: String,
        frames: List<TimestampedFrame>,
        aiResponseGenerator: suspend (String, List<TimestampedFrame>) -> Flow<Pair<String, Boolean>>,
        onObjectFound: () -> Unit = {}
    ) {
        Log.i(TAG, "Coordinating object finding for '$target' with session: $sessionId")

        // Get appropriate prompt using two-phase system
        val prompt = twoPhasePromptManager.getPrompt(sessionId)
            ?: promptGenerator.generateObjectDetectionPrompt(target) // Fallback to old system

        try {
            aiResponseGenerator(prompt, frames)
                .collect { (chunk, done) ->
                    if (done && chunk.isNotBlank()) {
                        val response = chunk.trim()

                        // Check if object was found
                        if (response.contains("FOUND IT", ignoreCase = true)) {
                            navigationCueFlow.emit(
                                NavigationCue.InformationalAlert("$target found! $response", true)
                            )
                            // End the session when object is found
                            twoPhasePromptManager.endSession(sessionId)
                            onObjectFound()
                        } else {
                            // Continue searching
                            navigationCueFlow.emit(
                                NavigationCue.AmbientUpdate(response, done)
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error during object finding: ${e.message}")
            navigationCueFlow.emit(
                NavigationCue.InformationalAlert("Object search temporarily unavailable", true)
            )
        }
    }
}
