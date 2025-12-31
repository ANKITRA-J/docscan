package com.docscan.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {
    
    fun createPdfFromBitmaps(
        context: Context,
        bitmaps: List<Bitmap>,
        fileName: String? = null
    ): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val pdfFileName = fileName ?: "Document_${timeStamp}.pdf"
            
            val pdfDir = File(context.getExternalFilesDir(null), "PDFs")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }
            
            val pdfFile = File(pdfDir, pdfFileName)
            
            val pdfDocument = PdfDocument()
            
            bitmaps.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)
            }
            
            FileOutputStream(pdfFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            
            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
            val imageFileName = fileName ?: "Document_${timeStamp}.jpg"
            
            val imageDir = File(context.getExternalFilesDir(null), "Images")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }
            
            val imageFile = File(imageDir, imageFileName)
            
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            
            imageFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
