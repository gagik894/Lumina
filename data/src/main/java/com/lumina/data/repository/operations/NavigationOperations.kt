package com.lumina.data.repository.operations

import android.util.Log
import com.lumina.data.datasource.ObjectDetectorDataSource
import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.AlertCoordinator
import com.lumina.data.repository.FrameBufferManager
import com.lumina.data.repository.NavigationModeManager
import com.lumina.data.repository.ThreatAssessmentManager
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.NavigationModeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NavigationOperations"

@Singleton
class NavigationOperations @Inject constructor(
    private val objectDetectorDataSource: ObjectDetectorDataSource,
    private val frameBufferManager: FrameBufferManager,
    private val navigationModeManager: NavigationModeManager,
    private val threatAssessmentManager: ThreatAssessmentManager,
    private val alertCoordinator: AlertCoordinator,
    private val aiOperationHelper: AiOperationHelper
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    fun startDirectorPipeline() {
        if (navigationModeManager.isActive(NavigationModeService.OperatingMode.NAVIGATION)) {
            return // Already running
        }

        Log.d(TAG, "Starting Director Pipeline (NAVIGATION mode)")

        val navigationJob = repositoryScope.launch {
            objectDetectorDataSource.getDetectionStream(frameBufferManager.getFrameFlow())
                .collectLatest { detectedObjects ->
                    if (!isActive) return@collectLatest

                    Log.i(TAG, "Detected objects: $detectedObjects")
                    val currentTime = System.currentTimeMillis()

                    val assessment = threatAssessmentManager.assessThreatLevel(
                        detectedObjects,
                        currentTime
                    )

                    val motionFrames = frameBufferManager.getMotionAnalysisFrames()

                    aiOperationHelper.withAiOperation {
                        when (assessment) {
                            is ThreatAssessmentManager.AssessmentResult.CriticalAlert -> {
                                if (motionFrames.isNotEmpty()) {
                                    alertCoordinator.coordinateCriticalAlert(
                                        assessment.detectedObjects,
                                        motionFrames,
                                        aiOperationHelper::generateResponse
                                    )
                                }
                            }

                            is ThreatAssessmentManager.AssessmentResult.InformationalAlert -> {
                                if (motionFrames.isNotEmpty()) {
                                    alertCoordinator.coordinateInformationalAlert(
                                        assessment.newObjects,
                                        motionFrames,
                                        aiOperationHelper::generateResponse
                                    )
                                }
                            }

                            is ThreatAssessmentManager.AssessmentResult.AmbientUpdate -> {
                                if (motionFrames.isNotEmpty()) {
                                    alertCoordinator.coordinateAmbientUpdate(
                                        motionFrames,
                                        aiOperationHelper::generateResponse
                                    )
                                }
                            }

                            ThreatAssessmentManager.AssessmentResult.NoAlert -> {
                                // Continue monitoring without generating alerts
                            }
                        }
                    }
                }
        }

        navigationModeManager.startMode(
            NavigationModeService.OperatingMode.NAVIGATION,
            navigationJob
        )
    }

    fun stopNavigation() {
        Log.d(TAG, "🛑 Explicitly stopping navigation pipeline")
        navigationModeManager.stopAllModes()
        objectDetectorDataSource.setPaused(true)
        frameBufferManager.clear()
        threatAssessmentManager.reset()
    }

    fun getNavigationCues(): Flow<NavigationCue> {
        return alertCoordinator.getNavigationCueFlow()
    }
}
