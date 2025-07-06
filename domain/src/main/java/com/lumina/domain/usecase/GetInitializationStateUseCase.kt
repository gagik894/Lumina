package com.lumina.domain.usecase

import com.lumina.domain.model.InitializationState
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class GetInitializationStateUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(): StateFlow<InitializationState> = repository.initializationState
}

