package com.lumina.domain.model

/**
 * Represents the result of object detection with spatial information.
 *
 * @property label The detected object label (e.g., "person", "car", "dog")
 * @property confidence Detection confidence score (0.0 to 1.0)
 * @property boundingBox Spatial location and size of the detected object
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox
)

/**
 * Represents the spatial bounds of a detected object in the camera frame.
 *
 * @property left Left edge position (0.0 to 1.0 normalized coordinates)
 * @property top Top edge position (0.0 to 1.0 normalized coordinates)
 * @property right Right edge position (0.0 to 1.0 normalized coordinates)
 * @property bottom Bottom edge position (0.0 to 1.0 normalized coordinates)
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    /**
     * Calculates the height of the bounding box as a percentage of screen height.
     */
    val height: Float get() = bottom - top

    /**
     * Calculates the width of the bounding box as a percentage of screen width.
     */
    val width: Float get() = right - left

    /**
     * Calculates the area of the bounding box (0.0 to 1.0).
     */
    val area: Float get() = height * width
}
