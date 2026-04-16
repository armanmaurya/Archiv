package com.example.pdfscanner.camera

import android.content.Context
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraPreviewController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val detector: DocumentCornerDetector = DocumentCornerDetector()
) {
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private var lastDetectedCorners: List<PointF>? = null
    private var strikeOutCount = 0

    fun startCameraPreview(
        previewView: PreviewView,
        getCurrentAspectRatio: () -> Float,
        onImageCaptureReady: (ImageCapture) -> Unit,
        onCornersUpdated: (List<PointF>?, Float?) -> Unit,
        onError: (String) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                onImageCaptureReady(imageCapture)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        try {
                            val result = detector.detectDocumentCorners(imageProxy)
                            val smoothedResult = if (result != null) {
                                strikeOutCount = 0
                                val (rawCorners, aspect) = result
                                val sorted = detector.sortCornersClockwise(rawCorners)
                                val currentLast = lastDetectedCorners
                                val smoothed = if (currentLast != null && currentLast.size == 4) {
                                    sorted.mapIndexed { index, newPt ->
                                        val oldPt = currentLast[index]
                                        PointF(
                                            oldPt.x * 0.7f + newPt.x * 0.3f,
                                            oldPt.y * 0.7f + newPt.y * 0.3f
                                        )
                                    }
                                } else {
                                    sorted
                                }

                                lastDetectedCorners = smoothed
                                Pair(smoothed, aspect)
                            } else {
                                strikeOutCount++
                                if (strikeOutCount > 5) {
                                    lastDetectedCorners = null
                                    null
                                } else {
                                    lastDetectedCorners?.let { Pair(it, getCurrentAspectRatio()) }
                                }
                            }

                            ContextCompat.getMainExecutor(context).execute {
                                if (smoothedResult != null) {
                                    onCornersUpdated(smoothedResult.first, smoothedResult.second)
                                } else {
                                    onCornersUpdated(null, null)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("detectDocumentCorners", "Error locating document corners", e)
                        } finally {
                            imageProxy.close()
                        }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )
                } catch (error: IllegalStateException) {
                    onError("Unable to bind camera: ${error.message ?: "unknown error"}")
                } catch (error: IllegalArgumentException) {
                    onError("Unable to bind camera: ${error.message ?: "unknown error"}")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }
}
