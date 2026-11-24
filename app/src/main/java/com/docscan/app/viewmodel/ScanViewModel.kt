package com.docscan.app.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.model.CornerType
import com.docscan.app.model.EnhanceMode
import com.docscan.app.model.QuadCorners
import com.docscan.app.model.ScanDocument
import com.docscan.app.model.ScannedPage
import com.docscan.app.util.FileUtils
import com.docscan.app.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    // Multi-page batch session
    var scannedPages = mutableStateOf<List<ScannedPage>>(emptyList())
        private set

    // Default filter for new scans
    var defaultFilter = mutableStateOf(EnhanceMode.Magic)
        private set

    // Current captured or imported image file (for single crop editor)
    var capturedImageFile = mutableStateOf<File?>(null)
        private set

    // Current rotation applied in editor (0, 90, 180, 270)
    var manualRotation = mutableStateOf(0)
        private set

    // Cropped bitmap for current page
    var croppedBitmap = mutableStateOf<Bitmap?>(null)
        private set

    // Enhanced bitmap for current page
    var enhancedBitmap = mutableStateOf<Bitmap?>(null)
        private set

    // Current enhancement mode for current page
    var currentEnhanceMode = mutableStateOf(EnhanceMode.Original)
        private set

    // Persistent scanned documents
    var documents = mutableStateOf<List<ScanDocument>>(emptyList())
        private set

    // Opened external PDF for viewing and tools (Remove Password, Compress, Add Password)
    var openedPdfFile = mutableStateOf<File?>(null)
        private set

    fun setOpenedPdf(file: File?) {
        openedPdfFile.value = file
    }

    // Current document ID (created on first scan)
    var currentDocumentId = mutableStateOf<String?>(null)
        private set

    // Current document title
    var currentDocumentTitle = mutableStateOf("")
        private set

    // Saved page file paths on disk for current document (O(1) memory footprint)
    var currentDocumentPagePaths = mutableStateOf<List<String>>(emptyList())
        private set

    // Queue of images for legacy multi-image import from gallery
    var imageQueue = mutableStateOf<List<File>>(emptyList())
        private set

    var queueTotalCount = mutableStateOf(0)
        private set

    var queueCurrentIndex = mutableStateOf(0)
        private set

    init {
        loadPersistedDocuments()
    }

    fun loadPersistedDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = FileUtils.loadDocuments(getApplication())
            withContext(Dispatchers.Main) {
                documents.value = list
            }
        }
    }

    // -------------------------------------------------------------
    // Continuous Camera / Batch Scanning & Multi-Page Review Flow
    // -------------------------------------------------------------

    fun addScannedPage(file: File) {
        val page = ScannedPage(
            originalFile = file,
            filter = defaultFilter.value
        )
        scannedPages.value = scannedPages.value + page
    }

    fun setScannedPages(files: List<File>) {
        scannedPages.value = files.map { file ->
            ScannedPage(
                originalFile = file,
                filter = defaultFilter.value
            )
        }
    }

    fun updatePageCorners(pageId: String, corners: QuadCorners) {
        scannedPages.value = scannedPages.value.map { page ->
            if (page.id == pageId) {
                page.copy(corners = corners, processedPath = null)
            } else {
                page
            }
        }
    }

    fun rotatePage(pageId: String) {
        scannedPages.value = scannedPages.value.map { page ->
            if (page.id == pageId) {
                val newRot = (page.rotation + 90) % 360
                page.copy(rotation = newRot, processedPath = null)
            } else {
                page
            }
        }
    }

    fun resetPageCrop(pageId: String) {
        scannedPages.value = scannedPages.value.map { page ->
            if (page.id == pageId) {
                page.copy(corners = QuadCorners.FULL, processedPath = null)
            } else {
                page
            }
        }
    }

    fun deletePage(pageIndex: Int) {
        if (pageIndex in scannedPages.value.indices) {
            val list = scannedPages.value.toMutableList()
            list.removeAt(pageIndex)
            scannedPages.value = list
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex in scannedPages.value.indices && toIndex in scannedPages.value.indices && fromIndex != toIndex) {
            val list = scannedPages.value.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            scannedPages.value = list
        }
    }

    fun applyFilterToPage(pageIndex: Int, mode: EnhanceMode) {
        if (pageIndex in scannedPages.value.indices) {
            scannedPages.value = scannedPages.value.mapIndexed { idx, page ->
                if (idx == pageIndex) page.copy(filter = mode, processedPath = null) else page
            }
        }
    }

    fun applyFilterToAllPages(mode: EnhanceMode) {
        defaultFilter.value = mode
        scannedPages.value = scannedPages.value.map { page ->
            page.copy(filter = mode, processedPath = null)
        }
    }

    fun applyFilterToSelectedPages(indices: Set<Int>, mode: EnhanceMode) {
        scannedPages.value = scannedPages.value.mapIndexed { idx, page ->
            if (idx in indices) page.copy(filter = mode, processedPath = null) else page
        }
    }

    /**
     * Process all pages in background before exporting to PDF or Images.
     * Decodes, rotates, crops and enhances one page at a time with memory recycling.
     */
    suspend fun processAllPagesForExport(context: Context): List<String> = withContext(Dispatchers.IO) {
        val docId = ensureDocumentId()
        val processedPaths = mutableListOf<String>()

        scannedPages.value.forEachIndexed { index, page ->
            val pageFile = FileUtils.createPageFile(context, docId, index)

            // Decode original with safe sampling
            var bmp = FileUtils.decodeSampledBitmap(page.originalFile.absolutePath, 2048, 2048)
            if (bmp != null) {
                // Apply manual rotation
                if (page.rotation != 0) {
                    bmp = FileUtils.rotateBitmap(bmp, page.rotation, recycleSource = true)
                }

                // Apply crop
                val cropped = ImageProcessor.perspectiveTransform(
                    bmp,
                    page.corners.topLeft,
                    page.corners.topRight,
                    page.corners.bottomLeft,
                    page.corners.bottomRight
                )

                // Apply filter
                val enhanced = ImageProcessor.applyEnhancement(cropped, page.filter)

                // Save to disk
                val success = FileUtils.saveBitmap(enhanced, pageFile, quality = 92)
                if (success) {
                    processedPaths.add(pageFile.absolutePath)
                }

                // Clean up in-memory bitmaps
                if (bmp != cropped && !bmp.isRecycled) bmp.recycle()
                if (cropped != enhanced && !cropped.isRecycled) cropped.recycle()
                if (!enhanced.isRecycled) enhanced.recycle()
            }
        }

        withContext(Dispatchers.Main) {
            currentDocumentPagePaths.value = processedPaths
            if (processedPaths.isNotEmpty()) {
                // Set first page as preview
                viewModelScope.launch(Dispatchers.IO) {
                    val previewBmp = FileUtils.decodeSampledBitmap(processedPaths.first(), 800, 800)
                    withContext(Dispatchers.Main) {
                        enhancedBitmap.value = previewBmp
                    }
                }
            }
        }

        processedPaths
    }

    // -------------------------------------------------------------
    // Single / Legacy scan methods
    // -------------------------------------------------------------

    fun setCapturedImage(file: File) {
        capturedImageFile.value = file
        manualRotation.value = 0
        croppedBitmap.value = null
        enhancedBitmap.value = null
        currentEnhanceMode.value = EnhanceMode.Original
    }

    fun rotateImage() {
        manualRotation.value = (manualRotation.value + 90) % 360
    }

    fun setImageQueue(files: List<File>) {
        imageQueue.value = files
        queueTotalCount.value = files.size
        queueCurrentIndex.value = 0
        manualRotation.value = 0
        setScannedPages(files)
        if (files.isNotEmpty()) {
            capturedImageFile.value = files[0]
        }
    }

    fun processNextImageFromQueue(): Boolean {
        val nextIndex = queueCurrentIndex.value + 1
        return if (nextIndex < imageQueue.value.size) {
            queueCurrentIndex.value = nextIndex
            capturedImageFile.value = imageQueue.value[nextIndex]
            manualRotation.value = 0
            croppedBitmap.value = null
            enhancedBitmap.value = null
            currentEnhanceMode.value = EnhanceMode.Original
            true
        } else {
            false
        }
    }

    fun hasMoreImagesInQueue(): Boolean {
        return queueCurrentIndex.value < imageQueue.value.size - 1
    }

    fun applyCrop(
        topLeft: Offset,
        topRight: Offset,
        bottomLeft: Offset,
        bottomRight: Offset
    ) {
        val imageFile = capturedImageFile.value ?: return
        var bitmap = FileUtils.decodeSampledBitmap(imageFile.absolutePath, 2048, 2048) ?: return

        if (manualRotation.value != 0) {
            bitmap = FileUtils.rotateBitmap(bitmap, manualRotation.value, recycleSource = true)
        }

        val cropped = ImageProcessor.perspectiveTransform(
            bitmap,
            topLeft,
            topRight,
            bottomLeft,
            bottomRight
        )

        croppedBitmap.value = cropped
        enhancedBitmap.value = ImageProcessor.applyEnhancement(cropped, currentEnhanceMode.value)
    }

    fun applyEnhancement(mode: EnhanceMode) {
        val cropped = croppedBitmap.value ?: return
        currentEnhanceMode.value = mode
        enhancedBitmap.value = ImageProcessor.applyEnhancement(cropped, mode)
    }

    fun addPageToCurrentDocument(): String? {
        val bmp = enhancedBitmap.value ?: croppedBitmap.value ?: return null
        val docId = ensureDocumentId()

        val pageIndex = currentDocumentPagePaths.value.size
        val pageFile = FileUtils.createPageFile(getApplication(), docId, pageIndex)
        val success = FileUtils.saveBitmap(bmp, pageFile, quality = 92)

        return if (success) {
            val path = pageFile.absolutePath
            currentDocumentPagePaths.value = currentDocumentPagePaths.value + path
            path
        } else {
            null
        }
    }

    fun ensureDocumentId(): String {
        return currentDocumentId.value ?: UUID.randomUUID().toString().also {
            currentDocumentId.value = it
        }
    }

    fun createDocument(name: String, thumbnailPath: String): ScanDocument {
        val documentId = ensureDocumentId()
        val finalTitle = if (name.isNotBlank()) name else "Scan ${SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())}"

        val document = ScanDocument(
            id = documentId,
            name = finalTitle,
            thumbnailPath = thumbnailPath,
            createdAt = Date(),
            pageCount = currentDocumentPagePaths.value.size.coerceAtLeast(1),
            enhanceMode = currentEnhanceMode.value,
            pagePaths = currentDocumentPagePaths.value
        )

        val updatedList = listOf(document) + documents.value.filter { it.id != documentId }
        documents.value = updatedList
        FileUtils.saveDocuments(getApplication(), updatedList)

        return document
    }

    fun updateDocumentName(docId: String, newName: String) {
        val updated = documents.value.map {
            if (it.id == docId) it.copy(name = newName) else it
        }
        documents.value = updated
        FileUtils.saveDocuments(getApplication(), updated)
    }

    fun deleteDocument(docId: String) {
        val doc = documents.value.find { it.id == docId }
        if (doc != null) {
            FileUtils.deleteDocumentFiles(getApplication(), doc)
            val updated = documents.value.filter { it.id != docId }
            documents.value = updated
            FileUtils.saveDocuments(getApplication(), updated)
        }
    }

    fun loadDocument(id: String): ScanDocument? {
        val doc = documents.value.find { it.id == id } ?: return null
        currentDocumentId.value = doc.id
        currentDocumentTitle.value = doc.name
        currentDocumentPagePaths.value = doc.pagePaths

        viewModelScope.launch(Dispatchers.IO) {
            val previewPath = doc.pagePaths.firstOrNull() ?: doc.thumbnailPath
            if (previewPath != null) {
                val previewBmp = FileUtils.decodeSampledBitmap(previewPath, 1024, 1024)
                withContext(Dispatchers.Main) {
                    enhancedBitmap.value = previewBmp
                }
            }
        }
        return doc
    }

    fun reset() {
        scannedPages.value = emptyList()
        defaultFilter.value = EnhanceMode.Magic
        capturedImageFile.value = null
        manualRotation.value = 0
        croppedBitmap.value = null
        enhancedBitmap.value = null
        currentEnhanceMode.value = EnhanceMode.Original
        currentDocumentId.value = null
        currentDocumentTitle.value = ""
        currentDocumentPagePaths.value = emptyList()
        imageQueue.value = emptyList()
        queueTotalCount.value = 0
        queueCurrentIndex.value = 0
    }

    fun resetCurrentPage() {
        capturedImageFile.value = null
        manualRotation.value = 0
        croppedBitmap.value = null
        enhancedBitmap.value = null
        currentEnhanceMode.value = EnhanceMode.Original
    }

    fun getFinalBitmap(): Bitmap? {
        return enhancedBitmap.value ?: croppedBitmap.value
    }

    fun getAllPages(): List<Bitmap> {
        val bmp = enhancedBitmap.value ?: croppedBitmap.value
        return if (bmp != null) listOf(bmp) else emptyList()
    }

    fun getPagePaths(): List<String> {
        return currentDocumentPagePaths.value
    }

    fun getPageCount(): Int {
        return if (currentDocumentPagePaths.value.isNotEmpty()) {
            currentDocumentPagePaths.value.size
        } else {
            scannedPages.value.size
        }
    }
}
