package com.docscan.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.docscan.app.model.ScanDocument
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

object FileUtils {

    private const val DOCUMENTS_INDEX_FILE = "scans_index.json"

    fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val storageDir = File(context.filesDir, "scans")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, "SCAN_${timeStamp}.jpg")
    }

    fun createPageFile(context: Context, docId: String, pageIndex: Int): File {
        val docDir = File(File(context.filesDir, "documents"), docId)
        if (!docDir.exists()) {
            docDir.mkdirs()
        }
        return File(docDir, "page_${pageIndex}.jpg")
    }

    fun getPdfDir(context: Context): File {
        val pdfDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "PDFs")
        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }
        return pdfDir
    }

    fun saveBitmap(
        bitmap: Bitmap,
        file: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 92
    ): Boolean {
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(format, quality, out)
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun deleteFile(file: File?): Boolean {
        return try {
            if (file != null && file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get the correct rotation for an image based on EXIF data
     */
    fun getImageRotation(file: File): Int {
        if (!file.exists() || file.length() == 0L) return 0
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Rotate bitmap based on degrees
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int, recycleSource: Boolean = false): Bitmap {
        if (degrees == 0) return bitmap
        return try {
            val matrix = Matrix()
            matrix.postRotate(degrees.toFloat())
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (recycleSource && rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            bitmap
        }
    }

    /**
     * Decode a bitmap with sensible inSampleSize and EXIF rotation, preventing OutOfMemory crashes
     */
    fun decodeSampledBitmap(
        filePath: String,
        reqWidth: Int = 2048,
        reqHeight: Int = 2048
    ): Bitmap? {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return null

        return try {
            // First decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            var decoded = BitmapFactory.decodeFile(filePath, options)
            if (decoded != null) {
                val rotation = getImageRotation(file)
                if (rotation != 0) {
                    decoded = rotateBitmap(decoded, rotation)
                }
            }
            decoded
        } catch (oom: OutOfMemoryError) {
            oom.printStackTrace()
            // Retry with larger inSampleSize
            try {
                val retryOptions = BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(filePath, retryOptions)
            } catch (e: Throwable) {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Save a file to the system Downloads/DocScan folder using MediaStore (Android 10+) or standard storage
     */
    fun saveToDownloads(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocScan")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, contentValues) ?: return null

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                uri
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val docScanDir = File(downloadsDir, "DocScan")
                if (!docScanDir.exists()) {
                    docScanDir.mkdirs()
                }
                val destFile = File(docScanDir, displayName)
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save persistent documents list to JSON
     */
    fun saveDocuments(context: Context, docs: List<ScanDocument>) {
        try {
            val file = File(context.filesDir, DOCUMENTS_INDEX_FILE)
            val jsonArray = JSONArray()
            docs.forEach { jsonArray.put(it.toJson()) }

            val tempFile = File(context.filesDir, "${DOCUMENTS_INDEX_FILE}.tmp")
            FileOutputStream(tempFile).use { out ->
                out.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
                out.flush()
            }
            if (tempFile.exists()) {
                tempFile.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Load persistent documents list from JSON
     */
    fun loadDocuments(context: Context): List<ScanDocument> {
        val file = File(context.filesDir, DOCUMENTS_INDEX_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val text = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(text)
            val list = mutableListOf<ScanDocument>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i)
                if (obj != null) {
                    list.add(ScanDocument.fromJson(obj))
                }
            }
            // Sort newest first
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Delete all files belonging to a document
     */
    fun deleteDocumentFiles(context: Context, doc: ScanDocument) {
        try {
            // Delete thumbnail
            doc.thumbnailPath?.let { deleteFile(File(it)) }
            // Delete PDF
            doc.pdfPath?.let { deleteFile(File(it)) }
            // Delete page files
            doc.pagePaths.forEach { deleteFile(File(it)) }
            // Delete document directory if exists
            val docDir = File(File(context.filesDir, "documents"), doc.id)
            if (docDir.exists()) {
                docDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
