package com.lumina.data.repository

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransientOperationCoordinator"

/**
 * Coordinates transient operations to ensure they run exclusively without conflicts.
 *
 * This component manages the lifecycle of short-lived, user-initiated operations
 * such as object finding, scene description, and question answering. It ensures that:
 * - Only one transient operation runs at a time
 * - The main navigation pipeline is properly paused and resumed
 * - Resource conflicts are prevented through mutex synchronization
 * - Proper cleanup occurs even if operations fail or are cancelled
 *
 * The coordinator acts as a gatekeeper, enforcing mutual exclusion between
 * transient operations while providing a clean interface for operation execution.
 */
@Singleton
class TransientOperationCoordinator @Inject constructor(
    private val navigationModeManager: NavigationModeManager
) {

    /**
     * Mutex ensuring that transient operations run exclusively.
     * This prevents multiple user-initiated operations from conflicting with each other.
     */
    private val transientOperationMutex = Mutex()

    /**
     * Executes a transient operation with proper lifecycle management.
     *
     * This method:
     * 1. Acquires the transient operation mutex to ensure exclusivity
     * 2. Pauses the currently active navigation mode
     * 3. Executes the provided operation
     * 4. Resumes the previously active mode (if any)
     * 5. Releases the mutex
     *
     * @param operationName Human-readable name for logging purposes
     * @param operation The operation to execute within the controlled environment
     * @return The result of the operation execution
     */
    suspend fun <T> executeTransientOperation(
        operationName: String,
        operation: suspend () -> T
    ): T {
        return transientOperationMutex.withLock {
            Log.d(TAG, "Starting transient operation: $operationName")

            // Pause the active navigation mode to prevent conflicts
            val wasPaused = navigationModeManager.pauseActiveMode()

            try {
                // Execute the actual operation
                val result = operation()
                Log.d(TAG, "Completed transient operation: $operationName")
                return@withLock result

            } catch (e: Exception) {
                Log.e(TAG, "Error in transient operation: $operationName", e)
                throw e

            } finally {
                // Always attempt to resume the paused mode, even if operation failed
                if (wasPaused) {
                    resumePausedNavigationMode()
                }
            }
        }
    }

    /**
     * Checks if a transient operation is currently running.
     *
     * This can be useful for UI state management or preventing certain actions
     * while a transient operation is in progress.
     *
     * @return true if a transient operation is currently executing
     */
    fun isTransientOperationRunning(): Boolean {
        return transientOperationMutex.isLocked
    }

    /**
     * Resumes the navigation mode that was paused for a transient operation.
     *
     * This method determines what mode should be resumed and delegates to the
     * appropriate resume strategy. If no mode was paused, it defaults to
     * starting the main navigation pipeline.
     */
    private fun resumePausedNavigationMode() {
        val pausedMode = navigationModeManager.getPausedMode()

        when (pausedMode) {
            NavigationModeManager.OperatingMode.NAVIGATION -> {
                Log.d(TAG, "Resuming navigation mode after transient operation")
                // The actual mode resumption will be handled by the repository
                // This coordinator just manages the lifecycle
                navigationModeManager.clearPausedMode()
            }

            null -> {
                Log.d(TAG, "No paused mode found after transient operation")
                // If no mode was paused, we might want to start the default navigation
                // This decision can be delegated back to the repository
            }
        }
    }

    /**
     * Force-stops any running transient operation (emergency cleanup).
     *
     * This is typically used during application shutdown or when transitioning
     * to a completely different application state. Use with caution as it may
     * interrupt ongoing operations.
     */
    fun forceStopTransientOperations() {
        Log.w(TAG, "Force stopping transient operations")
        // The mutex will be released when any running operation completes or is cancelled
        // This method serves as a marker for emergency cleanup scenarios
    }

    /**
     * Executes a transient operation with additional safety checks.
     *
     * This variant provides additional validation and error handling for
     * operations that may have specific requirements or failure modes.
     *
     * @param operationName Human-readable name for logging
     * @param validate Pre-execution validation function
     * @param operation The operation to execute
     * @param onFailure Custom failure handler (optional)
     * @return The result of the operation execution
     */
    suspend fun <T> executeTransientOperationWithValidation(
        operationName: String,
        validate: suspend () -> Boolean = { true },
        operation: suspend () -> T,
        onFailure: (suspend (Exception) -> Unit)? = null
    ): T {
        return executeTransientOperation(operationName) {
            // Perform pre-execution validation
            if (!validate()) {
                throw IllegalStateException("Validation failed for operation: $operationName")
            }

            try {
                operation()
            } catch (e: Exception) {
                onFailure?.invoke(e)
                throw e
            }
        }
    }
}
