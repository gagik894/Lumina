package com.lumina.domain.usecase.scene

import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for asking questions about the current scene.
 *
 * This use case allows the user to ask a question about the current scene and receive navigation cues
 * as a response. It interacts with the [LuminaRepository] to process the question and generate
 * the appropriate navigation cues.
 */
class AskQuestionUseCase @Inject constructor(
    private val repository: LuminaRepository
) {
    operator fun invoke(question: String): Flow<NavigationCue> = repository.askQuestion(question)
}
