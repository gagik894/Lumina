package com.lumina.domain.model

/**
 * Represents a scene description returned by the AI model.
 * @property partialResponse The partial response string from the AI model.
 * @property isDone Indicates whether the response is complete or still streaming.
 */
data class SceneDescription(
    val partialResponse: String,
    val isDone: Boolean
)