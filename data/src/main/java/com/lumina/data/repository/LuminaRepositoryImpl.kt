package com.lumina.data.repository

import com.lumina.data.datasource.AiDataSource
import com.lumina.data.repository.operations.FindObjectOperation
import com.lumina.data.repository.operations.LuminaOperations
import com.lumina.data.repository.operations.NavigationOperations
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.InitializationState
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Implementation of [LuminaRepository] that coordinates between AI data sources and object detection
 * to provide intelligent navigation assistance for visually impaired users.
 *
 * This refactored implementation follows the single responsibility principle by delegating
 * specific concerns to specialized helper classes.
 *
 * The repository now serves as a lightweight orchestrator that maintains the public API
 * contract while delegating complex logic to specialized components. This design improves
 * testability, maintainability, and follows Google's recommended architecture patterns.
 *
 * @param gemmaDataSource The AI data source responsible for generating scene descriptions
 * @param frameBufferManager Manages the circular buffer of camera frames
 * @param navigationOperations Handles navigation mode lifecycle and transitions
 * @param findObjectOperation Handles the find object operation
 * @param luminaOperationsProvider Provider for [LuminaOperations] to break circular dependency
 */
@Singleton
class LuminaRepositoryImpl @Inject constructor(
    private val gemmaDataSource: AiDataSource,
    private val frameBufferManager: FrameBufferManager,
    private val navigationOperations: NavigationOperations,
    private val findObjectOperation: FindObjectOperation,
    private val luminaOperationsProvider: Provider<LuminaOperations>
) : LuminaRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    // Lazy initialization to break circular dependency
    private val luminaOperations: LuminaOperations by lazy { luminaOperationsProvider.get() }

    override val initializationState: StateFlow<InitializationState> =
        gemmaDataSource.initializationState

    override fun getNavigationCues(): Flow<NavigationCue> = luminaOperations.getNavigationCues()

    override fun startNavigationPipeline() = luminaOperations.startNavigationPipeline()

    override suspend fun processNewFrame(image: ImageInput) {
        frameBufferManager.processNewFrame(image)
    }

    override fun stopNavigation() = navigationOperations.stopNavigation()

    override fun describeScene(
        image: ImageInput,
        prompt: String
    ): Flow<NavigationCue> = luminaOperations.describeScene(image, prompt)

    override fun findObject(target: String): Flow<NavigationCue> =
        luminaOperations.findObject(target)

    override fun askQuestion(question: String): Flow<NavigationCue> =
        luminaOperations.askQuestion(question)

    override fun startCrossingMode(): Flow<NavigationCue> = luminaOperations.startCrossingMode()

    override fun identifyCurrency(image: ImageInput): Flow<NavigationCue> =
        luminaOperations.identifyCurrency(image)

    override fun readReceipt(image: ImageInput): Flow<NavigationCue> =
        luminaOperations.readReceipt(image)

    override fun readText(image: ImageInput): Flow<NavigationCue> = luminaOperations.readText(image)

    override fun identifyCurrencyMultiFrame(images: List<ImageInput>): Flow<NavigationCue> =
        luminaOperations.identifyCurrencyMultiFrame(images)

    override fun readReceiptMultiFrame(images: List<ImageInput>): Flow<NavigationCue> =
        luminaOperations.readReceiptMultiFrame(images)

    override fun readTextMultiFrame(images: List<ImageInput>): Flow<NavigationCue> =
        luminaOperations.readTextMultiFrame(images)

    companion object {
        val COCO_LABELS = setOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
            "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush"
        )
    }
}