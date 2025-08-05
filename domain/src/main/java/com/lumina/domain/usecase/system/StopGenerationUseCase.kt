package com.lumina.domain.usecase.system

import com.lumina.domain.repository.LuminaRepository
import javax.inject.Inject

/**
 * Use case for stopping the generation process.
 *
 * This class encapsulates the logic for stopping the generation process
 * by delegating the call to the [LuminaRepository].
 *
 * @property repository The [LuminaRepository] instance used to interact with the data layer.
 */
class StopGenerationUseCase @Inject constructor(
    private val repository: LuminaRepository
) {

    suspend operator fun invoke() = repository.stopGeneration()
}