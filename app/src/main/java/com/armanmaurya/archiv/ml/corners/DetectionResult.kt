package com.armanmaurya.archiv.ml.corners

/**
 * Ergebnis der Corner-Detection in Original-Bitmap-Koordinaten (TL,TR,BR,BL).
 */
data class DetectionResult(
    val success: Boolean,
    val source: Source,
    val cornersOriginalTLTRBRBL: Array<DoubleArray>? = null,
    val chosenSource: String? = null,
    val penaltyMask: Double? = null,
    val penaltyCorners: Double? = null
) {
    companion object {
        fun success(source: Source, cornersOriginalTLTRBRBL: Array<DoubleArray>): DetectionResult {
            return DetectionResult(true, source, cornersOriginalTLTRBRBL)
        }

        fun successDebug(
            source: Source,
            cornersOriginalTLTRBRBL: Array<DoubleArray>,
            chosenSource: String?,
            penaltyMask: Double?,
            penaltyCorners: Double?
        ): DetectionResult {
            return DetectionResult(
                true, source, cornersOriginalTLTRBRBL, chosenSource, penaltyMask, penaltyCorners
            )
        }

        fun fail(source: Source): DetectionResult {
            return DetectionResult(false, source)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DetectionResult

        if (success != other.success) return false
        if (source != other.source) return false
        if (cornersOriginalTLTRBRBL != null) {
            if (other.cornersOriginalTLTRBRBL == null) return false
            if (!cornersOriginalTLTRBRBL.contentDeepEquals(other.cornersOriginalTLTRBRBL)) return false
        } else if (other.cornersOriginalTLTRBRBL != null) return false
        if (chosenSource != other.chosenSource) return false
        if (penaltyMask != other.penaltyMask) return false
        if (penaltyCorners != other.penaltyCorners) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (cornersOriginalTLTRBRBL?.contentDeepHashCode() ?: 0)
        result = 31 * result + (chosenSource?.hashCode() ?: 0)
        result = 31 * result + (penaltyMask?.hashCode() ?: 0)
        result = 31 * result + (penaltyCorners?.hashCode() ?: 0)
        return result
    }
}
