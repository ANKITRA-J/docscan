package com.docscan.app.model

import java.util.Date

/**
 * Data model for scanned documents
 */
data class ScanDocument(
    val id: String,
    val name: String,
    val thumbnailPath: String? = null,
    val createdAt: Date = Date(),
    val pageCount: Int = 1,
    val enhanceMode: EnhanceMode = EnhanceMode.Original,
    val pagePaths: List<String> = emptyList() // Paths to all page images
)

/**
 * Enhancement modes for document processing
 */
enum class EnhanceMode(val displayName: String) {
    Original("Original"),
    Grayscale("Grayscale"),
    Color("Color"),
    BlackWhite("Black & White"),
    Magic("Magic")
}

