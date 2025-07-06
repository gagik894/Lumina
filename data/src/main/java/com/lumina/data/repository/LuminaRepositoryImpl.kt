package com.lumina.data.repository

import android.graphics.BitmapFactory
import com.lumina.data.datasource.AiDataSource
import com.lumina.domain.model.ImageInput
import com.lumina.domain.model.SceneDescription
import com.lumina.domain.repository.LuminaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * LuminaRepositoryImpl is the implementation of the LuminaRepository interface.
 * It interacts with the AiDataSource to generate scene descriptions based on image inputs.
 *
 * @property aiDataSource The data source used to interact with the AI model for generating scene descriptions.
 */
class LuminaRepositoryImpl @Inject constructor(
    private val aiDataSource: AiDataSource
) : LuminaRepository {

    /**
     * Describes a scene based on the provided image and prompt.
     * This method converts the ImageInput to a Bitmap and calls the data source to generate the scene description.
     *
     * @param image The image input to be processed.
     * @param prompt The prompt to guide the description generation.
     * @return A flow of SceneDescription containing partial responses and completion status.
     */
    override fun describeScene(image: ImageInput, prompt: String): Flow<SceneDescription> {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)

        return aiDataSource.generateSceneDescription(prompt, bitmap)
            .map { (partialResult, isDone) ->
                SceneDescription(
                    partialResponse = partialResult,
                    isDone = isDone
                )
            }
    }
}