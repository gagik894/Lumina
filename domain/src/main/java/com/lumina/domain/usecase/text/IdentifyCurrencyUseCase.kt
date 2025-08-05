package com.lumina.domain.usecase.text

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for identifying currency (bills and coins) from image input.
 *
 * This use case provides specialized functionality for visually impaired users
 * to identify the denomination and details of money they are holding.
 *
 * @property repository The LuminaRepository instance used to process currency identification.
 */
class IdentifyCurrencyUseCase @Inject constructor(
    private val repository: LuminaRepository
) {

    /**
     * Invokes the use case to identify currency from the provided image input.
     *
     * The AI will analyze the image to determine:
     * - Currency denomination and value
     * - Country of origin
     * - Bill or coin characteristics (color, size, features)
     * - Any security features or markings
     *
     * @param image The image input containing currency to be identified.
     * @return A flow of NavigationCue containing currency identification details
     */
    operator fun invoke(image: ImageInput): Flow<NavigationCue> {
        return repository.identifyCurrency(image)
    }

    /**
     * Invokes the use case to identify currency from multiple image frames for better accuracy.
     *
     * The AI will analyze multiple frames of the same currency to determine:
     * - Currency denomination and value with improved accuracy
     * - Country of origin
     * - Bill or coin characteristics (color, size, features)
     * - Any security features or markings
     *
     * @param images List of image inputs containing frames of the same currency.
     * @return A flow of NavigationCue containing currency identification details
     */
    fun identifyMultiFrame(): Flow<NavigationCue> {
        return repository.identifyCurrencyMultiFrame()
    }
}
