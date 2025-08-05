package com.lumina.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain service for analyzing detected objects and determining threat levels and alerting strategies.
 *
 * This service implements the core decision-making logic for the Lumina navigation system,
 * determining when and what type of alerts should be triggered based on:
 * - Object threat classification (critical vs. important)
 * - Temporal cooldowns to prevent alert spam
 * - Change detection for new objects in the environment
 *
 * The assessment follows a priority hierarchy:
 * 1. Critical threats (immediate danger) - highest priority
 * 2. New important objects - medium priority
 * 3. Ambient updates - lowest priority
 */
@Singleton
class ThreatAssessmentService @Inject constructor() {

    /**
     * Objects that pose immediate physical danger and require urgent user attention.
     * These typically include fast-moving vehicles that could cause injury.
     */
    private val criticalObjects = setOf(
        "car", "truck", "bus", "bicycle", "motorcycle", "train"
    )

    /**
     * Objects that are significant for navigation but not immediately dangerous.
     * These warrant informational alerts when newly detected.
     */
    private val importantObjects = setOf(
        "person", "car", "dog", "cat", "bicycle", "motorcycle", "truck", "bus"
    )

    /** Minimum time between critical alerts to prevent overwhelming the user */
    private var lastCriticalAlertTime = 0L

    /** Minimum time between informational alerts to maintain alert quality */
    private var lastInformationalAlertTime = 0L

    /** Minimum time between ambient updates to avoid flooding the user with updates */
    private var lastAmbientUpdateTime = 0L

    /** Previously detected objects for change detection */
    private var lastSeenObjects = emptySet<String>()

    companion object {
        /** Cooldown period for critical alerts in milliseconds */
        private const val CRITICAL_ALERT_COOLDOWN_MS = 3000L // 3 seconds

        /** Cooldown period for informational alerts in milliseconds */
        private const val INFORMATIONAL_ALERT_COOLDOWN_MS = 5000L // 5 seconds

        /** Cooldown period for ambient updates in milliseconds */
        private const val AMBIENT_UPDATE_COOLDOWN_MS = 20000L // 20 seconds
    }

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
     * This method implements the core threat assessment logic, considering:
     * - Object threat classification
     * - Temporal cooldowns
     * - Change detection for new objects
     *
     * @param detectedObjects List of object labels detected in the current frame
     * @param currentTime Current system time in milliseconds
     * @return Assessment result indicating the appropriate response
     */
    fun assessThreatLevel(
        detectedObjects: List<String>,
        currentTime: Long
    ): AssessmentResult {
        val currentObjectLabels = detectedObjects.toSet()

        // Check for critical threats first (highest priority)
        val criticalObjectsPresent = detectedObjects.any { it in criticalObjects }
        if (criticalObjectsPresent && shouldTriggerCriticalAlert(currentTime)) {
            lastCriticalAlertTime = currentTime
            lastSeenObjects = currentObjectLabels
            return AssessmentResult.CriticalAlert(detectedObjects.filter { it in criticalObjects })
        }

        // Check for new important objects (medium priority)
        val newImportantObjects = findNewImportantObjects(currentObjectLabels)
        if (newImportantObjects.isNotEmpty() && shouldTriggerInformationalAlert(currentTime)) {
            lastInformationalAlertTime = currentTime
            lastSeenObjects = currentObjectLabels
            return AssessmentResult.InformationalAlert(newImportantObjects)
        }

        // Update object tracking even if no alert is triggered
        lastSeenObjects = currentObjectLabels

        if (shouldTriggerAmbientUpdate(currentTime)) {
            lastAmbientUpdateTime = currentTime
            return AssessmentResult.AmbientUpdate
        }

        return AssessmentResult.NoAlert
    }

    /**
     * Determines if a critical alert should be triggered based on cooldown periods.
     *
     * @param currentTime Current system time in milliseconds
     * @return true if enough time has passed since the last critical alert
     */
    private fun shouldTriggerCriticalAlert(currentTime: Long): Boolean {
        return (currentTime - lastCriticalAlertTime) > CRITICAL_ALERT_COOLDOWN_MS
    }

    /**
     * Determines if an informational alert should be triggered based on cooldown periods.
     *
     * @param currentTime Current system time in milliseconds
     * @return true if enough time has passed since the last informational alert
     */
    private fun shouldTriggerInformationalAlert(currentTime: Long): Boolean {
        return (currentTime - lastInformationalAlertTime) > INFORMATIONAL_ALERT_COOLDOWN_MS
    }


    private fun shouldTriggerAmbientUpdate(currentTime: Long): Boolean {
        return (currentTime - lastAmbientUpdateTime) > AMBIENT_UPDATE_COOLDOWN_MS
    }

    /**
     * Identifies newly appeared important objects by comparing current detections with previous state.
     *
     * @param currentObjects Set of currently detected object labels
     * @return List of important objects that weren't present in the last assessment
     */
    private fun findNewImportantObjects(currentObjects: Set<String>): List<String> {
        val newObjects = currentObjects - lastSeenObjects
        return newObjects.filter { it in importantObjects }
    }

    /**
     * Resets the assessment state, clearing object tracking and cooldown timers.
     *
     * This is typically called when starting a new navigation session or
     * switching between different operating modes.
     */
    fun reset() {
        lastSeenObjects = emptySet()
        lastCriticalAlertTime = 0L
        lastInformationalAlertTime = 0L
    }

    /**
     * Checks if a specific object type is classified as critical.
     *
     * @param objectLabel The object label to check
     * @return true if the object is considered a critical threat
     */
    fun isCriticalObject(objectLabel: String): Boolean {
        return objectLabel in criticalObjects
    }

    /**
     * Checks if a specific object type is classified as important.
     *
     * @param objectLabel The object label to check
     * @return true if the object is considered important for navigation
     */
    fun isImportantObject(objectLabel: String): Boolean {
        return objectLabel in importantObjects
    }
}
