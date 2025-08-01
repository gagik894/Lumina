package com.lumina.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain service for managing navigation mode lifecycle and state transitions.
 *
 * This service enforces the single-responsibility principle for mode management,
 * ensuring that only one long-running navigation mode is active at any time while
 * properly handling pausing/resuming for transient operations.
 *
 * The system supports:
 * - One primary long-running mode: NAVIGATION (the "Director Pipeline")
 * - Transient operations that temporarily pause the active mode
 * - Proper state management during mode transitions
 */
@Singleton
class NavigationModeService @Inject constructor() {

    /**
     * Defines the exclusive, long-running operational modes of the navigation system.
     * Currently only NAVIGATION is supported as the primary continuous mode.
     */
    enum class OperatingMode {
        /**
         * The main navigation mode that provides continuous environmental awareness
         * through object detection and AI-powered scene analysis.
         */
        NAVIGATION
    }

    /** The currently active long-running mode, null if no mode is running */
    private var activeMode: OperatingMode? = null

    /** The mode that was paused by a transient operation for later resumption */
    private var pausedMode: OperatingMode? = null

    /** Flag indicating if a mode is currently active */
    private var isModeActive: Boolean = false

    /**
     * Checks if a specific mode is currently active.
     *
     * @param mode The mode to check
     * @return true if the specified mode is currently active
     */
    fun isActive(mode: OperatingMode): Boolean {
        return activeMode == mode && isModeActive
    }

    /**
     * Checks if any mode is currently active.
     *
     * @return true if any long-running mode is currently active
     */
    fun isAnyModeActive(): Boolean {
        return isModeActive
    }

    /**
     * Starts a new mode.
     *
     * If another mode is already active, it will be marked as inactive first.
     * This ensures mutual exclusion between different operating modes.
     *
     * @param mode The mode to start
     */
    fun startMode(mode: OperatingMode) {
        // Mark any existing active mode as inactive
        if (isModeActive) {
            stopActiveMode()
        }

        activeMode = mode
        isModeActive = true
        pausedMode = null // Clear any paused mode since we're starting fresh
    }

    /**
     * Pauses the currently active long-running mode to allow a transient operation to run.
     *
     * The paused mode is recorded so it can be resumed later. If no mode is active,
     * this operation is a no-op.
     *
     * @return true if a mode was paused, false if no mode was active
     */
    fun pauseActiveMode(): Boolean {
        return if (isModeActive) {
            pausedMode = activeMode
            stopActiveMode()
            true
        } else {
            pausedMode = null
            false
        }
    }

    /**
     * Stops the currently active mode.
     */
    private fun stopActiveMode() {
        isModeActive = false
        activeMode = null
    }

    /**
     * Gets the mode that was previously paused and should be resumed.
     *
     * @return The paused mode, or null if no mode was paused
     */
    fun getPausedMode(): OperatingMode? {
        return pausedMode
    }

    /**
     * Clears the paused mode without resuming it.
     *
     * This is typically called after successfully resuming a mode or when
     * deciding not to resume the paused mode.
     */
    fun clearPausedMode() {
        pausedMode = null
    }

    /**
     * Stops all modes and cleans up state.
     *
     * This marks all modes as inactive and clears all state. This is typically
     * called when shutting down the navigation system.
     */
    fun stopAllModes() {
        isModeActive = false
        activeMode = null
        pausedMode = null
    }

    /**
     * Gets the currently active mode.
     *
     * @return The active mode, or null if no mode is active
     */
    fun getActiveMode(): OperatingMode? {
        return if (isModeActive) activeMode else null
    }
}
