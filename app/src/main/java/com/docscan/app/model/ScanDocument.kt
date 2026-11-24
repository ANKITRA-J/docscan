package com.docscan.app.model

import org.json.JSONArray
import org.json.JSONObject
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
    val pagePaths: List<String> = emptyList(), // Paths to all page images
    val pdfPath: String? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("thumbnailPath", thumbnailPath ?: "")
        json.put("createdAt", createdAt.time)
        json.put("pageCount", pageCount)
        json.put("enhanceMode", enhanceMode.name)
        val pathsArray = JSONArray()
        pagePaths.forEach { pathsArray.put(it) }
        json.put("pagePaths", pathsArray)
        json.put("pdfPath", pdfPath ?: "")
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ScanDocument {
            val id = json.optString("id", "")
            val name = json.optString("name", "Document")
            val thumbnailPath = json.optString("thumbnailPath").takeIf { it.isNotEmpty() }
            val createdAt = Date(json.optLong("createdAt", System.currentTimeMillis()))
            val pageCount = json.optInt("pageCount", 1)
            val enhanceModeStr = json.optString("enhanceMode", EnhanceMode.Original.name)
            val enhanceMode = try {
                EnhanceMode.valueOf(enhanceModeStr)
            } catch (e: Exception) {
                EnhanceMode.Original
            }
            val pagePaths = mutableListOf<String>()
            val pathsArray = json.optJSONArray("pagePaths")
            if (pathsArray != null) {
                for (i in 0 until pathsArray.length()) {
                    val p = pathsArray.optString(i)
                    if (p.isNotEmpty()) {
                        pagePaths.add(p)
                    }
                }
            }
            val pdfPath = json.optString("pdfPath").takeIf { it.isNotEmpty() }
            return ScanDocument(
                id = id,
                name = name,
                thumbnailPath = thumbnailPath,
                createdAt = createdAt,
                pageCount = if (pagePaths.isNotEmpty()) pagePaths.size else pageCount,
                enhanceMode = enhanceMode,
                pagePaths = pagePaths,
                pdfPath = pdfPath
            )
        }
    }
}

/**
 * Enhancement modes for document processing
 */
enum class EnhanceMode(val displayName: String) {
    Original("Original"),
    Magic("Magic Color"),
    BlackWhite("Black & White"),
    Grayscale("Grayscale"),
    Color("Vibrant Color")
}
