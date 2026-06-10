package com.armanmaurya.archiv.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.delay

data class TextSelection(
    val basePageIndex: Int,
    val baseIndex: Int,
    val extentPageIndex: Int,
    val extentIndex: Int
) {
    val isReversed: Boolean
        get() = basePageIndex > extentPageIndex || (basePageIndex == extentPageIndex && baseIndex > extentIndex)

    val startPageIndex: Int get() = if (isReversed) extentPageIndex else basePageIndex
    val startIndex: Int get() = if (isReversed) extentIndex else baseIndex
    val endPageIndex: Int get() = if (isReversed) basePageIndex else extentPageIndex
    val endIndex: Int get() = if (isReversed) baseIndex else extentIndex
}
enum class DragHandle { LEFT, RIGHT, NONE }

data class PageText(val characters: List<TextCharacter>)
data class TextCharacter(val text: String, val boundingBox: android.graphics.RectF)
data class PageLinks(val links: List<PdfLink>)
data class PdfLink(val uri: String, val boundingBox: android.graphics.RectF)

data class SearchResult(
    val pageIndex: Int,
    val startIndex: Int,
    val endIndex: Int
)

class TextPositionStripper : PDFTextStripper() {
    val characters = mutableListOf<TextCharacter>()

    override fun writeString(string: String?, textPositions: MutableList<TextPosition>?) {
        textPositions?.forEach { pos ->
            val rect = android.graphics.RectF(
                pos.xDirAdj,
                pos.yDirAdj - pos.heightDir,
                pos.xDirAdj + pos.widthDirAdj,
                pos.yDirAdj
            )
            characters.add(TextCharacter(pos.unicode, rect))
        }
    }
}

class PdfViewerViewModel : ViewModel() {

    // UI States
    var isTopBarVisible by mutableStateOf(false)
        private set
    var manualBrightness by mutableFloatStateOf(0.5f)
        private set
    var isAutoBrightness by mutableStateOf(true)
        private set
    var isInteractingWithSlider by mutableStateOf(false)
        private set
    var activeSelection by mutableStateOf<TextSelection?>(null)
        private set

    // Search States
    var isSearchActive by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    var currentSearchIndex by mutableStateOf(-1)
        private set
    var isSearching by mutableStateOf(false)
        private set

    // PDF States
    var pageCount by mutableStateOf(0)
        private set
    var isError by mutableStateOf(false)
        private set
    var isLoaded by mutableStateOf(false)
        private set

    private val _pageSizes = ConcurrentHashMap<Int, IntSize>()
    val pageSizes: Map<Int, IntSize> get() = _pageSizes

    private val _pageTextCache = ConcurrentHashMap<Int, PageText>()
    val pageTextCache: Map<Int, PageText> get() = _pageTextCache

    private val _pageLinksCache = ConcurrentHashMap<Int, PageLinks>()
    val pageLinksCache: Map<Int, PageLinks> get() = _pageLinksCache

    private val inFlightExtractions = Collections.synchronizedSet(mutableSetOf<Int>())

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdDocument: PDDocument? = null
    private var tempPdfFile: File? = null
    private val rendererMutex = Mutex()

    fun toggleTopBar() {
        isTopBarVisible = !isTopBarVisible
    }

    fun updateTopBarVisibility(visible: Boolean) {
        isTopBarVisible = visible
    }

    fun updateBrightness(brightness: Float) {
        manualBrightness = brightness
        isAutoBrightness = false
        isInteractingWithSlider = true
    }

    fun toggleAutoBrightness(auto: Boolean) {
        isAutoBrightness = auto
    }

    fun updateSliderInteraction(interacting: Boolean) {
        isInteractingWithSlider = interacting
    }

    fun updateSelection(selection: TextSelection?) {
        activeSelection = selection
    }

    fun clearSelection() {
        activeSelection = null
    }

    fun openSearch() {
        isSearchActive = true
        isTopBarVisible = false
    }

    fun closeSearch() {
        isSearchActive = false
        searchQuery = ""
        searchResults = emptyList()
        currentSearchIndex = -1
        isSearching = false
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        performSearch(query)
    }

    fun nextSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
        }
    }

    fun previousSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = if (currentSearchIndex <= 0) searchResults.size - 1 else currentSearchIndex - 1
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            searchResults = emptyList()
            currentSearchIndex = -1
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            isSearching = true
            
            // Ensure all pages are extracted
            val extractionJobs = (0 until pageCount).map { i ->
                launch { requestTextExtraction(i) }
            }
            extractionJobs.forEach { it.join() }

            val results = mutableListOf<SearchResult>()
            val lowerQuery = query.lowercase()

            for (pageIndex in 0 until pageCount) {
                val pageText = _pageTextCache[pageIndex] ?: continue
                val fullText = pageText.characters.joinToString("") { it.text }.lowercase()
                
                var index = fullText.indexOf(lowerQuery)
                while (index != -1) {
                    results.add(SearchResult(
                        pageIndex = pageIndex,
                        startIndex = index,
                        endIndex = index + lowerQuery.length - 1
                    ))
                    index = fullText.indexOf(lowerQuery, index + 1)
                }
            }

            withContext(Dispatchers.Main) {
                searchResults = results
                currentSearchIndex = if (results.isNotEmpty()) 0 else -1
                isSearching = false
            }
        }
    }

    fun selectAll() {
        if (pageCount > 0) {
            for (i in 0 until pageCount) {
                requestTextExtraction(i)
            }
            activeSelection = TextSelection(
                basePageIndex = 0,
                baseIndex = 0,
                extentPageIndex = pageCount - 1,
                extentIndex = Int.MAX_VALUE
            )
        }
    }

    fun loadDocument(uri: Uri?, context: Context) {
        if (isLoaded || isError) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context)
                if (uri != null) {
                    // Copy to temp file for random access loading (more memory efficient)
                    val tempFile = File(context.cacheDir, "temp_viewer_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempPdfFile = tempFile

                    // Use the temp file for PdfRenderer (more stable than direct Uri access)
                    val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    fileDescriptor = pfd
                    
                    val newRenderer = PdfRenderer(pfd)
                    renderer = newRenderer
                    pageCount = newRenderer.pageCount
                    
                    // Signal loaded as soon as basic renderer is ready
                    isLoaded = true

                    // Extract page sizes in background, lock only per page to not block renderPage calls
                    viewModelScope.launch(Dispatchers.IO) {
                        for (i in 0 until pageCount) {
                            try {
                                rendererMutex.withLock {
                                    renderer?.openPage(i)?.use { page ->
                                        _pageSizes[i] = IntSize(page.width, page.height)
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    
                    // Load PDFBox in background as it's only needed for text/links
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            pdDocument = PDDocument.load(tempPdfFile)
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                isError = true
            }
        }
    }

    suspend fun renderPage(index: Int, targetWidth: Int = 0): Bitmap? = withContext(Dispatchers.IO) {
        if (index < 0 || index >= pageCount) return@withContext null
        
        rendererMutex.withLock {
            val currentRenderer = renderer ?: return@withLock null
            try {
                currentRenderer.openPage(index).use { page ->
                    val width: Int
                    val height: Int
                    
                    if (targetWidth > 0) {
                        val aspectRatio = page.width.toFloat() / page.height.toFloat()
                        width = targetWidth
                        height = (targetWidth / aspectRatio).toInt()
                    } else {
                        width = page.width
                        height = page.height
                    }
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun renderTile(
        index: Int,
        tileWidth: Int,
        tileHeight: Int,
        normalizedRect: android.graphics.RectF
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (index < 0 || index >= pageCount) return@withContext null
        
        rendererMutex.withLock {
            val currentRenderer = renderer ?: return@withLock null
            try {
                currentRenderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    val matrix = android.graphics.Matrix()
                    
                    val scaleX = tileWidth / (normalizedRect.width() * page.width)
                    val scaleY = tileHeight / (normalizedRect.height() * page.height)
                    matrix.postScale(scaleX, scaleY)
                    
                    matrix.postTranslate(-normalizedRect.left * page.width * scaleX, -normalizedRect.top * page.height * scaleY)
                    
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun requestTextExtraction(index: Int) {
        if ((_pageTextCache.containsKey(index) && _pageLinksCache.containsKey(index)) || !inFlightExtractions.add(index)) return
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Wait for pdDocument if it's still loading
                var attempts = 0
                while (pdDocument == null && attempts < 50 && !isError) {
                    delay(100)
                    attempts++
                }

                val doc = pdDocument ?: return@launch
                if (index < 0 || index >= doc.numberOfPages) return@launch
                
                // Extract Text
                if (!_pageTextCache.containsKey(index)) {
                    val stripper = TextPositionStripper()
                    stripper.startPage = index + 1
                    stripper.endPage = index + 1
                    stripper.getText(doc)
                    _pageTextCache[index] = PageText(stripper.characters)
                }

                // Extract Links
                if (!_pageLinksCache.containsKey(index)) {
                    val page = doc.getPage(index)
                    val annotations = page.annotations
                    val links = mutableListOf<PdfLink>()
                    val pageHeight = page.mediaBox.height
                    
                    for (annotation in annotations) {
                        if (annotation is PDAnnotationLink) {
                            val action = annotation.action
                            if (action is PDActionURI) {
                                val rect = annotation.rectangle
                                // PDF coordinates are bottom-up, need to flip Y
                                val boundingBox = android.graphics.RectF(
                                    rect.lowerLeftX,
                                    pageHeight - rect.upperRightY,
                                    rect.upperRightX,
                                    pageHeight - rect.lowerLeftY
                                )
                                links.add(PdfLink(action.uri, boundingBox))
                            }
                        }
                    }
                    _pageLinksCache[index] = PageLinks(links)
                }
            } catch (e: Exception) {
            } finally {
                inFlightExtractions.remove(index)
            }
        }
    }

    fun getWordRange(pageIndex: Int, charIndex: Int): Pair<Int, Int> {
        val pageText = _pageTextCache[pageIndex] ?: return charIndex to charIndex
        val chars = pageText.characters
        if (charIndex < 0 || charIndex >= chars.size) return charIndex to charIndex
        
        var start = charIndex
        while (start > 0 && chars[start - 1].text.any { it.isLetterOrDigit() }) {
            start--
        }
        
        var end = charIndex
        while (end < chars.size - 1 && chars[end + 1].text.any { it.isLetterOrDigit() }) {
            end++
        }
        
        return start to end
    }

    override fun onCleared() {
        super.onCleared()
        try {
            renderer?.close()
            fileDescriptor?.close()
            pdDocument?.close()
            tempPdfFile?.delete()
        } catch (e: Exception) {}
        _pageTextCache.clear()
        _pageLinksCache.clear()
        _pageSizes.clear()
        inFlightExtractions.clear()
    }
}
