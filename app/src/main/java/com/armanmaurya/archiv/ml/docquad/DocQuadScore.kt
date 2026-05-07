package com.armanmaurya.archiv.ml.docquad

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Small geometry utilities for deterministic quad scoring.
 */
object DocQuadScore {

    fun areaAbs(quad: Array<DoubleArray>): Double {
        requireQuad(quad)
        var s = 0.0
        for (i in 0..3) {
            val j = (i + 1) % 4
            s += quad[i][0] * quad[j][1] - quad[j][0] * quad[i][1]
        }
        return abs(0.5 * s)
    }

    fun perimeter(quad: Array<DoubleArray>): Double {
        requireQuad(quad)
        var p = 0.0
        for (i in 0..3) {
            val j = (i + 1) % 4
            p += dist(quad[i][0], quad[i][1], quad[j][0], quad[j][1])
        }
        return p
    }

    fun edgeLengthMin(quad: Array<DoubleArray>): Double {
        requireQuad(quad)
        var m = Double.POSITIVE_INFINITY
        for (i in 0..3) {
            val j = (i + 1) % 4
            val d = dist(quad[i][0], quad[i][1], quad[j][0], quad[j][1])
            if (d < m) m = d
        }
        return m
    }

    fun edgeLengthMax(quad: Array<DoubleArray>): Double {
        requireQuad(quad)
        var m = 0.0
        for (i in 0..3) {
            val j = (i + 1) % 4
            val d = dist(quad[i][0], quad[i][1], quad[j][0], quad[j][1])
            if (d > m) m = d
        }
        return m
    }

    fun aspectLike(quad: Array<DoubleArray>): Double {
        val min = edgeLengthMin(quad)
        val max = edgeLengthMax(quad)
        return max / min.coerceAtLeast(1e-9)
    }

    fun selfIntersects(quad: Array<DoubleArray>): Boolean {
        requireQuad(quad)
        return segmentsIntersect(
            quad[0][0], quad[0][1], quad[1][0], quad[1][1],
            quad[2][0], quad[2][1], quad[3][0], quad[3][1]
        ) || segmentsIntersect(
            quad[1][0], quad[1][1], quad[2][0], quad[2][1],
            quad[3][0], quad[3][1], quad[0][0], quad[0][1]
        )
    }

    fun isConvex(quad: Array<DoubleArray>): Boolean {
        requireQuad(quad)
        val eps = 1e-9
        var sign = 0
        for (i in 0..3) {
            val j = (i + 1) % 4
            val k = (i + 2) % 4
            val cross = orient(
                quad[i][0], quad[i][1],
                quad[j][0], quad[j][1],
                quad[k][0], quad[k][1]
            )
            if (abs(cross) <= eps) return false
            val s = if (cross > 0.0) 1 else -1
            if (sign == 0) {
                sign = s
            } else if (s != sign) {
                return false
            }
        }
        return true
    }

    fun oobSum(quad: Array<DoubleArray>, w: Double, h: Double, tolPx: Double): Double {
        requireQuad(quad)
        require(w > 0.0 && h > 0.0 && tolPx >= 0.0)

        val left = -tolPx
        val top = -tolPx
        val right = (w - 1.0) + tolPx
        val bottom = (h - 1.0) + tolPx

        var s = 0.0
        for (i in 0..3) {
            val x = quad[i][0]
            val y = quad[i][1]
            s += oob1d(x, left, right) + oob1d(y, top, bottom)
        }
        return s
    }

    fun oobMax(quad: Array<DoubleArray>, w: Double, h: Double, tolPx: Double): Double {
        requireQuad(quad)
        require(w > 0.0 && h > 0.0 && tolPx >= 0.0)

        val left = -tolPx
        val top = -tolPx
        val right = (w - 1.0) + tolPx
        val bottom = (h - 1.0) + tolPx

        var m = 0.0
        for (i in 0..3) {
            val x = quad[i][0]
            val y = quad[i][1]
            val v = oob1d(x, left, right) + oob1d(y, top, bottom)
            if (v > m) m = v
        }
        return m
    }

    private fun requireQuad(quad: Array<DoubleArray>) {
        require(quad.size == 4) { "quad must be double[4][2]" }
        for (i in 0..3) {
            require(quad[i].size == 2) { "quad must be double[4][2]" }
        }
    }

    private fun oob1d(v: Double, min: Double, max: Double): Double {
        if (v < min) return min - v
        if (v > max) return v - max
        return 0.0
    }

    private fun dist(ax: Double, ay: Double, bx: Double, by: Double): Double {
        return hypot(bx - ax, by - ay)
    }

    private fun orient(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
    }

    private fun onSegment(
        ax: Double, ay: Double, bx: Double, by: Double, px: Double, py: Double, eps: Double
    ): Boolean {
        if (abs(orient(ax, ay, bx, by, px, py)) > eps) return false
        return (minOf(ax, bx) - eps <= px && px <= maxOf(ax, bx) + eps)
                && (minOf(ay, by) - eps <= py && py <= maxOf(ay, by) + eps)
    }

    private fun segmentsIntersect(
        ax: Double, ay: Double, bx: Double, by: Double,
        cx: Double, cy: Double, dx: Double, dy: Double
    ): Boolean {
        val eps = 1e-9
        val o1 = orient(ax, ay, bx, by, cx, cy)
        val o2 = orient(ax, ay, bx, by, dx, dy)
        val o3 = orient(cx, cy, dx, dy, ax, ay)
        val o4 = orient(cx, cy, dx, dy, bx, by)

        val s1 = sign(o1, eps)
        val s2 = sign(o2, eps)
        val s3 = sign(o3, eps)
        val s4 = sign(o4, eps)

        if (s1 == 0 && onSegment(ax, ay, bx, by, cx, cy, eps)) return true
        if (s2 == 0 && onSegment(ax, ay, bx, by, dx, dy, eps)) return true
        if (s3 == 0 && onSegment(cx, cy, dx, dy, ax, ay, eps)) return true
        if (s4 == 0 && onSegment(cx, cy, dx, dy, bx, by, eps)) return true

        return (s1 * s2 < 0) && (s3 * s4 < 0)
    }

    private fun sign(v: Double, eps: Double): Int {
        if (v > eps) return 1
        if (v < -eps) return -1
        return 0
    }
}
