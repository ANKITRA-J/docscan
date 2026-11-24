package com.docscan.app.util

import android.graphics.*
import androidx.compose.ui.geometry.Offset
import com.docscan.app.model.EnhanceMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageProcessor {

    /**
     * Apply perspective transform to crop image based on 4 corner points.
     * Safely bounds coordinates and handles non-convex / invalid quads with fallback.
     */
    fun perspectiveTransform(
        bitmap: Bitmap,
        topLeft: Offset,
        topRight: Offset,
        bottomLeft: Offset,
        bottomRight: Offset
    ): Bitmap {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return bitmap
        }

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        // Sanitize coordinates (ensure valid 0..1 range)
        val tl = sanitizeOffset(topLeft)
        val tr = sanitizeOffset(topRight)
        val bl = sanitizeOffset(bottomLeft)
        val br = sanitizeOffset(bottomRight)

        // Convert normalized coordinates to pixel coordinates
        val srcPoints = floatArrayOf(
            tl.x * width, tl.y * height,
            tr.x * width, tr.y * height,
            br.x * width, br.y * height,
            bl.x * width, bl.y * height
        )

        // Calculate output dimensions
        val widthTop = distance(tl.x * width, tl.y * height, tr.x * width, tr.y * height)
        val widthBottom = distance(bl.x * width, bl.y * height, br.x * width, br.y * height)
        val outputWidth = max(widthTop, widthBottom).toInt().coerceIn(100, 4096)

        val heightLeft = distance(tl.x * width, tl.y * height, bl.x * width, bl.y * height)
        val heightRight = distance(tr.x * width, tr.y * height, br.x * width, br.y * height)
        val outputHeight = max(heightLeft, heightRight).toInt().coerceIn(100, 4096)

        // Destination points (rectangle)
        val dstPoints = floatArrayOf(
            0f, 0f,
            outputWidth.toFloat(), 0f,
            outputWidth.toFloat(), outputHeight.toFloat(),
            0f, outputHeight.toFloat()
        )

        val matrix = Matrix()
        val polySuccess = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        if (!polySuccess) {
            // Fallback: simple axis-aligned rect crop if points are collinear or invalid
            val minX = (minOf(tl.x, bl.x) * width).toInt().coerceIn(0, bitmap.width - 1)
            val maxX = (maxOf(tr.x, br.x) * width).toInt().coerceIn(minX + 10, bitmap.width)
            val minY = (minOf(tl.y, tr.y) * height).toInt().coerceIn(0, bitmap.height - 1)
            val maxY = (maxOf(bl.y, br.y) * height).toInt().coerceIn(minY + 10, bitmap.height)
            return try {
                Bitmap.createBitmap(bitmap, minX, minY, maxX - minX, maxY - minY)
            } catch (e: Exception) {
                bitmap
            }
        }

        return try {
            val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                isDither = true
            }
            canvas.drawBitmap(bitmap, matrix, paint)
            output
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            // Fallback to simpler cropped bitmap if memory is tight
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun sanitizeOffset(offset: Offset): Offset {
        val x = if (offset.x.isNaN() || offset.x.isInfinite()) 0f else offset.x.coerceIn(0f, 1f)
        val y = if (offset.y.isNaN() || offset.y.isInfinite()) 0f else offset.y.coerceIn(0f, 1f)
        return Offset(x, y)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Apply enhancement filter to image safely without OOM or null/hardware config crashes
     */
    fun applyEnhancement(bitmap: Bitmap, mode: EnhanceMode): Bitmap {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return bitmap

        return when (mode) {
            EnhanceMode.Original -> bitmap
            EnhanceMode.Magic -> applyMagicEnhancement(bitmap)
            EnhanceMode.BlackWhite -> applyBlackAndWhite(bitmap)
            EnhanceMode.Grayscale -> applyGrayscale(bitmap)
            EnhanceMode.Color -> applyColorEnhancement(bitmap)
        }
    }

    private fun getSafeConfig(bitmap: Bitmap): Bitmap.Config {
        val config = bitmap.config
        return if (config == Bitmap.Config.HARDWARE) {
            Bitmap.Config.ARGB_8888
        } else {
            config
        }
    }

    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, getSafeConfig(bitmap))
            val canvas = Canvas(result)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val colorMatrix = ColorMatrix().apply {
                setSaturation(0f)
            }
            // Slight contrast enhancement for document legibility
            val contrastMatrix = ColorMatrix(floatArrayOf(
                1.15f, 0f, 0f, 0f, -10f,
                0f, 1.15f, 0f, 0f, -10f,
                0f, 0f, 1.15f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Throwable) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyColorEnhancement(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, getSafeConfig(bitmap))
            val canvas = Canvas(result)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val colorMatrix = ColorMatrix().apply {
                setSaturation(1.3f)
            }
            val contrastMatrix = ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 5f,
                0f, 1.1f, 0f, 0f, 5f,
                0f, 0f, 1.1f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Throwable) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyBlackAndWhite(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, getSafeConfig(bitmap))
            val canvas = Canvas(result)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            // Convert to grayscale first
            val grayMatrix = ColorMatrix().apply {
                setSaturation(0f)
            }

            // High contrast document thresholding (removes paper gray/shadows, deepens text ink)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                2.8f, 0f, 0f, 0f, -170f,
                0f, 2.8f, 0f, 0f, -170f,
                0f, 0f, 2.8f, 0f, -170f,
                0f, 0f, 0f, 1f, 0f
            ))

            grayMatrix.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(grayMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Throwable) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyMagicEnhancement(bitmap: Bitmap): Bitmap {
        return try {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, getSafeConfig(bitmap))
            val canvas = Canvas(result)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            // Boost saturation slightly and increase exposure + contrast for scanned doc look
            val satMatrix = ColorMatrix().apply {
                setSaturation(1.15f)
            }
            val magicMatrix = ColorMatrix(floatArrayOf(
                1.35f, 0f, 0f, 0f, 15f,
                0f, 1.35f, 0f, 0f, 15f,
                0f, 0f, 1.35f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            ))
            satMatrix.postConcat(magicMatrix)
            paint.colorFilter = ColorMatrixColorFilter(satMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Throwable) {
            e.printStackTrace()
            bitmap
        }
    }

    /**
     * Split a single photo of an open book into Left and Right pages down the vertical center.
     */
    fun splitBookPages(bitmap: Bitmap): Pair<Bitmap, Bitmap> {
        val width = bitmap.width
        val height = bitmap.height
        val halfWidth = (width / 2).coerceAtLeast(1)
        val left = Bitmap.createBitmap(bitmap, 0, 0, halfWidth, height)
        val right = Bitmap.createBitmap(bitmap, halfWidth, 0, width - halfWidth, height)
        return Pair(left, right)
    }

    /**
     * Crop the center area of an image corresponding to an ID Card aspect ratio (approx 1.586).
     */
    fun cropCenterCard(bitmap: Bitmap, aspect: Float = 1.586f): Bitmap {
        val imgWidth = bitmap.width.toFloat()
        val imgHeight = bitmap.height.toFloat()
        val imgAspect = imgWidth / imgHeight

        val cropWidth: Float
        val cropHeight: Float

        if (imgAspect > aspect) {
            cropHeight = imgHeight * 0.70f
            cropWidth = cropHeight * aspect
        } else {
            cropWidth = imgWidth * 0.85f
            cropHeight = cropWidth / aspect
        }

        val left = ((imgWidth - cropWidth) / 2f).toInt().coerceAtLeast(0)
        val top = ((imgHeight - cropHeight) / 2f).toInt().coerceAtLeast(0)
        val w = cropWidth.toInt().coerceIn(1, bitmap.width - left)
        val h = cropHeight.toInt().coerceIn(1, bitmap.height - top)

        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }

    /**
     * Merge Front and Back of an ID card onto a single standard A4 page.
     * Places Front centered in top half and Back centered in bottom half.
     */
    fun mergeIdCard(frontBitmap: Bitmap, backBitmap: Bitmap): Bitmap {
        val a4Width = 1654
        val a4Height = 2338
        val output = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val cardPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val targetWidth = (a4Width * 0.72f).toInt()

        // Front Card
        val frontRatio = targetWidth.toFloat() / frontBitmap.width.toFloat()
        val frontHeight = (frontBitmap.height * frontRatio).toInt()
        val frontLeft = (a4Width - targetWidth) / 2f
        val frontTop = ((a4Height / 2f - frontHeight) / 2f) + 40f
        val frontRect = RectF(frontLeft, frontTop, frontLeft + targetWidth, frontTop + frontHeight)

        canvas.drawText("FRONT", a4Width / 2f, frontTop - 24f, textPaint)
        canvas.drawBitmap(frontBitmap, null, frontRect, cardPaint)
        canvas.drawRoundRect(frontRect, 16f, 16f, borderPaint)

        // Back Card
        val backRatio = targetWidth.toFloat() / backBitmap.width.toFloat()
        val backHeight = (backBitmap.height * backRatio).toInt()
        val backLeft = (a4Width - targetWidth) / 2f
        val backTop = (a4Height / 2f) + ((a4Height / 2f - backHeight) / 2f) + 40f
        val backRect = RectF(backLeft, backTop, backLeft + targetWidth, backTop + backHeight)

        canvas.drawText("BACK", a4Width / 2f, backTop - 24f, textPaint)
        canvas.drawBitmap(backBitmap, null, backRect, cardPaint)
        canvas.drawRoundRect(backRect, 16f, 16f, borderPaint)

        return output
    }

    /**
     * Create thumbnail safely from bitmap
     */
    fun createThumbnail(bitmap: Bitmap, maxSize: Int = 300): Bitmap {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val safeMaxSize = maxSize.coerceAtLeast(64)
        val ratio = safeMaxSize.toFloat() / max(bitmap.width, bitmap.height)
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } catch (e: Throwable) {
            bitmap
        }
    }
}
