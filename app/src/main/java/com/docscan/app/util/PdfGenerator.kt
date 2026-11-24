package com.docscan.app.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.itextpdf.kernel.pdf.EncryptionConstants
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.PdfDocument as ItextPdfDocument
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.WriterProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

/**
 * Robust PDF and Image export generator
 * Formats pages to standard document sizes (A4, Letter) with proper margins,
 * handles multi-page documents safely by streaming page-by-page from disk to prevent OOM.
 * Supports PDF compression, password protection (AES-128), and decryption/unlocking.
 */
object PdfGenerator {

    enum class PageFormat(val displayName: String, val widthPt: Int, val heightPt: Int) {
        A4("A4 (210 × 297 mm)", 595, 842),
        LETTER("Letter (8.5 × 11 in)", 612, 792),
        FIT_IMAGE("Fit to Image", 0, 0)
    }

    enum class CompressionLevel(val displayName: String, val maxDimension: Int, val quality: Int) {
        ORIGINAL("Original / High", 2480, 92),
        MEDIUM("Medium (~2 MB)", 1600, 75),
        LOW("Small / Low (~500 KB)", 1100, 55)
    }

    /**
     * Create a multi-page PDF from a list of image file paths with compression and optional password encryption.
     * Streams images one by one and recycles each bitmap immediately, preventing memory leaks and OOM crashes.
     */
    fun createPdfFromPagePaths(
        context: Context,
        pagePaths: List<String>,
        fileName: String? = null,
        pageFormat: PageFormat = PageFormat.A4,
        marginPt: Float = 24f,
        compression: CompressionLevel = CompressionLevel.ORIGINAL,
        password: String? = null
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
        val tempRawFile = if (!password.isNullOrBlank()) File(pdfDir, "raw_${System.currentTimeMillis()}_$cleanFileName") else pdfFile
        val pdfDocument = PdfDocument()
        var pagesAdded = 0

        return try {
            pagePaths.forEach { path ->
                // Decode sampled bitmap safely with compression dimension constraint
                val bitmap = FileUtils.decodeSampledBitmap(
                    path,
                    reqWidth = compression.maxDimension,
                    reqHeight = compression.maxDimension
                ) ?: return@forEach

                try {
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
                } finally {
                    bitmap.recycle()
                }
            }

            if (pagesAdded == 0) {
                return null
            }

            FileOutputStream(tempRawFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
            }

            if (!password.isNullOrBlank()) {
                val encrypted = encryptPdf(tempRawFile, pdfFile, password)
                tempRawFile.delete()
                if (!encrypted) return null
            }

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            FileUtils.deleteFile(tempRawFile)
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
     * Create a PDF from in-memory Bitmaps with compression and optional password
     */
    fun createPdfFromBitmaps(
        context: Context,
        bitmaps: List<Bitmap>,
        fileName: String? = null,
        pageFormat: PageFormat = PageFormat.A4,
        marginPt: Float = 24f,
        compression: CompressionLevel = CompressionLevel.ORIGINAL,
        password: String? = null
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
        val tempRawFile = if (!password.isNullOrBlank()) File(pdfDir, "raw_${System.currentTimeMillis()}_$cleanFileName") else pdfFile
        val pdfDocument = PdfDocument()
        var pagesAdded = 0

        return try {
            bitmaps.forEach { rawBitmap ->
                if (rawBitmap.isRecycled || rawBitmap.width <= 0 || rawBitmap.height <= 0) return@forEach

                val scaleToReq = min(
                    compression.maxDimension.toFloat() / rawBitmap.width.toFloat(),
                    compression.maxDimension.toFloat() / rawBitmap.height.toFloat()
                )
                val bitmap = if (scaleToReq < 1.0f) {
                    val w = (rawBitmap.width * scaleToReq).toInt().coerceAtLeast(1)
                    val h = (rawBitmap.height * scaleToReq).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(rawBitmap, w, h, true)
                } else {
                    rawBitmap
                }

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

            if (pagesAdded == 0) {
                return null
            }

            FileOutputStream(tempRawFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
            }

            if (!password.isNullOrBlank()) {
                val encrypted = encryptPdf(tempRawFile, pdfFile, password)
                tempRawFile.delete()
                if (!encrypted) return null
            }

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            FileUtils.deleteFile(tempRawFile)
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
     * Encrypt an existing PDF with user password using AES-128 standard encryption.
     */
    fun encryptPdf(srcFile: File, destFile: File, userPassword: String): Boolean {
        return try {
            val reader = PdfReader(srcFile)
            val writerProperties = WriterProperties()
                .setStandardEncryption(
                    userPassword.toByteArray(),
                    userPassword.toByteArray(),
                    EncryptionConstants.ALLOW_PRINTING or EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_128
                )
            val writer = PdfWriter(FileOutputStream(destFile), writerProperties)
            val itextDoc = ItextPdfDocument(reader, writer)
            itextDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Decrypt and remove password from an encrypted PDF, writing clean unlocked PDF.
     */
    fun decryptPdf(srcFile: File, destFile: File, userPassword: String): Boolean {
        return try {
            val readerProperties = ReaderProperties().setPassword(userPassword.toByteArray())
            val reader = PdfReader(srcFile.inputStream(), readerProperties)
            val writer = PdfWriter(FileOutputStream(destFile))
            val itextDoc = ItextPdfDocument(reader, writer)
            itextDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if a PDF is password protected / encrypted.
     */
    fun isPdfEncrypted(file: File): Boolean {
        return try {
            val reader = PdfReader(file)
            val doc = ItextPdfDocument(reader)
            val isEnc = reader.isEncrypted
            doc.close()
            isEnc
        } catch (e: com.itextpdf.kernel.exceptions.BadPasswordException) {
            true
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            msg.contains("password") || msg.contains("encrypt")
        }
    }

    /**
     * Check if the supplied password can unlock the encrypted PDF.
     */
    fun verifyPassword(file: File, password: String): Boolean {
        return try {
            val props = ReaderProperties().setPassword(password.toByteArray())
            val reader = PdfReader(file.inputStream(), props)
            val doc = ItextPdfDocument(reader)
            val count = doc.numberOfPages
            doc.close()
            count > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get page count of a PDF file, unlocking with password if protected.
     */
    fun getPdfPageCount(file: File, password: String? = null): Int {
        return try {
            val props = ReaderProperties()
            if (!password.isNullOrBlank()) {
                props.setPassword(password.toByteArray())
            }
            val reader = PdfReader(file.inputStream(), props)
            val doc = ItextPdfDocument(reader)
            val count = doc.numberOfPages
            doc.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Compress an existing PDF by re-sampling pages at a chosen compression level.
     */
    fun compressExistingPdf(
        srcFile: File,
        destFile: File,
        level: CompressionLevel,
        password: String? = null
    ): Boolean {
        var workFile = srcFile
        var tempDecrypted: File? = null
        try {
            if (!password.isNullOrBlank()) {
                tempDecrypted = File.createTempFile("decrypted_", ".pdf")
                if (decryptPdf(srcFile, tempDecrypted, password)) {
                    workFile = tempDecrypted
                }
            }

            val pfd = ParcelFileDescriptor.open(workFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val newDoc = PdfDocument()

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val scale = when (level) {
                    CompressionLevel.ORIGINAL -> 1.0f
                    CompressionLevel.MEDIUM -> 0.70f
                    CompressionLevel.LOW -> 0.45f
                }
                val renderWidth = (page.width * scale * 2).toInt().coerceAtLeast(100)
                val renderHeight = (page.height * scale * 2).toInt().coerceAtLeast(100)
                val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val byteStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, level.quality, byteStream)
                val compressedBytes = byteStream.toByteArray()
                bitmap.recycle()

                val compressedBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                val newPage = newDoc.startPage(pageInfo)
                newPage.canvas.drawColor(Color.WHITE)
                newPage.canvas.drawBitmap(compressedBmp, null, RectF(0f, 0f, page.width.toFloat(), page.height.toFloat()), null)
                newDoc.finishPage(newPage)
                compressedBmp.recycle()
            }

            renderer.close()
            pfd.close()

            FileOutputStream(destFile).use { out ->
                newDoc.writeTo(out)
                out.flush()
            }
            newDoc.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            tempDecrypted?.delete()
        }
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
