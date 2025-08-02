package com.lumina.data.repository.operations

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.NavigationCue
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LuminaOperations @Inject constructor(
    private val describeSceneOperation: DescribeSceneOperation,
    private val findObjectOperation: FindObjectOperation,
    private val askQuestionOperation: AskQuestionOperation,
    private val crossingModeOperation: CrossingModeOperation,
    private val identifyCurrencyOperation: IdentifyCurrencyOperation,
    private val readReceiptOperation: ReadReceiptOperation,
    private val readTextOperation: ReadTextOperation,
    private val navigationOperations: NavigationOperations
) {
    fun describeScene(image: ImageInput, prompt: String): Flow<NavigationCue> =
        describeSceneOperation.execute(image, prompt)

    fun findObject(target: String): Flow<NavigationCue> = findObjectOperation.execute(target)
    fun askQuestion(question: String): Flow<NavigationCue> = askQuestionOperation.execute(question)
    fun startCrossingMode(): Flow<NavigationCue> = crossingModeOperation.execute()
    fun identifyCurrency(image: ImageInput): Flow<NavigationCue> =
        identifyCurrencyOperation.execute(image)

    fun readReceipt(image: ImageInput): Flow<NavigationCue> = readReceiptOperation.execute(image)
    fun readText(image: ImageInput): Flow<NavigationCue> = readTextOperation.execute(image)
    fun identifyCurrencyMultiFrame(images: List<ImageInput>): Flow<NavigationCue> =
        identifyCurrencyOperation.executeMultiFrame(images)

    fun readReceiptMultiFrame(images: List<ImageInput>): Flow<NavigationCue> =
        readReceiptOperation.executeMultiFrame(images)

    fun readTextMultiFrame(images: List<ImageInput>): Flow<NavigationCue> =
        readTextOperation.executeMultiFrame(images)

    fun startNavigation(): Flow<NavigationCue> = navigationOperations.execute()
}
