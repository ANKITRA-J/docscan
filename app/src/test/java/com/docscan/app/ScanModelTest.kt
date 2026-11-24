package com.docscan.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.docscan.app.model.CornerType
import com.docscan.app.model.EnhanceMode
import com.docscan.app.model.QuadCorners
import com.docscan.app.model.ScanDocument
import com.docscan.app.model.ScannedPage
import com.docscan.app.ui.crop.findNearestCorner
import com.docscan.app.util.PdfGenerator
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Date

class ScanModelTest {

    @Test
    fun testScanDocumentJsonSerialization() {
        val original = ScanDocument(
            id = "test-doc-123",
            name = "Invoice 2026",
            thumbnailPath = "/path/to/thumb.jpg",
            createdAt = Date(1700000000000L),
            pageCount = 3,
            enhanceMode = EnhanceMode.Magic,
            pagePaths = listOf("/path/1.jpg", "/path/2.jpg", "/path/3.jpg"),
            pdfPath = "/path/to/doc.pdf"
        )

        val json = original.toJson()
        val parsed = ScanDocument.fromJson(json)

        assertEquals(original.id, parsed.id)
        assertEquals(original.name, parsed.name)
        assertEquals(original.thumbnailPath, parsed.thumbnailPath)
        assertEquals(original.createdAt.time, parsed.createdAt.time)
        assertEquals(3, parsed.pageCount)
        assertEquals(EnhanceMode.Magic, parsed.enhanceMode)
        assertEquals(3, parsed.pagePaths.size)
        assertEquals("/path/1.jpg", parsed.pagePaths[0])
        assertEquals("/path/to/doc.pdf", parsed.pdfPath)
    }

    @Test
    fun testQuadCornersAdjustment() {
        val corners = QuadCorners(
            topLeft = Offset(0.1f, 0.1f),
            topRight = Offset(0.9f, 0.1f),
            bottomLeft = Offset(0.1f, 0.9f),
            bottomRight = Offset(0.9f, 0.9f)
        )

        val adjusted = corners.adjustCorner(CornerType.TopLeft, Offset(0.2f, 0.2f))
        assertEquals(0.2f, adjusted.topLeft.x, 0.001f)
        assertEquals(0.2f, adjusted.topLeft.y, 0.001f)
        assertEquals(0.9f, adjusted.topRight.x, 0.001f)
    }

    @Test
    fun testFindNearestCorner() {
        val corners = QuadCorners(
            topLeft = Offset(0.1f, 0.1f),
            topRight = Offset(0.9f, 0.1f),
            bottomLeft = Offset(0.1f, 0.9f),
            bottomRight = Offset(0.9f, 0.9f)
        )
        val size = Size(1000f, 1000f)

        // Near top-left (110, 110 px)
        val nearestTL = findNearestCorner(Offset(110f, 110f), corners, size, threshold = 200f)
        assertEquals(CornerType.TopLeft, nearestTL)

        // Near bottom-right (890, 890 px)
        val nearestBR = findNearestCorner(Offset(890f, 890f), corners, size, threshold = 200f)
        assertEquals(CornerType.BottomRight, nearestBR)

        // Center of image - far from all corners
        val nearestFar = findNearestCorner(Offset(500f, 500f), corners, size, threshold = 50f)
        assertNull(nearestFar)
    }

    @Test
    fun testPdfPageFormats() {
        assertEquals(595, PdfGenerator.PageFormat.A4.widthPt)
        assertEquals(842, PdfGenerator.PageFormat.A4.heightPt)
        assertEquals(612, PdfGenerator.PageFormat.LETTER.widthPt)
        assertEquals(792, PdfGenerator.PageFormat.LETTER.heightPt)
    }

    @Test
    fun testScannedPageCreation() {
        val page = ScannedPage(
            originalFile = File("/tmp/test.jpg"),
            filter = EnhanceMode.BlackWhite
        )
        assertEquals(0, page.rotation)
        assertEquals(EnhanceMode.BlackWhite, page.filter)
        assertEquals(0.05f, page.corners.topLeft.x, 0.001f)
    }
}
