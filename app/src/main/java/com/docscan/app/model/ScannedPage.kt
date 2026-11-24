package com.docscan.app.model

import androidx.compose.ui.geometry.Offset
import java.io.File
import java.util.UUID

enum class CornerType {
    TopLeft, TopRight, BottomLeft, BottomRight
}

data class QuadCorners(
    val topLeft: Offset = Offset(0.05f, 0.05f),
    val topRight: Offset = Offset(0.95f, 0.05f),
    val bottomLeft: Offset = Offset(0.05f, 0.95f),
    val bottomRight: Offset = Offset(0.95f, 0.95f)
) {
    fun adjustCorner(cornerType: CornerType, newOffset: Offset): QuadCorners {
        return when (cornerType) {
            CornerType.TopLeft -> copy(topLeft = newOffset)
            CornerType.TopRight -> copy(topRight = newOffset)
            CornerType.BottomLeft -> copy(bottomLeft = newOffset)
            CornerType.BottomRight -> copy(bottomRight = newOffset)
        }
    }

    companion object {
        val FULL = QuadCorners(
            topLeft = Offset(0f, 0f),
            topRight = Offset(1f, 0f),
            bottomLeft = Offset(0f, 1f),
            bottomRight = Offset(1f, 1f)
        )
    }
}

/**
 * Model representing an active scanned page before final export
 */
data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val originalFile: File,
    var corners: QuadCorners = QuadCorners(),
    var rotation: Int = 0,
    var filter: EnhanceMode = EnhanceMode.Magic,
    var processedPath: String? = null
)
