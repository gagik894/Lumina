package com.lumina.domain.usecase.camera

import com.lumina.domain.model.ImageInput
import com.lumina.domain.repository.LuminaRepository
import javax.inject.Inject

/**
 * Use case responsible for processing a new camera frame.
 *
 * This use case takes an [com.lumina.domain.model.ImageInput] as a parameter, which represents the
 * captured image data, and delegates the processing to the [com.lumina.domain.repository.LuminaRepository].
 *
 * @param repository The [com.lumina.domain.repository.LuminaRepository] instance used to handle the frame processing logic.
 */
class ProcessFrameUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    suspend operator fun invoke(imageInput: ImageInput) {
        repository.processNewFrame(imageInput)
    }
}