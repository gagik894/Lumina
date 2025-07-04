package com.lumina.data.repository

import android.graphics.BitmapFactory
import com.lumina.data.datasource.AiDataSource
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.SceneDescription
import com.lumina.domain.repository.LuminaRepository
import javax.inject.Inject

class LuminaRepositoryImpl @Inject constructor(
    private val aiDataSource: AiDataSource
) : LuminaRepository {

    override suspend fun describeScene(image: ImageInput, prompt: String): SceneDescription {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)

        val rawTextResponse = aiDataSource.generateResponse(prompt, bitmap)

        return SceneDescription(fullText = rawTextResponse)
    }
}