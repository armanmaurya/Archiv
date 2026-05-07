package com.armanmaurya.archiv.ml.docquad

import kotlin.math.*

/**
 * Minimal, deterministic postprocessor for DocQuadNet-256.
 */
object DocQuadPostprocessor {

    enum class ChosenSource {
        CORNERS,
        MASK,
    }

    enum class PeakMode {
        ARGMAX,
        REFINE_3X3,
    }

    data class Result(
        val corners256: Array<DoubleArray>,
        val cornersOriginal: Array<DoubleArray>?,
        val maskProbGt05Count: Int,
        val maskProbMean: Double,
        val quadFromMask256: Array<DoubleArray>,
        val quadFromMaskOriginal: Array<DoubleArray>?,
        val quadFromMaskUsedFallback: Boolean,
        val chosenQuad256: Array<DoubleArray>,
        val chosenQuadOriginal: Array<DoubleArray>?,
        val chosenSource: ChosenSource,
        val penaltyCorners: Double,
        val penaltyMask: Double,
        val suspiciousForProduct: Boolean,
        val suspiciousReason: String?
    )

    data class QuadFromMask(val quad256: Array<DoubleArray>, val usedFallback: Boolean)

    data class MaskStats(val maskProbGt05Count: Int, val maskProbMean: Double)

    private data class PathChoice(
        val chosenQuad256: Array<DoubleArray>,
        val chosenSource: ChosenSource,
        val penaltyCorners: Double,
        val penaltyMask: Double
    )

    fun postprocess(
        cornerHeatmaps: Array<Array<Array<FloatArray>>>,
        maskLogits: Array<Array<Array<FloatArray>>>,
        lb: DocQuadLetterbox? = null,
        peakMode: PeakMode = PeakMode.ARGMAX
    ): Result {
        val corners256 = corners64ToCorners256(cornerHeatmaps, peakMode)
        val ms = computeMaskStats(maskLogits)
        val qm = quadFromMask256(maskLogits, corners256)

        val pc = choosePath(corners256, qm.quad256, qm.usedFallback, maskLogits)
        val chosenQuad256 = pc.chosenQuad256
        val chosenSource = pc.chosenSource
        val penaltyCorners = pc.penaltyCorners
        val penaltyMask = pc.penaltyMask

        var cornersOriginal: Array<DoubleArray>? = null
        var quadOriginal: Array<DoubleArray>? = null
        var chosenOriginal: Array<DoubleArray>? = null

        if (lb != null) {
            cornersOriginal = mapCorners256ToOriginal(corners256, lb)
            quadOriginal = mapCorners256ToOriginal(qm.quad256, lb)
            chosenOriginal = if (chosenSource == ChosenSource.MASK) quadOriginal else cornersOriginal
        }

        val suspiciousReason = evaluateSuspicious(cornerHeatmaps, ms, qm, pc)
        val suspiciousForProduct = suspiciousReason != null

        return Result(
            corners256, cornersOriginal, ms.maskProbGt05Count, ms.maskProbMean,
            qm.quad256, quadOriginal, qm.usedFallback,
            chosenQuad256, chosenOriginal, chosenSource,
            penaltyCorners, penaltyMask, suspiciousForProduct, suspiciousReason
        )
    }

    private const val PEAK_SIGMA_THRESHOLD = 5.0
    private const val MASK_DIFFUSE_MEAN_THRESHOLD = 0.45
    private const val MASK_DIFFUSE_MIN_AREA = 100
    private const val GEOMETRY_IMPLAUSIBLE_THRESHOLD = 1e4
    private const val HARD_PENALTY_THRESHOLD = 1e5
    private const val AGREEMENT_MAX_CORNER_DIST = 32.0
    private const val MASK_SCORE_MARGIN = 50.0

    private fun evaluateSuspicious(
        cornerHeatmaps: Array<Array<Array<FloatArray>>>,
        ms: MaskStats,
        qm: QuadFromMask,
        pc: PathChoice
    ): String? {
        if (hasLowPeakMargin(cornerHeatmaps)) return "LOW_PEAK_MARGIN"

        if (ms.maskProbMean > MASK_DIFFUSE_MEAN_THRESHOLD && ms.maskProbGt05Count < MASK_DIFFUSE_MIN_AREA) {
            return "MASK_DIFFUSE"
        }

        if (qm.usedFallback && pc.penaltyCorners > GEOMETRY_IMPLAUSIBLE_THRESHOLD) {
            return "MASK_FALLBACK_AND_PCORNER"
        }

        if (!qm.usedFallback) {
            val maxDist = maxCornerDistance(pc.chosenQuad256, qm.quad256)
            if (pc.chosenSource == ChosenSource.CORNERS && maxDist > 64.0) {
                return "DISAGREE_64PX"
            }
        }

        val chosenPenalty = if (pc.chosenSource == ChosenSource.MASK) pc.penaltyMask else pc.penaltyCorners
        if (chosenPenalty >= GEOMETRY_IMPLAUSIBLE_THRESHOLD) return "GEOMETRY_IMPLAUSIBLE"

        return null
    }

    private fun hasLowPeakMargin(cornerHeatmaps: Array<Array<Array<FloatArray>>>): Boolean {
        for (c in 0..3) {
            val hm = cornerHeatmaps[0][c]
            var best = -Float.MAX_VALUE
            var sum = 0.0
            var n = 0
            for (y in 0..63) {
                for (x in 0..63) {
                    val v = hm[y][x]
                    sum += v
                    n++
                    if (v > best) best = v
                }
            }
            val mean = sum / n.coerceAtLeast(1)
            var sumSq = 0.0
            for (y in 0..63) {
                for (x in 0..63) {
                    val d = hm[y][x] - mean
                    sumSq += d * d
                }
            }
            val std = sqrt(sumSq / n.coerceAtLeast(1))
            if (std > 1e-6 && (best - mean) / std < PEAK_SIGMA_THRESHOLD) return true
        }
        return false
    }

    private fun choosePath(
        quadCorners256: Array<DoubleArray>,
        quadFromMask256: Array<DoubleArray>,
        quadFromMaskUsedFallback: Boolean,
        maskLogits: Array<Array<Array<FloatArray>>>
    ): PathChoice {
        val pAGeom = quadPenaltyGeometry(quadCorners256)
        val pA = pAGeom + maskDisagreementPenaltyForCorners(quadCorners256, maskLogits)

        if (quadFromMaskUsedFallback) {
            return PathChoice(quadCorners256, ChosenSource.CORNERS, pA, Double.POSITIVE_INFINITY)
        }

        val pB = quadPenaltyGeometry(quadFromMask256)

        if (pAGeom >= HARD_PENALTY_THRESHOLD && pB < HARD_PENALTY_THRESHOLD) {
            return PathChoice(quadFromMask256, ChosenSource.MASK, pA, pB)
        }
        if (pB >= HARD_PENALTY_THRESHOLD) {
            return PathChoice(quadCorners256, ChosenSource.CORNERS, pA, pB)
        }

        val maxCornerDist = maxCornerDistance(quadCorners256, quadFromMask256)
        if (maxCornerDist > AGREEMENT_MAX_CORNER_DIST) {
            return PathChoice(quadCorners256, ChosenSource.CORNERS, pA, pB)
        }

        if (pB < pAGeom - MASK_SCORE_MARGIN) {
            return PathChoice(quadFromMask256, ChosenSource.MASK, pA, pB)
        }

        return PathChoice(quadCorners256, ChosenSource.CORNERS, pA, pB)
    }

    private fun maxCornerDistance(quad1: Array<DoubleArray>, quad2: Array<DoubleArray>): Double {
        var maxD = 0.0
        for (i in 0..3) {
            val dx = quad1[i][0] - quad2[i][0]
            val dy = quad1[i][1] - quad2[i][1]
            val d = sqrt(dx * dx + dy * dy)
            if (d > maxD) maxD = d
        }
        return maxD
    }

    private fun quadPenaltyGeometry(quad256: Array<DoubleArray>): Double {
        var penalty = 0.0
        val w = 256.0
        val h = 256.0
        val tol = 2.0
        val hard = 16.0
        val kSoft = 10.0
        val kHard = 1000.0

        val oobSum = DocQuadScore.oobSum(quad256, w, h, tol)
        if (oobSum > 0.0) penalty += oobSum * kSoft
        val oobMax = DocQuadScore.oobMax(quad256, w, h, tol)
        if (oobMax > hard) penalty += 1e5 + (oobMax - hard) * kHard

        if (DocQuadScore.selfIntersects(quad256)) penalty += 1e6
        if (!DocQuadScore.isConvex(quad256)) penalty += 1e6
        if (DocQuadScore.areaAbs(quad256) <= 1.0) penalty += 1e6

        val edgeMin = DocQuadScore.edgeLengthMin(quad256)
        val edgeMax = DocQuadScore.edgeLengthMax(quad256)

        if (edgeMin < 8.0) penalty += (8.0 - edgeMin) * 1000.0
        val r = edgeMax / edgeMin.coerceAtLeast(1e-9)
        if (r > 25.0) penalty += (r - 25.0) * 100.0

        return penalty
    }

    private fun maskDisagreementPenaltyForCorners(
        quadCorners256: Array<DoubleArray>,
        maskLogits: Array<Array<Array<FloatArray>>>
    ): Double {
        val quad64 = Array(4) { i ->
            doubleArrayOf(quadCorners256[i][0] / 4.0, quadCorners256[i][1] / 4.0)
        }
        val grid = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56)
        var disagree = 0
        val m = maskLogits[0][0]

        for (gy in grid) {
            for (gx in grid) {
                val px = gx + 0.5
                val py = gy + 0.5
                val inQuad = pointInPolyInclusive(quad64, px, py)
                val inMask = sigmoid(m[gy][gx].toDouble()) > 0.5
                if (inQuad != inMask) disagree++
            }
        }
        return disagree.toDouble() * 10.0
    }

    private fun pointInPolyInclusive(poly: Array<DoubleArray>, px: Double, py: Double): Boolean {
        for (i in 0..3) {
            val j = (i + 1) % 4
            if (onSegment(poly[i][0], poly[i][1], poly[j][0], poly[j][1], px, py, 1e-9)) return true
        }
        var inside = false
        var j = 3
        for (i in 0..3) {
            if (((poly[i][1] > py) != (poly[j][1] > py)) &&
                (px < (poly[j][0] - poly[i][0]) * (py - poly[i][1]) / (poly[j][1] - poly[i][1]) + poly[i][0])
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun onSegment(ax: Double, ay: Double, bx: Double, by: Double, px: Double, py: Double, eps: Double): Boolean {
        if (abs((bx - ax) * (py - ay) - (by - ay) * (px - ax)) > eps) return false
        return (minOf(ax, bx) - eps <= px && px <= maxOf(ax, bx) + eps) &&
                (minOf(ay, by) - eps <= py && py <= maxOf(ay, by) + eps)
    }

    fun quadFromMask256(maskLogits: Array<Array<Array<FloatArray>>>, fallbackCorners256: Array<DoubleArray>): QuadFromMask {
        val m = maskLogits[0][0]
        var maskCount = 0
        var sumX = 0.0
        var sumY = 0.0
        for (y in 0..63) {
            for (x in 0..63) {
                if (sigmoid(m[y][x].toDouble()) > 0.5) {
                    maskCount++
                    sumX += x + 0.5
                    sumY += y + 0.5
                }
            }
        }
        if (maskCount == 0) return QuadFromMask(fallbackCorners256, true)
        val cx = sumX / maskCount
        val cy = sumY / maskCount

        var sxx = 0.0; var sxy = 0.0; var syy = 0.0
        for (y in 0..63) {
            for (x in 0..63) {
                if (sigmoid(m[y][x].toDouble()) > 0.5) {
                    val dx = (x + 0.5) - cx
                    val dy = (y + 0.5) - cy
                    sxx += dx * dx
                    sxy += dx * dy
                    syy += dy * dy
                }
            }
        }
        sxx /= maskCount; sxy /= maskCount; syy /= maskCount
        val trace = sxx + syy
        if (trace < 1e-12) return QuadFromMask(fallbackCorners256, true)
        val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(0.0, trace * trace / 4.0 - det))
        val lambda1 = trace / 2.0 + disc

        val v1x: Double; val v1y: Double
        if (abs(sxy) > 1e-12) {
            v1x = lambda1 - syy
            v1y = sxy
        } else {
            if (sxx >= syy) { v1x = 1.0; v1y = 0.0 } else { v1x = 0.0; v1y = 1.0 }
        }
        val n = hypot(v1x, v1y)
        if (n < 1e-12) return QuadFromMask(fallbackCorners256, true)
        val nv1x = v1x / n; val nv1y = v1y / n
        val v2x = -nv1y; val v2y = nv1x

        var uMin = Double.POSITIVE_INFINITY; var uMax = Double.NEGATIVE_INFINITY
        var vMin = Double.POSITIVE_INFINITY; var vMax = Double.NEGATIVE_INFINITY
        for (y in 0..63) {
            for (x in 0..63) {
                if (sigmoid(m[y][x].toDouble()) > 0.5) {
                    val dx = (x + 0.5) - cx
                    val dy = (y + 0.5) - cy
                    val u = dx * nv1x + dy * nv1y
                    val v = dx * v2x + dy * v2y
                    uMin = min(uMin, u); uMax = max(uMax, u)
                    vMin = min(vMin, v); vMax = max(vMax, v)
                }
            }
        }
        if (uMax - uMin < 1e-12 || vMax - vMin < 1e-12) return QuadFromMask(fallbackCorners256, true)

        var quad64 = Array(4) { DoubleArray(2) }
        quad64[0][0] = cx + uMax * nv1x + vMax * v2x; quad64[0][1] = cy + uMax * nv1y + vMax * v2y
        quad64[1][0] = cx + uMin * nv1x + vMax * v2x; quad64[1][1] = cy + uMin * nv1y + vMax * v2y
        quad64[2][0] = cx + uMin * nv1x + vMin * v2x; quad64[2][1] = cy + uMin * nv1y + vMin * v2y
        quad64[3][0] = cx + uMax * nv1x + vMin * v2x; quad64[3][1] = cy + uMax * nv1y + vMin * v2y

        quad64 = canonicalizeQuadOrderV1(quad64)
        val quad256 = Array(4) { i -> doubleArrayOf(quad64[i][0] * 4.0, quad64[i][1] * 4.0) }
        return QuadFromMask(quad256, false)
    }

    private fun canonicalizeQuadOrderV1(pts: Array<DoubleArray>): Array<DoubleArray> {
        var cx = 0.0; var cy = 0.0
        for (i in 0..3) { cx += pts[i][0]; cy += pts[i][1] }
        cx /= 4.0; cy /= 4.0
        val ordered = arrayOf(0, 1, 2, 3)
        ordered.sortWith { a, b ->
            val angA = atan2(pts[a][1] - cy, pts[a][0] - cx)
            val angB = atan2(pts[b][1] - cy, pts[b][0] - cx)
            if (angA < angB) -1 else if (angA > angB) 1 else a.compareTo(b)
        }
        var tlPos = 0
        var bestSum = Double.POSITIVE_INFINITY
        for (k in 0..3) {
            val s = pts[ordered[k]][0] + pts[ordered[k]][1]
            if (s < bestSum) { bestSum = s; tlPos = k }
        }
        return Array(4) { i ->
            val src = ordered[(tlPos + i) % 4]
            doubleArrayOf(pts[src][0], pts[src][1])
        }
    }

    private fun corners64ToCorners256(cornerHeatmaps: Array<Array<Array<FloatArray>>>, peakMode: PeakMode): Array<DoubleArray> {
        return when (peakMode) {
            PeakMode.ARGMAX -> argmaxCorners64ToCorners256(cornerHeatmaps)
            PeakMode.REFINE_3X3 -> refineCorners64ToCorners256_3x3(cornerHeatmaps)
        }
    }

    private fun argmaxCorners64ToCorners256(cornerHeatmaps: Array<Array<Array<FloatArray>>>): Array<DoubleArray> {
        val corners256 = Array(4) { DoubleArray(2) }
        for (c in 0..3) {
            var best = -Float.MAX_VALUE; var bestX = 0; var bestY = 0
            val hm = cornerHeatmaps[0][c]
            for (y in 0..63) {
                for (x in 0..63) {
                    if (hm[y][x] > best) { best = hm[y][x]; bestX = x; bestY = y }
                }
            }
            corners256[c][0] = (bestX + 0.5) * 4.0; corners256[c][1] = (bestY + 0.5) * 4.0
        }
        return corners256
    }

    private fun refineCorners64ToCorners256_3x3(cornerHeatmaps: Array<Array<Array<FloatArray>>>): Array<DoubleArray> {
        val corners256 = Array(4) { DoubleArray(2) }
        for (c in 0..3) {
            var best = -Float.MAX_VALUE; var bestX = 0; var bestY = 0
            val hm = cornerHeatmaps[0][c]
            for (y in 0..63) {
                for (x in 0..63) {
                    if (hm[y][x] > best) { best = hm[y][x]; bestX = x; bestY = y }
                }
            }
            val x0 = max(0, bestX - 1); val x1 = min(63, bestX + 1)
            val y0 = max(0, bestY - 1); val y1 = min(63, bestY + 1)
            var maxLogit = Double.NEGATIVE_INFINITY
            for (y in y0..y1) for (x in x0..x1) maxLogit = max(maxLogit, hm[y][x].toDouble())
            var sumW = 0.0; var sumX = 0.0; var sumY = 0.0
            for (y in y0..y1) {
                for (x in x0..x1) {
                    val w = exp(hm[y][x].toDouble() - maxLogit)
                    sumW += w; sumX += w * (x + 0.5); sumY += w * (y + 0.5)
                }
            }
            val x64 = if (sumW < 1e-12) bestX + 0.5 else sumX / sumW
            val y64 = if (sumW < 1e-12) bestY + 0.5 else sumY / sumW
            corners256[c][0] = x64 * 4.0; corners256[c][1] = y64 * 4.0
        }
        return corners256
    }

    private fun computeMaskStats(maskLogits: Array<Array<Array<FloatArray>>>): MaskStats {
        val m = maskLogits[0][0]
        var count = 0; var sumProb = 0.0
        for (y in 0..63) {
            for (x in 0..63) {
                val p = sigmoid(m[y][x].toDouble())
                sumProb += p
                if (p > 0.5) count++
            }
        }
        return MaskStats(count, sumProb / 4096.0)
    }

    private fun mapCorners256ToOriginal(corners256: Array<DoubleArray>, lb: DocQuadLetterbox): Array<DoubleArray> {
        return Array(4) { i -> lb.inverse(corners256[i][0], corners256[i][1]) }
    }

    private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))
}
