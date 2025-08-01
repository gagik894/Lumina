package com.lumina.data.repository

import android.util.Log
import com.lumina.domain.service.ThreatAssessmentService
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ThreatAssessmentManager"

/**
 * Data layer adapter for ThreatAssessmentService that adds logging and data layer concerns.
 *
 * This adapter wraps the pure domain ThreatAssessmentService with data layer concerns
 * like Android logging. It maintains the clean separation between business logic (domain)
 * and implementation details (data).
 */
@Singleton
class ThreatAssessmentManager @Inject constructor(
    private val threatAssessmentService: ThreatAssessmentService
) {

    /**
     * Assessment result indicating what type of alert (if any) should be triggered.
     */
    sealed class AssessmentResult {
        /** No alert needed at this time */
        data object NoAlert : AssessmentResult()

        /** Critical alert required for immediate threats */
        data class CriticalAlert(val detectedObjects: List<String>) : AssessmentResult()

        /** Informational alert for new important objects */
        data class InformationalAlert(val newObjects: List<String>) : AssessmentResult()

        /** Ambient update for general environmental awareness */
        data object AmbientUpdate : AssessmentResult()
    }

    /**
     * Analyzes the current object detection results and determines what type of alert is needed.
     *
     * This method delegates to the domain service and adds logging for debugging purposes.
     *
     * @param detectedObjects List of object labels detected in the current frame
     * @param currentTime Current system time in milliseconds
     * @return Assessment result indicating the appropriate response
     */
    fun assessThreatLevel(
        detectedObjects: List<String>,
        currentTime: Long
    ): AssessmentResult {
        val domainResult = threatAssessmentService.assessThreatLevel(detectedObjects, currentTime)

        // Convert domain result to data layer result and add logging
        return when (domainResult) {
            is ThreatAssessmentService.AssessmentResult.NoAlert -> {
                AssessmentResult.NoAlert
            }

            is ThreatAssessmentService.AssessmentResult.CriticalAlert -> {
                Log.d(TAG, "Critical threat detected: ${domainResult.detectedObjects}")
                AssessmentResult.CriticalAlert(domainResult.detectedObjects)
            }

            is ThreatAssessmentService.AssessmentResult.InformationalAlert -> {
                Log.d(TAG, "New important objects detected: ${domainResult.newObjects}")
                AssessmentResult.InformationalAlert(domainResult.newObjects)
            }

            is ThreatAssessmentService.AssessmentResult.AmbientUpdate -> {
                AssessmentResult.AmbientUpdate
            }
        }
    }

    /**
     * Resets the assessment state, clearing object tracking and cooldown timers.
     *
     * This is typically called when starting a new navigation session or
     * switching between different operating modes.
     */
    fun reset() {
        Log.d(TAG, "Resetting threat assessment state")
        threatAssessmentService.reset()
    }

    /**
     * Checks if a specific object type is classified as critical.
     *
     * @param objectLabel The object label to check
     * @return true if the object is considered a critical threat
     */
    fun isCriticalObject(objectLabel: String): Boolean {
        return threatAssessmentService.isCriticalObject(objectLabel)
    }

    /**
     * Checks if a specific object type is classified as important.
     *
     * @param objectLabel The object label to check
     * @return true if the object is considered important for navigation
     */
    fun isImportantObject(objectLabel: String): Boolean {
        return threatAssessmentService.isImportantObject(objectLabel)
    }
}
