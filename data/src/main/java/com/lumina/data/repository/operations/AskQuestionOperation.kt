package com.lumina.data.repository.operations

import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.FrameBufferManager
import com.lumina.data.repository.PromptGenerator
import com.lumina.data.repository.TransientOperationCoordinator
import com.lumina.domain.model.NavigationCue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import javax.inject.Inject

class AskQuestionOperation @Inject constructor(
    private val transientOperationCoordinator: TransientOperationCoordinator,
    private val frameBufferManager: FrameBufferManager,
    private val promptGenerator: PromptGenerator,
    private val aiOperationHelper: AiOperationHelper,
) {
    fun execute(question: String): Flow<NavigationCue> {
        return callbackFlow {
            try {
                transientOperationCoordinator.executeTransientOperation("ask_question") {
                    if (!isActive) return@executeTransientOperation

                    val bestFrame = frameBufferManager.getBestQualityFrame()
                    if (bestFrame == null) {
                        trySend(
                            NavigationCue.InformationalAlert(
                                "Camera frame not available.",
                                true
                            )
                        )
                        close()
                        return@executeTransientOperation
                    }

                    val prompt = promptGenerator.generateQuestionAnsweringPrompt(question)
                    aiOperationHelper.withAiOperation {
                        aiOperationHelper.generateResponse(prompt, bestFrame.bitmap)
                            .collect { (chunk, done) ->
                                trySend(NavigationCue.InformationalAlert(chunk, done))
                                if (done) close()
                            }
                    }
                }
            } catch (e: Exception) {
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to answer question. Please try again.",
                        true
                    )
                )
                close()
            }
            awaitClose { }
        }
    }
}
