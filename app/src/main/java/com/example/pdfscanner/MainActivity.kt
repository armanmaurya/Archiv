package com.example.pdfscanner

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.pdfscanner.camera.CameraPreviewController
import com.example.pdfscanner.image.fullImageBounds
import com.example.pdfscanner.image.orderCorners
import com.example.pdfscanner.navigation.AppNavHost
import com.example.pdfscanner.core.theme.PDFScannerTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.PointF
import android.util.Log
import org.opencv.android.OpenCVLoader

private data class SavedPdf(
    val id: String,
    val createdAtMillis: Long,
    val pageCount: Int,
    val pdfUri: String
)

class MainActivity : ComponentActivity() {
    private val capturedPageUris = mutableStateListOf<Uri>()
    private val pageCropBounds = mutableStateMapOf<String, List<PointF>>()
    private val pageRotationTurns = mutableStateMapOf<String, Int>()
    private val pageFilterModes = mutableStateMapOf<String, Int>()
    private val savedPdfs = mutableStateListOf<SavedPdf>()
    private var hasCameraPermission by mutableStateOf(false)
    private var isBusy by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    private var imageCapture: ImageCapture? = null
    private var detectedDocumentCorners by mutableStateOf<List<PointF>?>(null)
    private var imageAspectRatio by mutableStateOf(0.75f)
    private val cameraPreviewController by lazy { CameraPreviewController(this, this) }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (!granted) {
                errorMessage = "Camera permission is required to scan documents."
            }
        }

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) {
                isBusy = true
                lifecycleScope.launch {
                    val copiedUris = withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri -> copyUriToCache(uri) }
                    }
                    capturedPageUris.addAll(copiedUris)
                    copiedUris.forEach { uri ->
                        pageCropBounds.putIfAbsent(uri.toString(), fullImageBounds())
                        pageRotationTurns.putIfAbsent(uri.toString(), 0)
                        pageFilterModes.putIfAbsent(uri.toString(), 0)
                    }
                    isBusy = false
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (OpenCVLoader.initDebug()) {
            Log.d("SCANNER", "OpenCV loaded successfully!")
        } else {
            Log.e("SCANNER", "OpenCV load failed.")
        }
        
        hasCameraPermission = isCameraPermissionGranted()
        if (!hasCameraPermission) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        enableEdgeToEdge()
        setContent {
            PDFScannerTheme {
                AppNavHost(
                    hasCameraPermission = hasCameraPermission,
                    capturedPageUris = capturedPageUris.toList(),
                    pageCropBounds = pageCropBounds,
                    pageRotationTurns = pageRotationTurns,
                    pageFilterModes = pageFilterModes,
                    detectedCorners = detectedDocumentCorners,
                    imageAspectRatio = imageAspectRatio,
                    isBusy = isBusy,
                    errorMessage = errorMessage,
                    onCameraPreviewReady = ::startCameraPreview,
                    onRequestPermission = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onCapturePage = ::capturePage,
                    onSavePdf = ::saveCapturedPagesAsPdf,
                    onPickImages = { pickImagesLauncher.launch("image/*") },
                    onClearPages = {
                        capturedPageUris.clear()
                        pageCropBounds.clear()
                        pageRotationTurns.clear()
                        pageFilterModes.clear()
                    },
                    onUpdateCropBounds = { uri, bounds -> pageCropBounds[uri] = bounds },
                    onUpdateRotationTurns = { uri, turns -> pageRotationTurns[uri] = ((turns % 4) + 4) % 4 },
                    onUpdateFilterMode = { uri, mode -> pageFilterModes[uri] = mode.coerceIn(0, 2) },
                    onClearCropBounds = { pageCropBounds.clear() },
                    onDismissError = { errorMessage = null },
                    onReorderPages = { from, to ->
                        val item = capturedPageUris.removeAt(from)
                        capturedPageUris.add(to, item)
                    },
                    onRemovePage = { index ->
                        if (index in capturedPageUris.indices) {
                            val removed = capturedPageUris.removeAt(index)
                            pageCropBounds.remove(removed.toString())
                            pageRotationTurns.remove(removed.toString())
                            pageFilterModes.remove(removed.toString())
                        }
                    }
                )
            }
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCameraPreview(previewView: PreviewView) {
        cameraPreviewController.startCameraPreview(
            previewView = previewView,
            getCurrentAspectRatio = { imageAspectRatio },
            onImageCaptureReady = { imageCapture = it },
            onCornersUpdated = { corners, aspect ->
                if (corners != null && aspect != null) {
                    detectedDocumentCorners = corners
                    imageAspectRatio = aspect
                } else {
                    detectedDocumentCorners = null
                }
            },
            onError = { errorMessage = it }
        )
    }

    private fun capturePage() {
        if (!hasCameraPermission) {
            errorMessage = "Camera permission is required to capture pages."
            return
        }

        val captureUseCase = imageCapture
        if (captureUseCase == null) {
            errorMessage = "Camera is not ready yet."
            return
        }

        val cacheDirectory = File(cacheDir, "captured_pages")
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            errorMessage = "Unable to create cache directory for captured pages."
            return
        }

        isBusy = true
        errorMessage = null
        val outputFile = File(cacheDirectory, "page_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFile.toUri()
                    Log.d("CapturePage", "Image saved to file: ${outputFile.absolutePath}")
                    Log.d("CapturePage", "File exists: ${outputFile.exists()}, Size: ${outputFile.length()} bytes")
                    Log.d("CapturePage", "Adding URI to list: $savedUri")
                    capturedPageUris.add(savedUri)
                    val initialBounds = sanitizeInitialBounds(
                        detectedDocumentCorners
                        ?.takeIf { it.size == 4 }
                        ?.let { orderCorners(it) }
                    )
                    pageCropBounds[savedUri.toString()] = initialBounds
                    pageRotationTurns[savedUri.toString()] = 0
                    pageFilterModes[savedUri.toString()] = 0
                    isBusy = false
                }

                override fun onError(exception: ImageCaptureException) {
                    isBusy = false
                    errorMessage = exception.message ?: "Failed to capture page."
                    Log.e("CapturePage", "ImageCapture error: ${exception.message}", exception)
                }
            }
        )
    }

    /** Copies a [uri] (any scheme) into the app's page cache and returns a stable file:// URI. */
    private fun copyUriToCache(uri: Uri): Uri? {
        return try {
            val cacheDirectory = File(cacheDir, "captured_pages").also { if (!it.exists()) it.mkdirs() }
            val outputFile = File(cacheDirectory, "gallery_${System.currentTimeMillis()}_${uri.lastPathSegment?.takeLast(20)?.replace("/", "_")}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output -> input.copyTo(output) }
            } ?: run {
                Log.e("PickImages", "Could not open input stream for $uri")
                return null
            }
            if (outputFile.length() == 0L) {
                outputFile.delete()
                Log.e("PickImages", "Copied file is empty for $uri")
                return null
            }
            Log.d("PickImages", "Copied $uri → ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            outputFile.toUri()
        } catch (e: Exception) {
            Log.e("PickImages", "Failed to copy URI $uri: ${e.message}", e)
            null
        }
    }

    private fun saveCapturedPagesAsPdf() {
        if (capturedPageUris.isEmpty()) {
            errorMessage = "Capture at least one page before saving."
            return
        }

        isBusy = true
        errorMessage = null
        val pages = capturedPageUris.toList()
        val pageCount = pages.size

        lifecycleScope.launch {
            try {
                val savedUri = withContext(Dispatchers.IO) {
                    savePagesToDownloadsPdf(pages)
                }
                savedPdfs.add(
                    0,
                    SavedPdf(
                        id = "scan-${System.currentTimeMillis()}",
                        createdAtMillis = System.currentTimeMillis(),
                        pageCount = pageCount,
                        pdfUri = savedUri.toString()
                    )
                )
                capturedPageUris.clear()
            } catch (error: IOException) {
                errorMessage = error.message ?: "Failed to save PDF."
            } catch (error: SecurityException) {
                errorMessage = error.message ?: "Permission denied while saving PDF."
            } finally {
                isBusy = false
            }
        }
    }

    private fun savePagesToDownloadsPdf(pageUris: List<Uri>): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savePagesToMediaStorePdf(pageUris)
        } else {
            savePagesToExternalAppPdf(pageUris)
        }
    }

    private fun savePagesToMediaStorePdf(pageUris: List<Uri>): Uri {
        val resolver = contentResolver
        val fileName = "scan_${System.currentTimeMillis()}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + File.separator + "PDFScanner"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val destinationUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create PDF in Downloads.")

        try {
            resolver.openOutputStream(destinationUri, "w")?.use { outputStream ->
                writePagesToPdf(outputStream, pageUris)
            } ?: throw IOException("Unable to open destination output stream.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(destinationUri, values, null, null)
        } catch (error: IOException) {
            resolver.delete(destinationUri, null, null)
            throw error
        } catch (error: SecurityException) {
            resolver.delete(destinationUri, null, null)
            throw error
        }

        return destinationUri
    }

    private fun savePagesToExternalAppPdf(pageUris: List<Uri>): Uri {
        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("External downloads directory is unavailable.")
        val outputDir = File(downloadsDir, "PDFScanner")
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IOException("Unable to create output directory.")
        }

        val outputFile = File(outputDir, "scan_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { outputStream ->
            writePagesToPdf(outputStream, pageUris)
        }

        if (outputFile.length() == 0L) {
            outputFile.delete()
            throw IOException("Saved PDF is empty.")
        }

        return outputFile.toUri()
    }

    private fun writePagesToPdf(
        outputStream: OutputStream,
        pageUris: List<Uri>
    ) {
        if (pageUris.isEmpty()) {
            throw IOException("No captured pages to write to PDF.")
        }

        val pdfDocument = PdfDocument()
        try {
            pageUris.forEachIndexed { index, imageUri ->
                val bitmap = decodeScaledBitmap(imageUri)
                    ?: throw IOException("Unable to decode captured page: $imageUri")
                val page = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                )
                try {
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                } finally {
                    pdfDocument.finishPage(page)
                    bitmap.recycle()
                }
            }
            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
        }
    }

    private fun decodeScaledBitmap(uri: Uri, maxDimension: Int = 2000): Bitmap? {
        return com.example.pdfscanner.decodeScaledBitmap(this, uri, maxDimension)
    }

    private fun sanitizeInitialBounds(bounds: List<PointF>?): List<PointF> {
        if (bounds == null || bounds.size != 4) return fullImageBounds()
        val clamped = bounds.map { point ->
            PointF(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        }
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
    
}

fun decodeScaledBitmap(context: android.content.Context, uri: Uri, maxDimension: Int = 2000): Bitmap? {
    try {
        // Handle file:// URIs directly (e.g., from cache directory)
        if (uri.scheme == "file") {
            val filePath = uri.path
            if (filePath != null) {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    Log.d("DecodeScaledBitmap", "Decoding file directly: $filePath")
                    return decodeBitmapFromFile(filePath, maxDimension)
                }
            }
        }
        
        // Otherwise use ContentResolver
        Log.d("DecodeScaledBitmap", "Decoding via ContentResolver: $uri")
        return com.example.pdfscanner.decodeScaledBitmapViaContentResolver(context, uri, maxDimension)
    } catch (e: Exception) {
        Log.e("DecodeScaledBitmap", "Error decoding bitmap: ${e.message}", e)
        return null
    }
}

private fun decodeBitmapFromFile(filePath: String, maxDimension: Int = 2000): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(filePath, bounds)

    val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = if (largestDimension <= maxDimension) {
        1
    } else {
        maxOf(1, largestDimension / maxDimension)
    }

    val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return android.graphics.BitmapFactory.decodeFile(filePath, decodeOptions)
}

fun decodeScaledBitmapViaContentResolver(context: android.content.Context, uri: Uri, maxDimension: Int = 2000): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return null

    val largestDimension = max(bounds.outWidth, bounds.outHeight)
    val sampleSize = if (largestDimension <= maxDimension) {
        1
    } else {
        max(1, largestDimension / maxDimension)

    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
}
