package com.docscan.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.model.EnhanceMode
import com.docscan.app.model.ScanDocument
import com.docscan.app.util.FileUtils
import com.docscan.app.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    // Current captured or imported image file
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

    // All persistent scanned documents
    var documents = mutableStateOf<List<ScanDocument>>(emptyList())
        private set

    // Current document ID (created on first scan)
    var currentDocumentId = mutableStateOf<String?>(null)
        private set

    // Current document title
    var currentDocumentTitle = mutableStateOf("")
        private set

    // Saved page file paths on disk for current document (O(1) memory footprint)
    var currentDocumentPagePaths = mutableStateOf<List<String>>(emptyList())
        private set

    // Queue of images for multi-image import from gallery
    var imageQueue = mutableStateOf<List<File>>(emptyList())
        private set

    var queueTotalCount = mutableStateOf(0)
        private set

    var queueCurrentIndex = mutableStateOf(0)
        private set

    init {
        // Load persistent documents on startup
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
        if (files.isNotEmpty()) {
            capturedImageFile.value = files[0]
        }
    }

    /**
     * Advance to the next image in the queue. Returns true if advanced, false if queue is done.
     */
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

        // Decode safely with downsampling to prevent OOM
        var bitmap = FileUtils.decodeSampledBitmap(imageFile.absolutePath, 2048, 2048) ?: return

        // Apply manual rotation if set
        if (manualRotation.value != 0) {
            bitmap = FileUtils.rotateBitmap(bitmap, manualRotation.value)
        }

        val cropped = ImageProcessor.perspectiveTransform(
            bitmap,
            topLeft,
            topRight,
            bottomLeft,
            bottomRight
        )

        croppedBitmap.value = cropped
        // Apply current mode to cropped bitmap
        enhancedBitmap.value = ImageProcessor.applyEnhancement(cropped, currentEnhanceMode.value)
    }

    fun applyEnhancement(mode: EnhanceMode) {
        val cropped = croppedBitmap.value ?: return
        currentEnhanceMode.value = mode
        enhancedBitmap.value = ImageProcessor.applyEnhancement(cropped, mode)
    }

    /**
     * Commit current page to disk and add path to currentDocumentPagePaths.
     */
    fun addPageToCurrentDocument(): String? {
        val bmp = enhancedBitmap.value ?: croppedBitmap.value ?: return null
        val docId = currentDocumentId.value ?: UUID.randomUUID().toString().also {
            currentDocumentId.value = it
        }

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

        // Load preview for first page
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
        return currentDocumentPagePaths.value.size
    }
}
