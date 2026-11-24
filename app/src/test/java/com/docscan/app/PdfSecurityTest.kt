package com.docscan.app

import com.docscan.app.util.PdfGenerator
import com.itextpdf.kernel.pdf.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File

class PdfSecurityTest {

    @Test
    fun testPdfEncryption() {
        val outStream = ByteArrayOutputStream()
        val writerProperties = WriterProperties()
            .setStandardEncryption(
                "user123".toByteArray(),
                "owner123".toByteArray(),
                EncryptionConstants.ALLOW_PRINTING,
                EncryptionConstants.ENCRYPTION_AES_128
            )
        val writer = PdfWriter(outStream, writerProperties)
        val pdfDoc = PdfDocument(writer)
        pdfDoc.addNewPage()
        pdfDoc.close()
        val bytes = outStream.toByteArray()
        assert(bytes.isNotEmpty())

        // Try reading with password
        val readerProps = ReaderProperties().setPassword("user123".toByteArray())
        val inStream = ByteArrayInputStream(bytes)
        val reader = PdfReader(inStream, readerProps)
        val readPdf = PdfDocument(reader)
        assert(readPdf.numberOfPages == 1)
        readPdf.close()
    }

    @Test
    fun testPdfGeneratorSecurityWorkflow() {
        val tempRaw = File.createTempFile("test_raw_", ".pdf")
        val tempEnc = File.createTempFile("test_enc_", ".pdf")
        val tempDec = File.createTempFile("test_dec_", ".pdf")

        // 1. Create raw unencrypted PDF
        val writer = PdfWriter(tempRaw)
        val doc = PdfDocument(writer)
        doc.addNewPage()
        doc.close()

        assert(!PdfGenerator.isPdfEncrypted(tempRaw))

        // 2. Encrypt with password "secret123"
        val encSuccess = PdfGenerator.encryptPdf(tempRaw, tempEnc, "secret123")
        assert(encSuccess)
        assert(PdfGenerator.isPdfEncrypted(tempEnc))
        assert(PdfGenerator.verifyPassword(tempEnc, "secret123"))
        assert(!PdfGenerator.verifyPassword(tempEnc, "wrongPassword"))

        // 3. Decrypt and remove password
        val decSuccess = PdfGenerator.decryptPdf(tempEnc, tempDec, "secret123")
        assert(decSuccess)
        assert(!PdfGenerator.isPdfEncrypted(tempDec))
        assert(PdfGenerator.getPdfPageCount(tempDec) == 1)

        tempRaw.delete()
        tempEnc.delete()
        tempDec.delete()
    }
}
