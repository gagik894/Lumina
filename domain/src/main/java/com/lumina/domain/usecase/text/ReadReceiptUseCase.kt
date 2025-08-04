package com.lumina.domain.usecase.text

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for reading receipts and documents from image input.
 *
 * This use case provides specialized functionality for visually impaired users
 * to understand the content of receipts, bills, and other text documents.
 *
 * @property repository The LuminaRepository instance used to process receipt reading.
 */
class ReadReceiptUseCase @Inject constructor(
    private val repository: LuminaRepository
) {

    /**
     * Invokes the use case to read receipt content from the provided image input.
     *
     * The AI will analyze the document to extract:
     * - Business name and contact information
     * - Date and time of transaction
     * - Itemized list of purchases with prices
     * - Subtotal, tax, and total amounts
     * - Payment method and change information
     *
     * @param image The image input containing the receipt or document to be read.
     * @return A flow of NavigationCue containing organized receipt information
     */
    operator fun invoke(image: ImageInput): Flow<NavigationCue> {
        return repository.readReceipt(image)
    }
}
