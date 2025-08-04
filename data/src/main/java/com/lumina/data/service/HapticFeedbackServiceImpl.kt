package com.lumina.data.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.lumina.domain.service.HapticFeedbackService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HapticFeedbackService"

/**
 * Implementation of HapticFeedbackService using Android's Vibrator API.
 *
 * Provides tactile feedback for navigation assistance when audio cues
 * may not be sufficient or available.
 */
@Singleton
class HapticFeedbackServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HapticFeedbackService {

    private var isHapticEnabled = true
    private var hapticIntensity = 1.0f

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun triggerHaptic(pattern: HapticFeedbackService.HapticPattern) {
        if (!isHapticEnabled || vibrator?.hasVibrator() != true) {
            return
        }

        val vibrationPattern = getVibrationPattern(pattern)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(
                    vibrationPattern.timings,
                    vibrationPattern.amplitudes,
                    -1 // Don't repeat
                )
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(vibrationPattern.timings, -1)
            }

            Log.d(TAG, "Triggered haptic pattern: $pattern")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger haptic feedback", e)
        }
    }

    override fun setHapticEnabled(enabled: Boolean) {
        isHapticEnabled = enabled
        Log.d(TAG, "Haptic feedback ${if (enabled) "enabled" else "disabled"}")
    }

    override fun isHapticEnabled(): Boolean = isHapticEnabled

    override fun setHapticIntensity(intensity: Float) {
        hapticIntensity = intensity.coerceIn(0.0f, 1.0f)
        Log.d(TAG, "Haptic intensity set to: $hapticIntensity")
    }

    private fun getVibrationPattern(pattern: HapticFeedbackService.HapticPattern): VibrationPattern {
        return when (pattern) {
            HapticFeedbackService.HapticPattern.NOTIFICATION ->
                VibrationPattern(
                    timings = longArrayOf(0, 100),
                    amplitudes = intArrayOf(0, (255 * hapticIntensity).toInt())
                )

            HapticFeedbackService.HapticPattern.OBSTACLE_ALERT ->
                VibrationPattern(
                    timings = longArrayOf(0, 150, 100, 150),
                    amplitudes = intArrayOf(0, 200, 0, 200)
                )

            HapticFeedbackService.HapticPattern.CRITICAL_WARNING ->
                VibrationPattern(
                    timings = longArrayOf(0, 200, 100, 200, 100, 200),
                    amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                )

            HapticFeedbackService.HapticPattern.CROSSING_MODE ->
                VibrationPattern(
                    timings = longArrayOf(0, 100, 50, 100, 50, 100),
                    amplitudes = intArrayOf(0, 150, 0, 150, 0, 150)
                )

            HapticFeedbackService.HapticPattern.SUCCESS ->
                VibrationPattern(
                    timings = longArrayOf(0, 50, 50, 100),
                    amplitudes = intArrayOf(0, 180, 0, 220)
                )

            HapticFeedbackService.HapticPattern.DIRECTION_LEFT ->
                VibrationPattern(
                    timings = longArrayOf(0, 80, 40, 40),
                    amplitudes = intArrayOf(0, 180, 0, 100)
                )

            HapticFeedbackService.HapticPattern.DIRECTION_RIGHT ->
                VibrationPattern(
                    timings = longArrayOf(0, 40, 40, 80),
                    amplitudes = intArrayOf(0, 100, 0, 180)
                )

            HapticFeedbackService.HapticPattern.DIRECTION_FORWARD ->
                VibrationPattern(
                    timings = longArrayOf(0, 120),
                    amplitudes = intArrayOf(0, 200)
                )
        }.let { pattern ->
            // Apply intensity scaling
            VibrationPattern(
                timings = pattern.timings,
                amplitudes = pattern.amplitudes.map {
                    (it * hapticIntensity).toInt().coerceIn(0, 255)
                }.toIntArray()
            )
        }
    }

    private data class VibrationPattern(
        val timings: LongArray,
        val amplitudes: IntArray
    )
}
