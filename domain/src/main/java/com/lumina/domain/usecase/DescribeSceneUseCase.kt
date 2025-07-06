package com.lumina.domain.usecase

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.NavigationCue
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for describing a scene based on an image input.
 * This use case interacts with the LuminaRepository to get a description of the scene.
 *
 * @property repository The LuminaRepository instance used to fetch the scene description.
 */
class DescribeSceneUseCase @Inject constructor(
    private val repository: LuminaRepository
) {

    /**
     * Invokes the use case to describe a scene based on the provided image input.
     * It uses a predefined prompt to guide the description generation.
     *
     * @param image The image input to be processed.
     * @return A flow of NavigationCue containing the scene description
     */
    operator fun invoke(image: ImageInput): Flow<NavigationCue> {
        val prompt =
            "Describe this scene for a person who is blind. Focus on key objects, layout, and potential obstacles. Be clear and concise."
        return repository.describeScene(image, prompt)
    }
}