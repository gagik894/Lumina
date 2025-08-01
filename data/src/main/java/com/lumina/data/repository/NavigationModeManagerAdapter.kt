package com.lumina.data.repository

import android.util.Log
import kotlinx.coroutines.Job
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NavigationModeManager"

/**
 * Data layer adapter for NavigationModeService that adds coroutine job management.
 *
 * This adapter wraps the pure domain NavigationModeService with data layer concerns
 * like coroutine job lifecycle management and logging. It maintains the clean
 * separation between business logic (domain) and implementation details (data).
 */
@Singleton
class NavigationModeManager @Inject constructor(
    private val navigationModeService: com.lumina.domain.service.NavigationModeService
) {

    /** The coroutine job associated with the currently active long-running mode */
    private var activeJob: Job? = null

    /**
     * Checks if a specific mode is currently active.
     *
     * @param mode The mode to check
     * @return true if the specified mode is currently active
     */
    fun isActive(mode: OperatingMode): Boolean {
        val domainMode = mode.toDomainMode()
        return navigationModeService.isActive(domainMode) && activeJob?.isActive == true
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
            Log.d(TAG, "Cancelling active mode to start new mode: $mode")
            activeJob?.cancel()
        }

        val domainMode = mode.toDomainMode()
        navigationModeService.startMode(domainMode)
        activeJob = job

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
            Log.d(TAG, "Pausing active mode for transient operation")
            val wasPaused = navigationModeService.pauseActiveMode()
            activeJob?.cancel()
            activeJob = null
            wasPaused
        } else {
            Log.d(TAG, "No active mode to pause")
            navigationModeService.clearPausedMode()
            false
        }
    }

    /**
     * Gets the mode that was previously paused and should be resumed.
     *
     * @return The paused mode, or null if no mode was paused
     */
    fun getPausedMode(): OperatingMode? {
        return navigationModeService.getPausedMode()?.toDataMode()
    }

    /**
     * Clears the paused mode without resuming it.
     *
     * This is typically called after successfully resuming a mode or when
     * deciding not to resume the paused mode.
     */
    fun clearPausedMode() {
        navigationModeService.clearPausedMode()
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
        navigationModeService.stopAllModes()
    }

    /**
     * Gets the currently active mode.
     *
     * @return The active mode, or null if no mode is active
     */
    fun getActiveMode(): OperatingMode? {
        return if (activeJob?.isActive == true) {
            navigationModeService.getActiveMode()?.toDataMode()
        } else {
            null
        }
    }

    /**
     * Defines the exclusive, long-running operational modes of the repository.
     * Currently only NAVIGATION is supported as the primary continuous mode.
     */
    enum class OperatingMode {
        /**
         * The main navigation mode that provides continuous environmental awareness
         * through object detection and AI-powered scene analysis.
         */
        NAVIGATION;

        fun toDomainMode(): com.lumina.domain.service.NavigationModeService.OperatingMode {
            return when (this) {
                NAVIGATION -> com.lumina.domain.service.NavigationModeService.OperatingMode.NAVIGATION
            }
        }
    }
}

private fun com.lumina.domain.service.NavigationModeService.OperatingMode.toDataMode(): NavigationModeManager.OperatingMode {
    return when (this) {
        com.lumina.domain.service.NavigationModeService.OperatingMode.NAVIGATION -> NavigationModeManager.OperatingMode.NAVIGATION
    }
}
