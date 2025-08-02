package com.lumina.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.lumina.data.datasource.AiDataSource
import com.lumina.data.datasource.TimestampedFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiOperationHelper"

@Singleton
class AiOperationHelper @Inject constructor(
    private val gemmaDataSource: AiDataSource
) {
    private val aiMutex = Mutex()

    @Volatile
    private var isAiOperationInProgress = false

    suspend fun <T> withAiOperation(block: suspend () -> T): T {
        waitForAiOperationToComplete()
        return aiMutex.withLock {
            Log.d(TAG, "Starting AI operation...")
            isAiOperationInProgress = true
            try {
                block()
            } finally {
                Log.d(TAG, "AI operation completed")
                isAiOperationInProgress = false
            }
        }
    }

    private suspend fun waitForAiOperationToComplete() {
        if (isAiOperationInProgress) {
            Log.d(TAG, "Waiting for ongoing AI operation to complete...")
        }

        // Wait up to 10 seconds for ongoing AI operations to complete
        var attempts = 0
        while (isAiOperationInProgress && attempts < 100) {
            kotlinx.coroutines.delay(100) // Wait 100ms
            attempts++
        }

        if (isAiOperationInProgress) {
            Log.w(TAG, "AI operation still in progress after waiting 10 seconds, proceeding anyway")
            // Force reset the flag to prevent permanent blocking
            isAiOperationInProgress = false
        } else if (attempts > 0) {
            Log.d(TAG, "AI operation completed after waiting ${attempts * 100}ms")
        }
    }

    fun generateResponse(
        prompt: String,
        bitmap: Bitmap
    ): Flow<Pair<String, Boolean>> = flow {
        try {
            gemmaDataSource.generateResponse(
                prompt,
                listOf(TimestampedFrame(bitmap, System.currentTimeMillis()))
            ).collect { (chunk, isDone) ->
                emit(Pair(chunk, isDone))
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI operation failed", e)
            throw e
        }
    }

    fun generateResponse(
        prompt: String,
        frames: List<TimestampedFrame>
    ): Flow<Pair<String, Boolean>> = flow {
        try {
            gemmaDataSource.generateResponse(prompt, frames).collect { (chunk, isDone) ->
                emit(Pair(chunk, isDone))
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI operation failed", e)
            throw e
        }
    }

    fun reset() {
        gemmaDataSource.resetSession()
    }
}
