package com.lumina.domain.usecase

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for reading any text content from image input.
 *
 * This use case provides general text reading functionality for visually impaired users
 * to access any textual information in images, such as signs, labels, documents, or menus.
 *
 * @property repository The LuminaRepository instance used to process text reading.
 */
class ReadTextUseCase @Inject constructor(
    private val repository: LuminaRepository
) {

    /**
     * Invokes the use case to read all visible text from the provided image input.
     *
     * The AI will systematically extract and organize:
     * - Large headings and titles
     * - Main body text content
     * - Labels and captions
     * - Fine print and additional details
     * - Any other visible text elements
     *
     * @param image The image input containing text to be read.
     * @return A flow of NavigationCue containing systematically organized text content
     */
    operator fun invoke(image: ImageInput): Flow<NavigationCue> {
        return repository.readText(image)
    }
}
