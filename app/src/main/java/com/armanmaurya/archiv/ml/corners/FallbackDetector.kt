package com.armanmaurya.archiv.ml.corners

import android.content.Context
import android.graphics.Bitmap

class FallbackDetector(
    private val primary: CornerDetector,
    private val secondary: CornerDetector
) : CornerDetector {
    override fun detect(src: Bitmap, ctx: Context): DetectionResult {
        val result = primary.detect(src, ctx)
        if (result.success) return result
        return secondary.detect(src, ctx)
    }
}
