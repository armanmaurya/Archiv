package com.armanmaurya.archiv.ui.scanner.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.armanmaurya.archiv.R
import com.armanmaurya.archiv.camera.FrameProcessor
import com.armanmaurya.archiv.bitmap.fullImageBounds
import com.armanmaurya.archiv.bitmap.orderCorners
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun CameraView(
    onCameraPreviewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { PreviewView(context).also(onCameraPreviewReady) }
    )
}

@Composable
private fun DocumentOverlay(
    corners: List<PointF>?,
    imageAspectRatio: Float,
    modifier: Modifier = Modifier
) {
    val detectedCorners = corners?.takeIf { it.size == 4 } ?: return
    val animatedCorners = detectedCorners.mapIndexed { index, point ->
        animateCornerPoint(point = point, label = "corner-$index")
    }

    val cornerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    Canvas(modifier = modifier.fillMaxSize()) {
        val targetRatio = size.width / size.height

        val scaledWidth: Float
        val scaledHeight: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspectRatio > targetRatio) {
            scaledHeight = size.height
            scaledWidth = size.height * imageAspectRatio
            offsetX = (size.width - scaledWidth) / 2f
            offsetY = 0f
        } else {
            scaledWidth = size.width

            scaledHeight = size.width / imageAspectRatio
            offsetX = 0f
            offsetY = (size.height - scaledHeight) / 2f
        }

        val cornerRadius = 14.dp.toPx()
        animatedCorners.forEach { point ->
            val center = Offset(
                x = point.x * scaledWidth + offsetX,
                y = point.y * scaledHeight + offsetY
            )
            drawCircle(
                color = cornerColor,
                radius = cornerRadius,
                center = center
            )
        }
    }
}

@Composable
private fun animateCornerPoint(point: PointF, label: String): PointF {
    val animatedX by animateFloatAsState(
        targetValue = point.x,
        animationSpec = tween(durationMillis = 220, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "$label-x"
    )
    val animatedY by animateFloatAsState(
        targetValue = point.y,
        animationSpec = tween(durationMillis = 220, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "$label-y"
    )
    return PointF(animatedX, animatedY)
}

@Composable
private fun CameraError(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    message?.let {
        Surface(
            modifier = modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss error")
                }
            }
        }
    }
}

@Composable
private fun SearchingIndicator(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Surface(
            modifier = modifier.padding(bottom = 24.dp),
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(text = "Searching for documents", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun CameraPreview(
    captureRequestKey: Long,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onCapture: (Uri, List<PointF>) -> Unit,
    onCameraBusyChange: (Boolean) -> Unit,
    onCameraError: (String?) -> Unit,
    isAutoEdgeDetectionEnabled: Boolean,
    isAutoCaptureEnabled: Boolean = false,
    onAutoCapture: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val frameProcessor = remember { FrameProcessor() }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var hasCameraPermission by remember { mutableStateOf(isCameraPermissionGranted(context)) }

    val requestCameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (!granted) {
                onCameraBusyChange(false)
                onCameraError("Camera permission is required to scan documents.")
            } else {
                onCameraError(null)
            }
        }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var detectedCorners by remember { mutableStateOf<List<PointF>?>(null) }
    var frozenPreviewFrame by remember { mutableStateOf<Bitmap?>(null) }

    var imageAspectRatio by remember { mutableFloatStateOf(0.75f) }
    var autoCaptureProgress by remember { mutableFloatStateOf(0f) }
    val currentDetectedCorners by rememberUpdatedState(detectedCorners)
    val clearFrozenPreviewFrame = {
        frozenPreviewFrame?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        frozenPreviewFrame = null
    }

    // AUTO-CAPTURE DISABLED
    // LaunchedEffect(autoCaptureProgress) {
    //     if (autoCaptureProgress >= 1f && isAutoCaptureEnabled) {
    //         onAutoCapture()
    //     }
    // }

    DisposableEffect(Unit) {
        onDispose {
            clearFrozenPreviewFrame()
            analyzerExecutor.shutdown()
            onCameraBusyChange(false)
            camera?.cameraControl?.enableTorch(false)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(isAutoEdgeDetectionEnabled) {
        if (!isAutoEdgeDetectionEnabled) {
            detectedCorners = null
        }
    }

    LaunchedEffect(previewView, lifecycleOwner, hasCameraPermission, isAutoEdgeDetectionEnabled) {
        val targetPreviewView = previewView
        if (!hasCameraPermission || targetPreviewView == null) {
            imageCapture = null
            camera = null
            isTorchOn = false
            detectedCorners = null
            clearFrozenPreviewFrame()
            onCameraBusyChange(false)
            return@LaunchedEffect
        }

        bindCameraPreview(
            context = context,
            lifecycleOwner = lifecycleOwner,
            frameProcessor = frameProcessor,
            analyzerExecutor = analyzerExecutor,
            previewView = targetPreviewView,
            autoEdgeDetectionEnabled = isAutoEdgeDetectionEnabled,
            onImageCaptureReady = { captureUseCase -> imageCapture = captureUseCase },
            onCameraReady = { boundCamera -> camera = boundCamera },
            onStabilityProgress = { _ -> /* AUTO-CAPTURE DISABLED */ },
            onCornersUpdated = { corners, aspect, bmp ->
                if (corners != null && aspect != null) {
                    detectedCorners = corners
                    imageAspectRatio = aspect
                } else {
                    detectedCorners = null
                }
            },
            onError = { message -> onCameraError(message) }
        )
    }

    LaunchedEffect(captureRequestKey, hasCameraPermission) {
        if (captureRequestKey == 0L) return@LaunchedEffect
        if (!hasCameraPermission) {
            onCameraError("Camera permission is required to capture pages.")
            return@LaunchedEffect
        }

        val captureUseCase = imageCapture
        if (captureUseCase == null) {
            onCameraError("Camera is not ready yet.")
            return@LaunchedEffect
        }

        val cacheDirectory = File(context.cacheDir, "captured_pages")
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            onCameraError("Unable to create cache directory for captured pages.")
            return@LaunchedEffect
        }

        val outputFile = File(cacheDirectory, "page_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        onCameraBusyChange(true)
        onCameraError(null)
        previewView?.bitmap?.copy(Bitmap.Config.ARGB_8888, false)?.let { capturedFrame ->
            clearFrozenPreviewFrame()
            frozenPreviewFrame = capturedFrame
        }

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    clearFrozenPreviewFrame()
                    val savedUri = outputFile.toUri()
                    val initialBounds = if (isAutoEdgeDetectionEnabled) {
                        sanitizeInitialBounds(
                            currentDetectedCorners
                                ?.takeIf { it.size == 4 }
                                ?.let { orderCorners(it) }
                        )
                    } else {
                        fullImageBounds()
                    }
                    onCapture(savedUri, initialBounds)
                    onCameraBusyChange(false)
                }

                override fun onError(exception: ImageCaptureException) {
                    clearFrozenPreviewFrame()
                    onCameraBusyChange(false)
                    onCameraError(exception.message ?: "Failed to capture page.")
                    Log.e("CapturePage", "ImageCapture error: ${exception.message}", exception)
                }
            }
        )
    }

    if (!hasCameraPermission) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.camera_permission_required), color = MaterialTheme.colorScheme.error)
                Button(onClick = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.camera_permission_grant))
                }
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxWidth()) {
        CameraView(
            onCameraPreviewReady = { preview -> previewView = preview },
            modifier =
                Modifier.fillMaxSize().clip(
                    RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
        )

        frozenPreviewFrame?.let { capturedFrame ->
            Image(
                bitmap = capturedFrame.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(
                    RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
            )
        }

        if (isAutoEdgeDetectionEnabled) {
            DocumentOverlay(corners = detectedCorners, imageAspectRatio = imageAspectRatio)
        }

        CameraError(
            message = errorMessage,
            onDismiss = onDismissError,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Torch toggle button — top-end corner
        camera?.let { boundCamera ->
            val torchIcon = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff
            val torchBg = if (isTorchOn)
                androidx.compose.ui.graphics.Color(0xFFFFB300) // amber
            else
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)
            val torchTint = if (isTorchOn)
                androidx.compose.ui.graphics.Color.Black
            else
                androidx.compose.ui.graphics.Color.White
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(torchBg)
                    .clickable {
                        val next = !isTorchOn
                        isTorchOn = next
                        boundCamera.cameraControl.enableTorch(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = torchIcon,
                    contentDescription = if (isTorchOn) "Turn off torch" else "Turn on torch",
                    tint = torchTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        SearchingIndicator(
            isVisible = isAutoEdgeDetectionEnabled && detectedCorners == null && frozenPreviewFrame == null,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // AUTO-CAPTURE DISABLED
        // if (isAutoCaptureEnabled && autoCaptureProgress > 0f) {
        //     AutoCaptureProgressArc(
        //         progress = autoCaptureProgress,
        //         modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        //     )
        // }
    }
}

@Composable
private fun AutoCaptureProgressArc(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(56.dp)) {
        drawArc(
            color = color.copy(alpha = 0.25f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun bindCameraPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    frameProcessor: FrameProcessor,
    analyzerExecutor: ExecutorService,
    previewView: PreviewView,
    autoEdgeDetectionEnabled: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraReady: (Camera) -> Unit,
    onStabilityProgress: (Float) -> Unit,
    onCornersUpdated: (List<PointF>?, Float?, Bitmap?) -> Unit,
    onError: (String) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            val preview =
                Preview.Builder()
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageCapture =
                ImageCapture.Builder()
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
            onImageCaptureReady(imageCapture)

            val useCases = mutableListOf<UseCase>(preview, imageCapture)
            if (autoEdgeDetectionEnabled) {
                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                var lastDetectedCorners: List<PointF>? = null
                var lastAspectRatio: Float? = null
                var strikeOutCount = 0
                var prevStableCorners: List<PointF>? = null
                var stableFrameCount = 0
                val STABLE_FRAMES_REQUIRED = 15
                val STABILITY_THRESHOLD = 0.015f

                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    try {
                        val result = frameProcessor.processFrame(imageProxy, context)
                        val smoothedResult =
                            if (result != null && result.first != null && result.second != null) {
                                strikeOutCount = 0
                                val rawCorners = result.first!!
                                val aspect = result.second!!
                                val sorted = frameProcessor.sortCornersClockwise(rawCorners)
                                val currentLast = lastDetectedCorners
                                val smoothed =
                                    if (currentLast != null && currentLast.size == 4) {
                                        sorted.mapIndexed { index, newPt ->
                                            val oldPt = currentLast[index]
                                            // Higher old-weight (0.55) means smaller per-frame jump,
                                            // producing a smoother glide toward the new prediction.
                                            PointF(
                                                oldPt.x * 0.55f + newPt.x * 0.45f,
                                                oldPt.y * 0.55f + newPt.y * 0.45f
                                            )
                                        }
                                    } else {
                                        sorted
                                    }

                            lastDetectedCorners = smoothed
                                lastAspectRatio = aspect
                                Pair(smoothed, aspect)
                            } else {
                                strikeOutCount++
                                if (strikeOutCount > 5) {
                                    lastDetectedCorners = null
                                    null
                                } else {
                                    val corners = lastDetectedCorners
                                    val aspect = lastAspectRatio
                                    if (corners != null && aspect != null) {
                                        Pair(corners, aspect)
                                    } else {
                                        null
                                    }
                                }
                            }

                        val currentCorners = smoothedResult?.first
                        if (currentCorners != null && currentCorners.size == 4) {
                            val prev = prevStableCorners
                            val maxDisplacement = if (prev != null && prev.size == 4) {
                                currentCorners.zip(prev).maxOf { (new, old) ->
                                    kotlin.math.hypot(
                                        (new.x - old.x).toDouble(),
                                        (new.y - old.y).toDouble()
                                    ).toFloat()
                                }
                            } else Float.MAX_VALUE
                            if (maxDisplacement < STABILITY_THRESHOLD) {
                                stableFrameCount = minOf(stableFrameCount + 1, STABLE_FRAMES_REQUIRED)
                            } else {
                                stableFrameCount = 0
                            }
                            prevStableCorners = currentCorners
                        } else {
                            stableFrameCount = 0
                            prevStableCorners = null
                        }
                        val stabilityProgress = (stableFrameCount.toFloat() / STABLE_FRAMES_REQUIRED).coerceIn(0f, 1f)
                        if (stableFrameCount >= STABLE_FRAMES_REQUIRED) {
                            stableFrameCount = 0
                        }

                        val edgeBmp = result?.third
                        ContextCompat.getMainExecutor(context).execute {
                            onStabilityProgress(stabilityProgress)
                            if (smoothedResult != null) {
                                onCornersUpdated(smoothedResult.first, smoothedResult.second, edgeBmp)
                            } else {
                                onCornersUpdated(null, null, edgeBmp)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("detectDocumentCorners", "Error locating document corners", e)
                    } finally {
                        imageProxy.close()
                    }
                }

                useCases += imageAnalysis
            } else {
                onCornersUpdated(null, null, null)
            }

            try {
                cameraProvider.unbindAll()
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    *useCases.toTypedArray()
                )
                ContextCompat.getMainExecutor(context).execute {
                    onCameraReady(boundCamera)
                }
            } catch (error: IllegalStateException) {
                onError("Unable to bind camera: ${error.message ?: "unknown error"}")
            } catch (error: IllegalArgumentException) {
                onError("Unable to bind camera: ${error.message ?: "unknown error"}")
            }
        },
        ContextCompat.getMainExecutor(context)
    )
}

private fun sanitizeInitialBounds(bounds: List<PointF>?): List<PointF> {
    if (bounds == null || bounds.size != 4) return fullImageBounds()
    val clamped = bounds.map { point -> PointF(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f)) }
    val area = kotlin.math.abs(polygonArea(clamped))
    if (area < 0.02f) return fullImageBounds()
    if (!isConvexQuad(clamped)) return fullImageBounds()
    return clamped
}

/**
 * Returns true if the four points (in order) form a convex (non-self-intersecting) quadrilateral.
 * Uses the cross-product sign at each vertex; all signs must agree for the quad to be convex.
 */
private fun isConvexQuad(pts: List<PointF>): Boolean {
    if (pts.size != 4) return false
    var sign = 0
    for (i in pts.indices) {
        val a = pts[i]
        val b = pts[(i + 1) % 4]
        val c = pts[(i + 2) % 4]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        val s = when {
            cross > 1e-6f  ->  1
            cross < -1e-6f -> -1
            else           ->  0   // collinear – degenerate edge
        }
        if (s == 0) return false   // collinear vertices → degenerate quad
        if (sign == 0) sign = s else if (s != sign) return false
    }
    return true
}

private fun polygonArea(points: List<PointF>): Float {
    if (points.size < 3) return 0f
    var sum = 0f
    for (i in points.indices) {
        val j = (i + 1) % points.size
        sum += points[i].x * points[j].y - points[j].x * points[i].y
    }
    return sum * 0.5f
}

private fun isCameraPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}
