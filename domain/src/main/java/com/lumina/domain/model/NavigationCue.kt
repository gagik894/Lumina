package com.lumina.domain.model

/**
 * Represents different types of navigation cues based on urgency and context.
 */
sealed class NavigationCue {
    /**
     * Critical alert for immediate threats requiring instant user attention.
     * Used when dangerous objects are detected close or approaching rapidly.
     *
     * @property message Brief critical alert message (typically 3 words)
     * @property isDone Whether the streaming response is complete
     */
    data class CriticalAlert(
        val message: String,
        val isDone: Boolean = false
    ) : NavigationCue()

    /**
     * Informational alert for important new objects or changes in the environment.
     * Used when new significant objects appear in the scene.
     *
     * @property message Concise description of the new information
     * @property isDone Whether the streaming response is complete
     */
    data class InformationalAlert(
        val message: String,
        val isDone: Boolean = false
    ) : NavigationCue()

    /**
     * Ambient update providing general environmental context.
     * Used for periodic updates when no immediate threats or changes are detected.
     *
     * @property message Detailed description of the general surroundings
     * @property isDone Whether the streaming response is complete
     */
    data class AmbientUpdate(
        val message: String,
        val isDone: Boolean = false
    ) : NavigationCue()
}
