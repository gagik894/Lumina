package com.lumina.domain.usecase

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.SceneDescription
import com.lumina.domain.repository.LuminaRepository
import javax.inject.Inject

class DescribeSceneUseCase @Inject constructor(private val repository: LuminaRepository) {
    suspend operator fun invoke(image: ImageInput): SceneDescription {
        val prompt =
            "Describe this scene for a person who is blind. Focus on key objects, layout, and potential obstacles. Be clear and concise."
        return repository.describeScene(image, prompt)
    }
}