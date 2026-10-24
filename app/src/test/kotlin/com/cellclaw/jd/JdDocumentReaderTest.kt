package com.cellclaw.jd

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class JdDocumentReaderTest {

    private val reader = JdDocumentReader()
    private val parser = JdParser()

    private fun createSyntheticDocx(paragraphs: List<String>): ByteArray {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        zos.putNextEntry(ZipEntry("word/document.xml"))
        val body = paragraphs.joinToString("\n") { "<w:p><w:r><w:t>$it</w:t></w:r></w:p>" }
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""
        zos.write(xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
        zos.close()
        return baos.toByteArray()
    }

    @Test
    fun `reads plain text document successfully`() {
        val text = "Company: TechCorp\nRole: Android Lead\nBatch: 2026\nMin CGPA: 8.0"
        val stream = ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8))

        val result = reader.read(stream, "text/plain", "jd.txt")
        assertTrue("Result should be Success", result is DocumentReadResult.Success)

        val success = result as DocumentReadResult.Success
        assertEquals("text/plain", success.mimeType)
        assertEquals(text, success.text)

        // Verify it parses cleanly with JdParser
        val req = parser.parse(success.text)
        assertEquals("TechCorp", req.company.value)
        assertEquals(8.0, req.minCgpa.value ?: 0.0, 0.001)
    }

    @Test
    fun `reads synthetic docx file with paragraphs and unescaping`() {
        val paragraphs = listOf(
            "Company: CloudSystems &amp; Co",
            "Role: Software Engineer",
            "Location: Hyderabad",
            "Skills: Kotlin, Java, Docker"
        )
        val docxBytes = createSyntheticDocx(paragraphs)
        val stream = ByteArrayInputStream(docxBytes)

        val result = reader.read(
            stream,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "placement_jd.docx"
        )
        assertTrue("Result should be Success", result is DocumentReadResult.Success)

        val success = result as DocumentReadResult.Success
        assertTrue(success.text.contains("Company: CloudSystems & Co"))
        assertTrue(success.text.contains("Skills: Kotlin, Java, Docker"))

        val req = parser.parse(success.text)
        assertEquals("CloudSystems & Co", req.company.value)
        assertEquals("Software Engineer", req.role.value)
    }

    @Test
    fun `empty text returns FallbackToPaste`() {
        val stream = ByteArrayInputStream(ByteArray(0))
        val result = reader.read(stream, "text/plain", "empty.txt")

        assertTrue("Expected FallbackToPaste", result is DocumentReadResult.FallbackToPaste)
        val fallback = result as DocumentReadResult.FallbackToPaste
        assertTrue(fallback.reason.contains("empty", ignoreCase = true))
    }

    @Test
    fun `scanned or image-only PDF returns FallbackToPaste with clear message`() {
        // Mock a PDF with only header and no text layer
        val pdfHeader = "%PDF-1.4\n%scanned binary stream raster only\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1)
        val stream = ByteArrayInputStream(pdfHeader)

        val result = reader.read(stream, "application/pdf", "scan.pdf")
        assertTrue("Expected FallbackToPaste for scanned PDF", result is DocumentReadResult.FallbackToPaste)

        val fallback = result as DocumentReadResult.FallbackToPaste
        assertTrue(fallback.reason.contains("scanned", ignoreCase = true) || fallback.reason.contains("No extractable text", ignoreCase = true))
    }

    @Test
    fun `PDF with text layer extracts text successfully using custom extractor`() {
        val customExtractor = PdfTextExtractor {
            "Company: InnoSoft\nRole: Backend Intern\nMin CGPA: 7.0\nDeadline: 2026-05-01"
        }
        val customReader = JdDocumentReader(customExtractor)
        val dummyPdfBytes = "%PDF-1.7\nsome dummy content".toByteArray(StandardCharsets.ISO_8859_1)

        val result = customReader.read(ByteArrayInputStream(dummyPdfBytes), "application/pdf", "inno.pdf")
        assertTrue("Expected Success", result is DocumentReadResult.Success)

        val success = result as DocumentReadResult.Success
        val req = parser.parse(success.text)
        assertEquals("InnoSoft", req.company.value)
        assertEquals(7.0, req.minCgpa.value ?: 0.0, 0.001)
    }

    @Test
    fun `malformed docx returns FallbackToPaste without crash`() {
        val corruptBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x12, 0x34) // Corrupt zip header
        val stream = ByteArrayInputStream(corruptBytes)

        val result = reader.read(stream, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "broken.docx")
        assertTrue("Expected FallbackToPaste for corrupted docx", result is DocumentReadResult.FallbackToPaste)
    }

    @Test
    fun `unsupported binary file returns FallbackToPaste`() {
        val randomBinary = byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01) // ELF binary header
        val stream = ByteArrayInputStream(randomBinary)

        val result = reader.read(stream, "application/octet-stream", "binary.bin")
        assertTrue("Expected FallbackToPaste for unsupported binary", result is DocumentReadResult.FallbackToPaste)
        val fallback = result as DocumentReadResult.FallbackToPaste
        assertTrue(fallback.reason.contains("Unsupported", ignoreCase = true))
    }
}
