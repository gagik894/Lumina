package com.lumina.data.repository.operations

import android.graphics.BitmapFactory
import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.PromptGenerator
import com.lumina.data.repository.TransientOperationCoordinator
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.NavigationCue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import javax.inject.Inject

class DescribeSceneOperation @Inject constructor(
    private val transientOperationCoordinator: TransientOperationCoordinator,
    private val promptGenerator: PromptGenerator,
    private val aiOperationHelper: AiOperationHelper,
) {
    fun execute(image: ImageInput, prompt: String): Flow<NavigationCue> {
        return callbackFlow {
            try {
                transientOperationCoordinator.executeTransientOperation("describe_scene") {
                    if (!isActive) return@executeTransientOperation

                    val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                    val contextualPrompt = promptGenerator.generateSceneDescriptionPrompt(prompt)

                    try {
                        aiOperationHelper.withAiOperation {
                            aiOperationHelper.generateResponse(contextualPrompt, bitmap)
                                .collect { (chunk, done) ->
                                    trySend(NavigationCue.InformationalAlert(chunk, done))
                                    if (done) close()
                                }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            } finally {
            }
            awaitClose { }
        }
    }
}
