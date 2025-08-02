package com.lumina.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lumina.data.datasource.TimestampedFrame
import com.lumina.data.util.FrameSelector
import com.lumina.domain.model.ImageInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
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

    private val managerScope = CoroutineScope(Dispatchers.Default)
    private val _frameFlow = MutableSharedFlow<Pair<Bitmap, Long>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        managerScope.launch {
            _frameFlow.collect { (bitmap, timestamp) ->
                addFrame(bitmap, timestamp)
            }
        }
    }

    fun getFrameFlow(): Flow<Pair<Bitmap, Long>> = _frameFlow

    suspend fun processNewFrame(image: ImageInput) {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        val timestamp = System.currentTimeMillis()
        _frameFlow.tryEmit(Pair(bitmap, timestamp))
    }

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
            val oldFrame = frameBuffer.removeFirst()
            if (!oldFrame.bitmap.isRecycled) {
                oldFrame.bitmap.recycle()
            }
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
     * Retrieves the best quality (sharpest) recent frame for single-frame AI analysis.
     *
     * This method uses the FrameSelector to find the sharpest frame from recent frames,
     * ensuring optimal image quality for AI processing. This significantly improves
     * AI response quality compared to using potentially blurred latest frames.
     *
     * @return The best quality recent frame, or null if buffer is empty
     */
    @Synchronized
    fun getBestQualityFrame(): TimestampedFrame? {
        return FrameSelector.selectBestQualityFrame(frameBuffer.toList())
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
    fun getFrames(): List<TimestampedFrame> {
        return frameBuffer.toList()
    }

    /**
     * Clears all frames from the buffer and recycles their bitmaps.
     */
    @Synchronized
    fun clear() {
        frameBuffer.forEach {
            if (!it.bitmap.isRecycled) {
                it.bitmap.recycle()
            }
        }
        frameBuffer.clear()
    }
}
