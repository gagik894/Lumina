package com.lumina.domain.usecase

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for asking questions about the current scene.
 */
class AskQuestionUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(question: String): Flow<NavigationCue> = repository.askQuestion(question)
}
