package com.lumina.data.util

import android.graphics.Bitmap
import com.lumina.data.datasource.TimestampedFrame
import kotlin.math.abs

/**
 * Utility responsible for choosing the most suitable frames for Gemma vision.
 * It enforces:
 * 1. Motion context  – two frames roughly N frames apart.
 * 2. Sharpness       – avoids blurry frames by evaluating edge strength.
 */
object FrameSelector {

    private const val MOTION_WINDOW_MS = 800L   // look ~0.8s back for motion context
    private const val SEARCH_RADIUS = 3      // ± frames to look for sharp alternative
    private const val SHARPNESS_THRESHOLD = 12.0

    /**
     * Returns up to two frames providing motion context and acceptable sharpness.
     * Fallbacks to whatever is available if sharp alternatives cannot be found.
     */
    fun selectMotionFrames(buffer: List<TimestampedFrame>): List<TimestampedFrame> {
        if (buffer.isEmpty()) return emptyList()
        if (buffer.size == 1) return listOf(buffer.last())

        val latestIdx = buffer.lastIndex
        val latestFrame = buffer[latestIdx]

        // Find frame whose timestamp is at least MOTION_WINDOW_MS older.
        var candidateIdx = latestIdx
        for (i in buffer.indices.reversed()) {
            if (latestFrame.timestampMs - buffer[i].timestampMs >= MOTION_WINDOW_MS) {
                candidateIdx = i
                break
            }
        }

        val sharpLatest = findSharpNear(buffer, latestIdx)
        val sharpOlder =
            if (candidateIdx != latestIdx) findSharpNear(buffer, candidateIdx) else null

        return listOfNotNull(sharpOlder, sharpLatest).distinct()
    }

    private fun findSharpNear(buffer: List<TimestampedFrame>, index: Int): TimestampedFrame {
        // Exact index first
        if (isSharp(buffer[index].bitmap)) return buffer[index]

        for (offset in 1..SEARCH_RADIUS) {
            val left = index - offset
            val right = index + offset
            if (left >= 0 && isSharp(buffer[left].bitmap)) return buffer[left]
            if (right <= buffer.lastIndex && isSharp(buffer[right].bitmap)) return buffer[right]
        }
        // Give up – return original even if blurred
        return buffer[index]
    }

    /**
     * Very lightweight sharpness heuristic based on average gradient magnitude.
     */
    private fun isSharp(bmp: Bitmap): Boolean {
        val step = 8
        val w = bmp.width
        val h = bmp.height

        if (w < step * 2 || h < step * 2) return true // tiny image, assume sharp

        var sum = 0.0
        var count = 0

        for (y in step until h - step step step) {
            for (x in step until w - step step step) {
                val c = luminance(bmp.getPixel(x, y))
                val diff = abs(c - luminance(bmp.getPixel(x + step, y))) +
                        abs(c - luminance(bmp.getPixel(x, y + step)))
                sum += diff
                count++
            }
        }
        val score = sum / count
        return score >= SHARPNESS_THRESHOLD
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
} 