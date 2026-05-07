package com.armanmaurya.archiv.ml.docquad

import kotlin.math.min

/**
 * Represents a letterbox transformation for mapping a source rectangle to a destination rectangle
 * while maintaining the aspect ratio.
 */
class DocQuadLetterbox private constructor(
    val srcW: Int,
    val srcH: Int,
    val dstW: Int,
    val dstH: Int,
    val scale: Double,
    val offsetX: Double,
    val offsetY: Double
) {
    companion object {
        fun create(srcW: Int, srcH: Int, dstW: Int, dstH: Int): DocQuadLetterbox {
            require(srcW > 0 && srcH > 0) { "srcW/srcH must be > 0" }
            require(dstW > 0 && dstH > 0) { "dstW/dstH must be > 0" }

            val s = min(dstW.toDouble() / srcW, dstH.toDouble() / srcH)
            val newW = srcW.toDouble() * s
            val newH = srcH.toDouble() * s
            val ox = (dstW.toDouble() - newW) / 2.0
            val oy = (dstH.toDouble() - newH) / 2.0
            return DocQuadLetterbox(srcW, srcH, dstW, dstH, s, ox, oy)
        }

        fun create(srcW: Int, srcH: Int): DocQuadLetterbox {
            return create(srcW, srcH, 256, 256)
        }
    }

    fun forward(x: Double, y: Double): DoubleArray {
        return doubleArrayOf(x * scale + offsetX, y * scale + offsetY)
    }

    fun inverse(x: Double, y: Double): DoubleArray {
        return doubleArrayOf((x - offsetX) / scale, (y - offsetY) / scale)
    }
}
