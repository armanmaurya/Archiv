package com.armanmaurya.archiv.ml.corners

import android.content.Context
import android.graphics.Bitmap

/**
 * Schlanke Abstraktion für Corner-Detection.
 */
interface CornerDetector {
    fun detect(src: Bitmap, ctx: Context, isLiveAnalysis: Boolean = false): DetectionResult
}
