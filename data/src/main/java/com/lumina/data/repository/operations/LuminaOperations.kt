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
    fun describeScene(): Flow<NavigationCue> =
        describeSceneOperation.executeMultiFrame()

    fun findObject(target: String): Flow<NavigationCue> = findObjectOperation.execute(target)
    fun askQuestion(question: String): Flow<NavigationCue> = askQuestionOperation.execute(question)
    fun startCrossingMode(): Flow<NavigationCue> = crossingModeOperation.execute()
    fun identifyCurrency(image: ImageInput): Flow<NavigationCue> =
        identifyCurrencyOperation.execute(image)

    fun readReceipt(image: ImageInput): Flow<NavigationCue> = readReceiptOperation.execute(image)
    fun readText(image: ImageInput): Flow<NavigationCue> = readTextOperation.execute(image)
    fun identifyCurrencyMultiFrame(): Flow<NavigationCue> =
        identifyCurrencyOperation.executeMultiFrame()

    fun readReceiptMultiFrame(): Flow<NavigationCue> =
        readReceiptOperation.executeMultiFrame()

    fun readTextMultiFrame(): Flow<NavigationCue> =
        readTextOperation.executeMultiFrame()

    fun startNavigation(): Flow<NavigationCue> = navigationOperations.execute()
}
