package com.lumina.data.repository

import android.util.Log
import kotlinx.coroutines.Job
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NavigationModeManager"

/**
 * Manages the lifecycle and state transitions of navigation modes in the Lumina system.
 *
 * This component enforces the single-responsibility principle for mode management,
 * ensuring that only one long-running navigation mode is active at any time while
 * properly handling pausing/resuming for transient operations.
 *
 * The system supports:
 * - One primary long-running mode: NAVIGATION (the "Director Pipeline")
 * - Transient operations that temporarily pause the active mode
 * - Proper cleanup and resource management during mode transitions
 */
@Singleton
class NavigationModeManager @Inject constructor() {

    /**
     * Defines the exclusive, long-running operational modes of the repository.
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

    /** The coroutine job associated with the currently active long-running mode */
    private var activeJob: Job? = null

    /**
     * Checks if a specific mode is currently active.
     *
     * @param mode The mode to check
     * @return true if the specified mode is currently active
     */
    fun isActive(mode: OperatingMode): Boolean {
        return activeMode == mode && activeJob?.isActive == true
    }

    /**
     * Checks if any mode is currently active.
     *
     * @return true if any long-running mode is currently active
     */
    fun isAnyModeActive(): Boolean {
        return activeJob?.isActive == true
    }

    /**
     * Starts a new mode with the associated coroutine job.
     *
     * If another mode is already active, it will be cancelled first.
     * This ensures mutual exclusion between different operating modes.
     *
     * @param mode The mode to start
     * @param job The coroutine job that implements the mode's logic
     */
    fun startMode(mode: OperatingMode, job: Job) {
        // Cancel any existing active mode
        if (activeJob?.isActive == true) {
            Log.d(TAG, "Cancelling active mode: $activeMode to start new mode: $mode")
            activeJob?.cancel()
        }

        activeMode = mode
        activeJob = job
        pausedMode = null // Clear any paused mode since we're starting fresh

        Log.d(TAG, "Started mode: $mode")
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
        return if (activeJob?.isActive == true) {
            Log.d(TAG, "Pausing active mode: $activeMode for transient operation")
            pausedMode = activeMode
            activeJob?.cancel()
            activeJob = null
            activeMode = null
            true
        } else {
            Log.d(TAG, "No active mode to pause")
            pausedMode = null
            false
        }
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
     * Stops all modes and cleans up resources.
     *
     * This cancels any active job and clears all state. This is typically
     * called when shutting down the navigation system.
     */
    fun stopAllModes() {
        Log.d(TAG, "Stopping all modes")
        activeJob?.cancel()
        activeJob = null
        activeMode = null
        pausedMode = null
    }

    /**
     * Gets the currently active mode.
     *
     * @return The active mode, or null if no mode is active
     */
    fun getActiveMode(): OperatingMode? {
        return if (activeJob?.isActive == true) activeMode else null
    }
}
