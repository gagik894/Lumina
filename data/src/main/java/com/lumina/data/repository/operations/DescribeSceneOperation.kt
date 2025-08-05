package com.lumina.data.repository.operations

import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.FrameBufferManager
import com.lumina.data.repository.TransientOperationCoordinator
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.service.PromptGenerationService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import javax.inject.Inject

class DescribeSceneOperation @Inject constructor(
    private val transientOperationCoordinator: TransientOperationCoordinator,
    private val promptGenerationService: PromptGenerationService,
    private val aiOperationHelper: AiOperationHelper,
    private val frameBufferManager: FrameBufferManager? = null
) {
    fun executeMultiFrame(): Flow<NavigationCue> {
        return callbackFlow {
            try {
                transientOperationCoordinator.executeTransientOperation("describe_scene") {
                    if (!isActive) return@executeTransientOperation
                    val prompt = promptGenerationService.generateDescribeScenePrompt()
                    if (frameBufferManager == null) {
                        trySend(
                            NavigationCue.InformationalAlert(
                                "Frame buffer manager is not available. Please try again.",
                                true
                            )
                        )
                        close()
                        return@executeTransientOperation
                    }

                    val qualityFrames = frameBufferManager.getMotionAnalysisFrames()

                    try {
                        aiOperationHelper.withAiOperation {
                            aiOperationHelper.generateResponse(prompt, qualityFrames)
                                .collect { (chunk, done) ->
                                    trySend(NavigationCue.InformationalAlert(chunk, done))
                                    if (done) close()
                                }
                        }
                    } finally {
                    }
                }
            } finally {
            }
            awaitClose { }
        }
    }
}
