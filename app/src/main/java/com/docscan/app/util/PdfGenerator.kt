package com.docscan.app.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

/**
 * Robust PDF and Image export generator
 * Formats pages to standard document sizes (A4, Letter) with proper margins,
 * handles multi-page documents safely by streaming page-by-page from disk to prevent OOM.
 */
object PdfGenerator {

    enum class PageFormat(val displayName: String, val widthPt: Int, val heightPt: Int) {
        A4("A4 (210 × 297 mm)", 595, 842),
        LETTER("Letter (8.5 × 11 in)", 612, 792),
        FIT_IMAGE("Fit to Image", 0, 0)
    }

    /**
     * Create a multi-page PDF from a list of image file paths.
     * Streams images one by one and recycles each bitmap immediately, preventing memory leaks and OOM crashes.
     */
    fun createPdfFromPagePaths(
        context: Context,
        pagePaths: List<String>,
        fileName: String? = null,
        pageFormat: PageFormat = PageFormat.A4,
        marginPt: Float = 24f
    ): File? {
        if (pagePaths.isEmpty()) return null

        val pdfDir = FileUtils.getPdfDir(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanFileName = if (!fileName.isNullOrBlank()) {
            if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "${fileName}.pdf"
        } else {
            "Scan_${timeStamp}.pdf"
        }

        val pdfFile = File(pdfDir, cleanFileName)
        val pdfDocument = PdfDocument()
        var pagesAdded = 0

        return try {
            pagePaths.forEach { path ->
                // Decode sampled bitmap safely for this page (max 2480x3508 for ~300 DPI A4)
                val bitmap = FileUtils.decodeSampledBitmap(path, reqWidth = 2480, reqHeight = 3508)
                    ?: return@forEach

                try {
                    val isLandscape = bitmap.width > bitmap.height

                    val pageWidth: Int
                    val pageHeight: Int

                    if (pageFormat == PageFormat.FIT_IMAGE) {
                        // Scale to printable points (~150 DPI)
                        val scale = 72f / 150f
                        pageWidth = (bitmap.width * scale).toInt().coerceAtLeast(200)
                        pageHeight = (bitmap.height * scale).toInt().coerceAtLeast(200)
                    } else {
                        // Standard page format (A4 / Letter), matching orientation
                        if (isLandscape) {
                            pageWidth = pageFormat.heightPt
                            pageHeight = pageFormat.widthPt
                        } else {
                            pageWidth = pageFormat.widthPt
                            pageHeight = pageFormat.heightPt
                        }
                    }

                    val pageNumber = pagesAdded + 1
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // Fill background with clean white
                    canvas.drawColor(Color.WHITE)

                    // Calculate destination rect preserving aspect ratio inside margins
                    val effectiveMargin = if (pageFormat == PageFormat.FIT_IMAGE) 0f else marginPt
                    val availWidth = (pageWidth - 2 * effectiveMargin).coerceAtLeast(10f)
                    val availHeight = (pageHeight - 2 * effectiveMargin).coerceAtLeast(10f)

                    val scaleFactor = min(
                        availWidth / bitmap.width.toFloat(),
                        availHeight / bitmap.height.toFloat()
                    )

                    val destWidth = bitmap.width.toFloat() * scaleFactor
                    val destHeight = bitmap.height.toFloat() * scaleFactor

                    val destLeft = effectiveMargin + (availWidth - destWidth) / 2f
                    val destTop = effectiveMargin + (availHeight - destHeight) / 2f
                    val destRect = RectF(destLeft, destTop, destLeft + destWidth, destTop + destHeight)

                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                        isDither = true
                    }

                    canvas.drawBitmap(bitmap, null, destRect, paint)
                    pdfDocument.finishPage(page)
                    pagesAdded++
                } finally {
                    bitmap.recycle()
                }
            }

            if (pagesAdded == 0) {
                return null
            }

            FileOutputStream(pdfFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
            }

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            FileUtils.deleteFile(pdfFile)
            null
        } finally {
            try {
                pdfDocument.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Create a PDF from in-memory Bitmaps with proper formatting and page scaling
     */
    fun createPdfFromBitmaps(
        context: Context,
        bitmaps: List<Bitmap>,
        fileName: String? = null,
        pageFormat: PageFormat = PageFormat.A4,
        marginPt: Float = 24f
    ): File? {
        if (bitmaps.isEmpty()) return null

        val pdfDir = FileUtils.getPdfDir(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanFileName = if (!fileName.isNullOrBlank()) {
            if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "${fileName}.pdf"
        } else {
            "Scan_${timeStamp}.pdf"
        }

        val pdfFile = File(pdfDir, cleanFileName)
        val pdfDocument = PdfDocument()
        var pagesAdded = 0

        return try {
            bitmaps.forEach { bitmap ->
                if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return@forEach

                val isLandscape = bitmap.width > bitmap.height

                val pageWidth: Int
                val pageHeight: Int

                if (pageFormat == PageFormat.FIT_IMAGE) {
                    val scale = 72f / 150f
                    pageWidth = (bitmap.width * scale).toInt().coerceAtLeast(200)
                    pageHeight = (bitmap.height * scale).toInt().coerceAtLeast(200)
                } else {
                    if (isLandscape) {
                        pageWidth = pageFormat.heightPt
                        pageHeight = pageFormat.widthPt
                    } else {
                        pageWidth = pageFormat.widthPt
                        pageHeight = pageFormat.heightPt
                    }
                }

                val pageNumber = pagesAdded + 1
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)

                val effectiveMargin = if (pageFormat == PageFormat.FIT_IMAGE) 0f else marginPt
                val availWidth = (pageWidth - 2 * effectiveMargin).coerceAtLeast(10f)
                val availHeight = (pageHeight - 2 * effectiveMargin).coerceAtLeast(10f)

                val scaleFactor = min(
                    availWidth / bitmap.width.toFloat(),
                    availHeight / bitmap.height.toFloat()
                )

                val destWidth = bitmap.width.toFloat() * scaleFactor
                val destHeight = bitmap.height.toFloat() * scaleFactor

                val destLeft = effectiveMargin + (availWidth - destWidth) / 2f
                val destTop = effectiveMargin + (availHeight - destHeight) / 2f
                val destRect = RectF(destLeft, destTop, destLeft + destWidth, destTop + destHeight)

                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    isDither = true
                }

                canvas.drawBitmap(bitmap, null, destRect, paint)
                pdfDocument.finishPage(page)
                pagesAdded++
            }

            if (pagesAdded == 0) return null

            FileOutputStream(pdfFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
            }

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            FileUtils.deleteFile(pdfFile)
            null
        } finally {
            try {
                pdfDocument.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createPdfFromBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String? = null
    ): File? {
        return createPdfFromBitmaps(context, listOf(bitmap), fileName)
    }

    fun saveImageAsJpeg(
        context: Context,
        bitmap: Bitmap,
        fileName: String? = null
    ): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFileName = if (!fileName.isNullOrBlank()) {
                if (fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true)) {
                    fileName
                } else {
                    "${fileName}.jpg"
                }
            } else {
                "Scan_${timeStamp}.jpg"
            }

            val imageDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Images")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }

            val imageFile = File(imageDir, imageFileName)
            val success = FileUtils.saveBitmap(bitmap, imageFile, quality = 95)
            if (success) imageFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
