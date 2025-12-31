package com.docscan.app.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.docscan.app.model.EnhanceMode
import com.docscan.app.model.ScanDocument
import com.docscan.app.util.ImageProcessor
import java.io.File
import java.util.*

class ScanViewModel : ViewModel() {
    
    // Current captured image
    var capturedImageFile = mutableStateOf<File?>(null)
        private set
    
    // Cropped image
    var croppedBitmap = mutableStateOf<Bitmap?>(null)
        private set
    
    // Enhanced image
    var enhancedBitmap = mutableStateOf<Bitmap?>(null)
        private set
    
    // Current enhancement mode
    var currentEnhanceMode = mutableStateOf(EnhanceMode.Original)
        private set
    
    // List of scanned documents
    var documents = mutableStateOf<List<ScanDocument>>(emptyList())
        private set
    
    // Current document ID
    var currentDocumentId = mutableStateOf<String?>(null)
        private set
    
    fun setCapturedImage(file: File) {
        capturedImageFile.value = file
    }
    
    fun applyCrop(
        topLeft: Offset,
        topRight: Offset,
        bottomLeft: Offset,
        bottomRight: Offset
    ) {
        val imageFile = capturedImageFile.value ?: return
        var bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return
        
        // Apply rotation if needed
        val rotation = com.docscan.app.util.FileUtils.getImageRotation(imageFile)
        if (rotation != 0) {
            bitmap = com.docscan.app.util.FileUtils.rotateBitmap(bitmap, rotation)
        }
        
        val cropped = ImageProcessor.perspectiveTransform(
            bitmap,
            topLeft,
            topRight,
            bottomLeft,
            bottomRight
        )
        
        croppedBitmap.value = cropped
        enhancedBitmap.value = cropped // Initialize enhanced with cropped
    }
    
    fun applyEnhancement(mode: EnhanceMode) {
        val cropped = croppedBitmap.value ?: return
        currentEnhanceMode.value = mode
        
        // Apply enhancement in real-time
        val enhanced = ImageProcessor.applyEnhancement(cropped, mode)
        enhancedBitmap.value = enhanced
    }
    
    fun createDocument(name: String, thumbnailPath: String): String {
        val documentId = UUID.randomUUID().toString()
        val document = ScanDocument(
            id = documentId,
            name = name,
            thumbnailPath = thumbnailPath,
            createdAt = Date(),
            pageCount = 1,
            enhanceMode = currentEnhanceMode.value
        )
        
        documents.value = documents.value + document
        currentDocumentId.value = documentId
        return documentId
    }
    
    fun getDocument(id: String): ScanDocument? {
        return documents.value.find { it.id == id }
    }
    
    fun reset() {
        capturedImageFile.value = null
        croppedBitmap.value = null
        enhancedBitmap.value = null
        currentEnhanceMode.value = EnhanceMode.Original
        currentDocumentId.value = null
    }
    
    fun getFinalBitmap(): Bitmap? {
        return enhancedBitmap.value
    }
}
