package com.armanmaurya.archiv.ml.corners

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import com.armanmaurya.archiv.ml.docquad.DocQuadLetterbox
import com.armanmaurya.archiv.ml.docquad.DocQuadOrtRunner
import com.armanmaurya.archiv.ml.docquad.DocQuadPostprocessor

/** Produktiver Adapter für DocQuadNet-256. */
class DocQuadDetector(private val runner: DocQuadOrtRunner) : CornerDetector {

    companion object {
        private const val TAG = "DocQuadDetector"
        const val DEFAULT_MODEL_ASSET_PATH = "docquad/docquadnet256_trained_opset17.ort"
    }

    override fun detect(src: Bitmap, ctx: Context): DetectionResult {
        var in256: Bitmap? = null
        try {
            val srcW = src.width
            val srcH = src.height
            if (srcW <= 0 || srcH <= 0) return DetectionResult.fail(Source.DOCQUAD)

            val lb = DocQuadLetterbox.create(srcW, srcH, DocQuadOrtRunner.IN_W, DocQuadOrtRunner.IN_H)
            in256 = renderLetterbox256(src, lb)
            val input = bitmapToNchwFloat01(in256)

            val outputs = runner.run(input)

            val r = DocQuadPostprocessor.postprocess(
                outputs.cornerHeatmaps,
                outputs.maskLogits,
                lb,
                DocQuadPostprocessor.PeakMode.REFINE_3X3
            )

            if (r.chosenQuadOriginal == null || r.chosenQuadOriginal.size != 4) {
                return DetectionResult.fail(Source.DOCQUAD)
            }

            if (!isValidQuad(r.chosenQuadOriginal, srcW, srcH)) {
                return DetectionResult.fail(Source.DOCQUAD)
            }

            return DetectionResult.successDebug(
                Source.DOCQUAD,
                r.chosenQuadOriginal,
                r.chosenSource.name,
                r.penaltyMask,
                r.penaltyCorners
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Detection failed", t)
            return DetectionResult.fail(Source.DOCQUAD)
        } finally {
            try {
                if (in256 != null && !in256.isRecycled) in256.recycle()
            } catch (ignore: Throwable) {}
        }
    }

    private fun bitmapToNchwFloat01(bmp: Bitmap): FloatArray {
        val w = bmp.width
        val h = bmp.height
        require(w == DocQuadOrtRunner.IN_W && h == DocQuadOrtRunner.IN_H) { "bitmap must be 256x256" }
        
        val hw = h * w
        val out = FloatArray(3 * hw)
        val px = IntArray(hw)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        
        for (i in 0 until hw) {
            val c = px[i]
            out[i] = ((c shr 16) and 0xFF) / 255.0f // R
            out[hw + i] = ((c shr 8) and 0xFF) / 255.0f // G
            out[2 * hw + i] = (c and 0xFF) / 255.0f // B
        }
        return out
    }

    private fun renderLetterbox256(src: Bitmap, lb: DocQuadLetterbox): Bitmap {
        val out = Bitmap.createBitmap(DocQuadOrtRunner.IN_W, DocQuadOrtRunner.IN_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)

        val left = lb.offsetX.toFloat()
        val top = lb.offsetY.toFloat()
        val right = (lb.offsetX + lb.srcW.toDouble() * lb.scale).toFloat()
        val bottom = (lb.offsetY + lb.srcH.toDouble() * lb.scale).toFloat()
        val dst = RectF(left, top, right, bottom)
        canvas.drawBitmap(src, null, dst, null)
        return out
    }

    private fun isValidQuad(c: Array<DoubleArray>?, w: Int, h: Int): Boolean {
        if (c == null || c.size != 4) return false
        for (i in 0..3) {
            if (c[i].size != 2) return false
            val x = c[i][0]
            val y = c[i][1]
            if (!x.isFinite() || !y.isFinite()) return false
            if (x < -w * 0.25 || x > w * 1.25) return false
            if (y < -h * 0.25 || y > h * 1.25) return false
        }
        return true
    }
}
