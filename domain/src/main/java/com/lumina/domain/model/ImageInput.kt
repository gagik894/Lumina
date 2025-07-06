package com.lumina.domain.model

/**
 * Represents an image input, typically used for operations like image processing or uploading.
 * @property bytes The raw byte array representing the image data.
 */
@JvmInline
value class ImageInput(val bytes: ByteArray)