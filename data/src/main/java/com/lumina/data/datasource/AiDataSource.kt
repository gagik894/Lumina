package com.lumina.data.datasource

import android.graphics.Bitmap

interface AiDataSource {
    suspend fun generateResponse(prompt: String, image: Bitmap?): String
}