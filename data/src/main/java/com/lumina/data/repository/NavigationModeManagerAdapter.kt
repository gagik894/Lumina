package com.lumina.data.repository

import android.util.Log
import com.lumina.domain.service.NavigationModeService
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
    private val navigationModeService: NavigationModeService,
    private val frameBufferManager: FrameBufferManager,
    private val aiOperationHelper: AiOperationHelper
) {

    /** The coroutine job associated with the currently active long-running mode */
    private var activeJob: Job? = null

    /**
     * Checks if a specific mode is currently active.
     *
     * @param mode The mode to check
     * @return true if the specified mode is currently active
     */
    fun isActive(mode: NavigationModeService.OperatingMode): Boolean {
        return navigationModeService.isActive(mode) && activeJob?.isActive == true
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
    fun startMode(mode: NavigationModeService.OperatingMode, job: Job) {
        // Cancel any existing active mode without full cleanup
        if (activeJob?.isActive == true) {
            Log.d(TAG, "Cancelling active mode to start new mode: $mode")
            cancelActiveJobsOnly()
        }

        navigationModeService.startMode(mode)
        activeJob = job

        // When this mode's job completes, perform cleanup
        job.invokeOnCompletion { cause ->
            Log.d(TAG, "Mode $mode completed (cause: $cause), performing cleanup")
            frameBufferManager.clear()
            aiOperationHelper.reset()
        }

        Log.d(TAG, "Started mode: $mode")
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
        frameBufferManager.clear()
        aiOperationHelper.reset()
    }

    /**
     * Cancels active jobs without full resource cleanup.
     *
     * This is used when starting new operations - we want to cancel
     * running jobs but not reset AI session/frame buffer yet to allow
     * proper cleanup time.
     */
    fun cancelActiveModes() {
        Log.d(TAG, "Cancelling active modes without full cleanup")
        cancelActiveJobsOnly()
    }

    /**
     * Private helper to cancel jobs without cleanup.
     */
    private fun cancelActiveJobsOnly() {
        activeJob?.cancel()
        activeJob = null
        navigationModeService.stopAllModes()
    }

    /**
     * Gets the currently active mode.
     *
     * @return The active mode, or null if no mode is active
     */
    fun getActiveMode(): NavigationModeService.OperatingMode? {
        return if (activeJob?.isActive == true) {
            navigationModeService.getActiveMode()
        } else {
            null
        }
    }
}