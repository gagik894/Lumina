package com.lumina.data.repository.operations

import android.graphics.BitmapFactory
import com.lumina.data.repository.AiOperationHelper
import com.lumina.data.repository.FrameBufferManager
import com.lumina.data.repository.PromptGenerator
import com.lumina.data.repository.TransientOperationCoordinator
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.NavigationCue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import javax.inject.Inject

abstract class ImageBasedOperation(
    private val transientOperationCoordinator: TransientOperationCoordinator,
    protected val promptGenerator: PromptGenerator,
    private val aiOperationHelper: AiOperationHelper,
    private val frameBufferManager: FrameBufferManager? = null
) {
    protected abstract fun getOperationName(): String
    protected abstract fun getPrompt(image: ImageInput): String
    protected open fun getMultiFramePrompt(): String = ""
    protected open fun useHighResolution(): Boolean = false

    fun execute(image: ImageInput): Flow<NavigationCue> {
        return callbackFlow {
            try {
                transientOperationCoordinator.executeTransientOperation(getOperationName()) {
                    if (!isActive) return@executeTransientOperation

                    val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                    val prompt = getPrompt(image)

                    try {
                        aiOperationHelper.withAiOperation {
                            aiOperationHelper.generateResponse(prompt, bitmap, useHighResolution())
                                .collect { (chunk, done) ->
                                    trySend(NavigationCue.InformationalAlert(chunk, done))
                                    if (done) close()
                                }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to ${getOperationName()}. Please try again.",
                        true
                    )
                )
                close()
            }
            awaitClose { }
        }
    }

    fun executeMultiFrame(): Flow<NavigationCue> {
        return callbackFlow {
            try {
                transientOperationCoordinator.executeTransientOperation(getOperationName() + "_multi") {
                    if (!isActive) return@executeTransientOperation

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
                    if (qualityFrames.isEmpty()) {
                        trySend(
                            NavigationCue.InformationalAlert(
                                "No quality frames available. Please try again.",
                                true
                            )
                        )
                        close()
                        return@executeTransientOperation
                    }

                    try {
                        val prompt = getMultiFramePrompt()
                        aiOperationHelper.withAiOperation {
                            aiOperationHelper.generateResponse(
                                prompt,
                                qualityFrames,
                                useHighResolution()
                            )
                                .collect { (chunk, done) ->
                                    trySend(NavigationCue.InformationalAlert(chunk, done))
                                    if (done) close()
                                }
                        }
                    } finally {
                        // Frames are managed by frame buffer, no need to recycle manually
                    }
                }
            } catch (e: Exception) {
                trySend(
                    NavigationCue.InformationalAlert(
                        "Unable to ${getOperationName()}. Please try again.",
                        true
                    )
                )
                close()
            }
            awaitClose { }
        }
    }
}

class IdentifyCurrencyOperation @Inject constructor(
    transientOperationCoordinator: TransientOperationCoordinator,
    promptGenerator: PromptGenerator,
    aiOperationHelper: AiOperationHelper,
    frameBufferManager: FrameBufferManager
) : ImageBasedOperation(
    transientOperationCoordinator,
    promptGenerator,
    aiOperationHelper,
    frameBufferManager
) {
    override fun getOperationName() = "identify_currency"
    override fun getPrompt(image: ImageInput) =
        promptGenerator.generateCurrencyIdentificationPrompt()

    override fun getMultiFramePrompt() = promptGenerator.generateMultiFrameCurrencyPrompt()
    override fun useHighResolution() = true
}

class ReadReceiptOperation @Inject constructor(
    transientOperationCoordinator: TransientOperationCoordinator,
    promptGenerator: PromptGenerator,
    aiOperationHelper: AiOperationHelper,
    frameBufferManager: FrameBufferManager
) : ImageBasedOperation(
    transientOperationCoordinator,
    promptGenerator,
    aiOperationHelper,
    frameBufferManager
) {
    override fun getOperationName() = "read_receipt"
    override fun getPrompt(image: ImageInput) = promptGenerator.generateReceiptReadingPrompt()
    override fun getMultiFramePrompt() = promptGenerator.generateMultiFrameReceiptPrompt()
    override fun useHighResolution() = true
}

class ReadTextOperation @Inject constructor(
    transientOperationCoordinator: TransientOperationCoordinator,
    promptGenerator: PromptGenerator,
    aiOperationHelper: AiOperationHelper,
    frameBufferManager: FrameBufferManager
) : ImageBasedOperation(
    transientOperationCoordinator,
    promptGenerator,
    aiOperationHelper,
    frameBufferManager
) {
    override fun getOperationName() = "read_text"
    override fun getPrompt(image: ImageInput) = promptGenerator.generateTextReadingPrompt()
    override fun getMultiFramePrompt() = promptGenerator.generateMultiFrameTextPrompt()
    override fun useHighResolution() = true
}
