package com.lumina.data.repository

import android.graphics.Bitmap
import com.lumina.data.datasource.TimestampedFrame
import com.lumina.data.util.FrameSelector
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a circular buffer of timestamped camera frames optimized for motion analysis.
 *
 * This component maintains a rolling window of frames that allows the system to:
 * - Sample frames for motion detection with optimal temporal spacing
 * - Provide context for AI analysis by comparing current vs. previous states
 * - Efficiently manage memory by limiting buffer size
 *
 * The buffer is designed to hold enough frames (~35) to enable sampling of two frames
 * approximately 30 frames apart for optimal motion detection while maintaining
 * acceptable memory usage.
 */
@Singleton
class FrameBufferManager @Inject constructor() {

    companion object {
        private const val MAX_BUFFER_SIZE = 35
    }

    /**
     * Thread-safe circular buffer storing timestamped frames.
     * Uses ArrayDeque for efficient add/remove operations at both ends.
     */
    private val frameBuffer = ArrayDeque<TimestampedFrame>(MAX_BUFFER_SIZE)

    /**
     * Adds a new frame to the buffer, maintaining the maximum size constraint.
     *
     * If the buffer is at capacity, the oldest frame is removed before adding
     * the new one. This operation is thread-safe.
     *
     * @param bitmap The camera frame to add
     * @param timestamp The capture timestamp in milliseconds
     */
    @Synchronized
    fun addFrame(bitmap: Bitmap, timestamp: Long) {
        frameBuffer.add(TimestampedFrame(bitmap, timestamp))

        // Remove oldest frames if we exceed capacity
        if (frameBuffer.size > MAX_BUFFER_SIZE) {
            frameBuffer.removeFirst()
        }
    }

    /**
     * Retrieves the most recent frame from the buffer.
     *
     * @return The latest frame, or null if buffer is empty
     */
    @Synchronized
    fun getLatestFrame(): TimestampedFrame? {
        return frameBuffer.lastOrNull()
    }

    /**
     * Selects optimal frames for motion analysis using the FrameSelector utility.
     *
     * This method leverages the FrameSelector to intelligently choose frames that:
     * - Provide good motion context (temporal spacing)
     * - Have acceptable sharpness for AI analysis
     * - Fall back gracefully when optimal frames aren't available
     *
     * @return List of frames suitable for motion analysis (typically 1-2 frames)
     */
    @Synchronized
    fun getMotionAnalysisFrames(): List<TimestampedFrame> {
        return FrameSelector.selectMotionFrames(frameBuffer.toList())
    }

    /**
     * Returns a snapshot of all frames currently in the buffer.
     *
     * This creates a defensive copy to prevent external modification of the
     * internal buffer state.
     *
     * @return Immutable list of all buffered frames
     */
    @Synchronized
    fun getAllFrames(): List<TimestampedFrame> {
        return frameBuffer.toList()
    }

    /**
     * Clears all frames from the buffer.
     *
     * This is typically called when stopping navigation or resetting the system.
     * Memory cleanup for bitmaps should be handled by the caller if needed.
     */
    @Synchronized
    fun clear() {
        frameBuffer.clear()
    }

    /**
     * Returns the current number of frames in the buffer.
     *
     * @return Current buffer size
     */
    @Synchronized
    fun size(): Int {
        return frameBuffer.size
    }

    /**
     * Checks if the buffer is empty.
     *
     * @return true if no frames are currently buffered
     */
    @Synchronized
    fun isEmpty(): Boolean {
        return frameBuffer.isEmpty()
    }
}
