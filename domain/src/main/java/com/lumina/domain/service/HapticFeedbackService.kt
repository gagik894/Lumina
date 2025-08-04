package com.lumina.domain.service

/**
 * Service for providing haptic feedback patterns to enhance accessibility.
 *
 * Provides non-auditory navigation cues through vibration patterns that complement
 * voice navigation for situations where audio cannot be used or as additional
 * confirmation for critical alerts.
 */
interface HapticFeedbackService {

    /**
     * Vibration patterns for different navigation scenarios
     */
    enum class HapticPattern {
        /** Short vibration for general notifications */
        NOTIFICATION,

        /** Double pulse for obstacles detected */
        OBSTACLE_ALERT,

        /** Triple pulse for critical warnings */
        CRITICAL_WARNING,

        /** Rhythmic pattern for crossing mode active */
        CROSSING_MODE,

        /** Success pattern for completed actions */
        SUCCESS,

        /** Direction indicators - left, right, forward */
        DIRECTION_LEFT,
        DIRECTION_RIGHT,
        DIRECTION_FORWARD
    }

    /**
     * Triggers a specific haptic pattern
     * @param pattern The haptic pattern to execute
     */
    fun triggerHaptic(pattern: HapticPattern)

    /**
     * Enables or disables haptic feedback
     * @param enabled Whether haptic feedback should be active
     */
    fun setHapticEnabled(enabled: Boolean)

    /**
     * Checks if haptic feedback is currently enabled
     * @return true if haptic feedback is enabled
     */
    fun isHapticEnabled(): Boolean

    /**
     * Sets the intensity of haptic feedback (0.0 to 1.0)
     * @param intensity Vibration intensity level
     */
    fun setHapticIntensity(intensity: Float)
}
