package com.lumina.data.datasource

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaAiDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : AiDataSource {

    private val modelName = "gemma-3n-e2b-it-int4.task"

    private val llmInference: LlmInference by lazy {
        val modelPath = getAbsoluteModelPath(modelName)
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setMaxNumImages(1)
            .build()
        LlmInference.createFromOptions(context, options)
    }

    private fun createNewSession(): LlmInferenceSession {
        val optionsBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.8f)
            .setTopK(40)

        val graphOptions = GraphOptions.builder()
            .setEnableVisionModality(true)
            .build()
        optionsBuilder.setGraphOptions(graphOptions)

        return LlmInferenceSession.createFromOptions(llmInference, optionsBuilder.build())
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

    private fun getAbsoluteModelPath(modelName: String): String {
        val destinationFile = File(context.cacheDir, modelName)
        if (destinationFile.exists()) {
            return destinationFile.absolutePath
        }
        context.assets.open(modelName).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return destinationFile.absolutePath
    }
}