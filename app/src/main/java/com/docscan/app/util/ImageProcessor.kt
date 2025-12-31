package com.docscan.app.util

import android.graphics.*
import androidx.compose.ui.geometry.Offset
import com.docscan.app.model.EnhanceMode
import kotlin.math.max
import kotlin.math.sqrt

object ImageProcessor {
    
    /**
     * Apply perspective transform to crop image based on corner points
     */
    fun perspectiveTransform(
        bitmap: Bitmap,
        topLeft: Offset,
        topRight: Offset,
        bottomLeft: Offset,
        bottomRight: Offset
    ): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        
        // Convert normalized coordinates to pixel coordinates
        val srcPoints = floatArrayOf(
            topLeft.x * width, topLeft.y * height,
            topRight.x * width, topRight.y * height,
            bottomRight.x * width, bottomRight.y * height,
            bottomLeft.x * width, bottomLeft.y * height
        )
        
        // Calculate output dimensions
        val widthTop = distance(topLeft.x * width, topLeft.y * height, topRight.x * width, topRight.y * height)
        val widthBottom = distance(bottomLeft.x * width, bottomLeft.y * height, bottomRight.x * width, bottomRight.y * height)
        val outputWidth = max(widthTop, widthBottom).toInt()
        
        val heightLeft = distance(topLeft.x * width, topLeft.y * height, bottomLeft.x * width, bottomLeft.y * height)
        val heightRight = distance(topRight.x * width, topRight.y * height, bottomRight.x * width, bottomRight.y * height)
        val outputHeight = max(heightLeft, heightRight).toInt()
        
        // Destination points (rectangle)
        val dstPoints = floatArrayOf(
            0f, 0f,
            outputWidth.toFloat(), 0f,
            outputWidth.toFloat(), outputHeight.toFloat(),
            0f, outputHeight.toFloat()
        )
        
        // Create transformation matrix
        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        
        // Apply transformation
        return Bitmap.createBitmap(
            outputWidth,
            outputHeight,
            Bitmap.Config.ARGB_8888
        ).apply {
            val canvas = Canvas(this)
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
    
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Apply enhancement filter to image
     */
    fun applyEnhancement(bitmap: Bitmap, mode: EnhanceMode): Bitmap {
        return when (mode) {
            EnhanceMode.Original -> bitmap
            EnhanceMode.Grayscale -> applyGrayscale(bitmap)
            EnhanceMode.Color -> applyColorEnhancement(bitmap)
            EnhanceMode.BlackWhite -> applyBlackAndWhite(bitmap)
            EnhanceMode.Magic -> applyMagicEnhancement(bitmap)
        }
    }
    
    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyColorEnhancement(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint()
        val colorMatrix = ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 0f,
            0f, 1.2f, 0f, 0f, 0f,
            0f, 0f, 1.2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyBlackAndWhite(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint()
        
        // First convert to grayscale
        val grayscaleMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        
        // Then apply high contrast
        val contrastMatrix = ColorMatrix(floatArrayOf(
            2f, 0f, 0f, 0f, -128f,
            0f, 2f, 0f, 0f, -128f,
            0f, 0f, 2f, 0f, -128f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        grayscaleMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(grayscaleMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyMagicEnhancement(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint()
        
        // Enhance contrast and brightness
        val colorMatrix = ColorMatrix(floatArrayOf(
            1.5f, 0f, 0f, 0f, -20f,
            0f, 1.5f, 0f, 0f, -20f,
            0f, 0f, 1.5f, 0f, -20f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    /**
     * Create thumbnail from bitmap
     */
    fun createThumbnail(bitmap: Bitmap, maxSize: Int = 300): Bitmap {
        val ratio = maxSize.toFloat() / max(bitmap.width, bitmap.height)
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
