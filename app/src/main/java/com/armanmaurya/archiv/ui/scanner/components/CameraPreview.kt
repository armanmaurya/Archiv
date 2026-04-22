package com.armanmaurya.archiv.ui.scanner.components

import android.Manifest
import android.R
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.armanmaurya.archiv.camera.FrameProcessor
import com.armanmaurya.archiv.bitmap.fullImageBounds
import com.armanmaurya.archiv.bitmap.orderCorners
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
        animationSpec = tween(durationMillis = 120),
        label = "$label-x"
    )
    val animatedY by animateFloatAsState(
        targetValue = point.y,
        animationSpec = tween(durationMillis = 120),
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
    var detectedCorners by remember { mutableStateOf<List<PointF>?>(null) }
    var frozenPreviewFrame by remember { mutableStateOf<Bitmap?>(null) }
    var imageAspectRatio by remember { mutableFloatStateOf(0.75f) }
    val currentDetectedCorners by rememberUpdatedState(detectedCorners)
    val clearFrozenPreviewFrame = {
        frozenPreviewFrame?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        frozenPreviewFrame = null
    }

    DisposableEffect(Unit) {
        onDispose {
            clearFrozenPreviewFrame()
            analyzerExecutor.shutdown()
            onCameraBusyChange(false)
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
                onCornersUpdated = { corners, aspect ->
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
                Text("Camera permission is required.", color = MaterialTheme.colorScheme.error)
                Button(onClick = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant permission")
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

        SearchingIndicator(
                isVisible = isAutoEdgeDetectionEnabled && detectedCorners == null && frozenPreviewFrame == null,
                modifier = Modifier.align(Alignment.BottomCenter)
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
        onCornersUpdated: (List<PointF>?, Float?) -> Unit,
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
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                    var lastDetectedCorners: List<PointF>? = null
                    var lastAspectRatio: Float? = null
                    var strikeOutCount = 0

                    imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                        try {
                            val result = frameProcessor.processFrame(imageProxy)
                            val smoothedResult =
                                    if (result != null) {
                                        strikeOutCount = 0
                                        val (rawCorners, aspect) = result
                                        val sorted = frameProcessor.sortCornersClockwise(rawCorners)
                                        val currentLast = lastDetectedCorners
                                        val smoothed =
                                                if (currentLast != null && currentLast.size == 4) {
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
                    }

                    useCases += imageAnalysis
                } else {
                    onCornersUpdated(null, null)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            *useCases.toTypedArray()
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

private fun sanitizeInitialBounds(bounds: List<PointF>?): List<PointF> {
    if (bounds == null || bounds.size != 4) return fullImageBounds()
    val clamped = bounds.map { point -> PointF(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f)) }
    val area = kotlin.math.abs(polygonArea(clamped))
    if (area < 0.02f) return fullImageBounds()
    return clamped
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
