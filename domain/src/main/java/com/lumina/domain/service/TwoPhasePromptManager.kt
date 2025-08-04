package com.lumina.domain.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for two-phase prompting system in continuous operations.
 *
 * Handles the coordination between initial context-setting prompts and
 * minimal follow-up prompts for token optimization in long-running operations.
 */
@Singleton
class TwoPhasePromptManager @Inject constructor(
    private val promptGenerationService: PromptGenerationService
) {
    companion object {
        private const val TAG = "TwoPhasePromptManager"
    }

    // Thread-safe session tracking
    private val sessionMutex = Mutex()
    private val activeSessions = mutableMapOf<String, SessionState>()

    /**
     * Represents the state of a continuous operation session.
     */
    private data class SessionState(
        val mode: PromptGenerationService.OperationMode,
        val target: String? = null,
        var isInitialized: Boolean = false
    )

    /**
     * Starts a new continuous operation session.
     *
     * @param sessionId Unique identifier for the session
     * @param mode The operation mode (crossing, navigation, object finding)
     * @param target Optional target for object finding mode
     */
    suspend fun startSession(
        sessionId: String,
        mode: PromptGenerationService.OperationMode,
        target: String? = null
    ) {
        sessionMutex.withLock {
            activeSessions[sessionId] = SessionState(mode, target, false)
            println("[$TAG] Started session: $sessionId with mode: $mode and target: $target")
        }
    }

    /**
     * Gets the appropriate prompt for the current session state.
     * Returns initial prompt for first call, follow-up prompts for subsequent calls.
     *
     * @param sessionId The session identifier
     * @return The appropriate prompt string, or null if session doesn't exist
     */
    suspend fun getPrompt(sessionId: String): String? {
        return sessionMutex.withLock {
            val sessionState = activeSessions[sessionId] ?: return@withLock null

            if (!sessionState.isInitialized) {
                // First call - return initial prompt and mark as initialized
                sessionState.isInitialized = true
                val prompt =
                    promptGenerationService.getInitialPrompt(sessionState.mode, sessionState.target)
                println("[$TAG] Session $sessionId: Returning INITIAL prompt (${prompt.length} chars)")
                prompt
            } else {
                // Subsequent calls - return follow-up prompt
                val prompt = promptGenerationService.getFollowUpPrompt(
                    sessionState.mode,
                    sessionState.target
                )
                println("[$TAG] Session $sessionId: Returning FOLLOW-UP prompt (${prompt.length} chars)")
                prompt
            }
        }
    }

    /**
     * Ends a continuous operation session and cleans up resources.
     *
     * @param sessionId The session identifier to end
     */
    suspend fun endSession(sessionId: String) {
        sessionMutex.withLock {
            val removed = activeSessions.remove(sessionId)
            if (removed != null) {
                println("[$TAG] Ended session: $sessionId")
            } else {
                println("[$TAG] Attempted to end non-existent session: $sessionId")
            }
        }
    }
}
