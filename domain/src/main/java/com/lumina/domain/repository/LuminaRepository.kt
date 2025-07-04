package com.lumina.domain.repository

import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.SceneDescription

interface LuminaRepository {
    suspend fun describeScene(image: ImageInput, prompt: String): SceneDescription
}