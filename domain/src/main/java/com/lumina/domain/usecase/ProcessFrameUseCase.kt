package com.lumina.domain.usecase

import com.lumina.domain.model.ImageInput
import com.lumina.domain.repository.LuminaRepository
import javax.inject.Inject

/**
 * Use case for processing new camera frames.
 */
class ProcessFrameUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    suspend operator fun invoke(imageInput: ImageInput) {
        repository.processNewFrame(imageInput)
    }
}
