package com.armanmaurya.archiv.ml.corners

/**
 * Ergebnis der Corner-Detection in Original-Bitmap-Koordinaten (TL,TR,BR,BL).
 */
data class DetectionResult(
    val success: Boolean,
    val cornersOriginalTLTRBRBL: Array<DoubleArray>? = null
) {
    companion object {
        fun success(cornersOriginalTLTRBRBL: Array<DoubleArray>): DetectionResult {
            return DetectionResult(true, cornersOriginalTLTRBRBL)
        }

        fun fail(): DetectionResult {
            return DetectionResult(false)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DetectionResult

        if (success != other.success) return false
        if (cornersOriginalTLTRBRBL != null) {
            if (other.cornersOriginalTLTRBRBL == null) return false
            if (!cornersOriginalTLTRBRBL.contentDeepEquals(other.cornersOriginalTLTRBRBL)) return false
        } else if (other.cornersOriginalTLTRBRBL != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + (cornersOriginalTLTRBRBL?.contentDeepHashCode() ?: 0)
        return result
    }
}
