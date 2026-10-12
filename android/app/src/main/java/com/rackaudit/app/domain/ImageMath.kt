package com.rackaudit.app.domain

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure arithmetic for photo downscaling, kept out of Android classes so it is unit-testable. */
object ImageMath {
    /** Largest power of two such that the decoded image is still >= [maxEdge] on its long edge. */
    fun inSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        require(width > 0 && height > 0 && maxEdge > 0) { "dimensions must be positive" }
        var sample = 1
        var longEdge = max(width, height)
        while (longEdge / 2 >= maxEdge) {
            sample *= 2
            longEdge /= 2
        }
        return sample
    }

    /** Scale so the long edge is at most [maxEdge]; never upscale. */
    fun scaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0) { "dimensions must be positive" }
        val longEdge = max(width, height)
        if (longEdge <= maxEdge) return width to height
        val ratio = maxEdge.toDouble() / longEdge
        return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
    }
}
