package com.lumina.data.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TrafficLightTimerService"

/**
 * Service for managing traffic light countdown timers.
 *
 * This service provides timer functionality for crossing mode, allowing the AI
 * processing to be paused when a traffic light countdown is detected.
 */
@Singleton
class TrafficLightTimerService @Inject constructor() {

    private val _timerEvents = MutableSharedFlow<TimerEvent>()
    val timerEvents: Flow<TimerEvent> = _timerEvents.asSharedFlow()

    private var currentTimerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Starts a countdown timer for the specified duration.
     *
     * @param seconds Number of seconds to count down
     * @param onTick Callback invoked each second with remaining time
     * @param onComplete Callback invoked when timer completes
     */
    fun startTimer(
        seconds: Int,
        onTick: (Int) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        Log.i(TAG, "Starting traffic light timer for $seconds seconds")

        // Cancel any existing timer
        stopTimer()

        currentTimerJob = scope.launch {
            try {
                _timerEvents.emit(TimerEvent.Started(seconds))

                for (remaining in seconds downTo 1) {
                    onTick(remaining)
                    _timerEvents.emit(TimerEvent.Tick(remaining))

                    // Announce at specific intervals for user feedback
                    if (remaining <= 5 || remaining % 10 == 0) {
                        _timerEvents.emit(TimerEvent.Announcement(remaining))
                    }

                    delay(1000)
                }

                Log.i(TAG, "Traffic light timer completed")
                _timerEvents.emit(TimerEvent.Completed)
                onComplete()

            } catch (e: Exception) {
                Log.e(TAG, "Timer interrupted", e)
                _timerEvents.emit(TimerEvent.Cancelled)
            } finally {
                currentTimerJob = null
            }
        }
    }

    /**
     * Stops the current timer if one is running.
     */
    fun stopTimer() {
        currentTimerJob?.cancel()
        currentTimerJob = null

        scope.launch {
            _timerEvents.emit(TimerEvent.Cancelled)
        }
    }

    /**
     * Checks if a timer is currently running.
     */
    fun isTimerRunning(): Boolean = currentTimerJob?.isActive == true

    /**
     * Events emitted by the traffic light timer.
     */
    sealed class TimerEvent {
        data class Started(val seconds: Int) : TimerEvent()
        data class Tick(val remaining: Int) : TimerEvent()
        data class Announcement(val remaining: Int) : TimerEvent()
        object Completed : TimerEvent()
        object Cancelled : TimerEvent()
    }
}
