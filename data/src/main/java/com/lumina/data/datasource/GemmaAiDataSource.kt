package com.lumina.data.datasource

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GemmaAiDataSource is a data source implementation for interacting with the Gemma AI model.
 * It provides functionality to generate scene descriptions based on a prompt and an image.
 *
 * @property context The application context used to initialize the LLM inference engine.
 */
@Singleton
class GemmaAiDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : AiDataSource {

    private val modelName = "gemma-3n-e2b-it-int4.task"

    private val llmInference: LlmInference by lazy {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelName)
            .setMaxTokens(1024)
            .build()
        LlmInference.createFromOptions(context, options)
    }

    private fun createNewSession(): LlmInferenceSession {
        val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.8f)
            .setTopK(40)
            .build()
        return LlmInferenceSession.createFromOptions(llmInference, options)
    }

    override fun generateSceneDescription(
        prompt: String,
        image: Bitmap
    ): Flow<Pair<String, Boolean>> = callbackFlow {
        val session = createNewSession()

        try {
            session.addQueryChunk(prompt)
            session.addImage(BitmapImageBuilder(image).build())

            session.generateResponseAsync { partialResult, done ->
                trySend(Pair(partialResult, done))
                if (done) {
                    channel.close()
                }
            }
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            session.close()
        }
    }

    override fun close() {
        try {
            llmInference.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}