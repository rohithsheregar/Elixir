package com.cellclaw.jd

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of reading a JD document.
 */
sealed class DocumentReadResult {
    data class Success(val text: String, val mimeType: String) : DocumentReadResult()
    data class FallbackToPaste(val reason: String) : DocumentReadResult()
}

/**
 * Strategy interface for extracting text from PDF files.
 */
fun interface PdfTextExtractor {
    fun extractText(inputStream: InputStream): String?
}

/**
 * Pure JVM and Android compatible document reader for user-picked files (PDF, DOCX, TXT).
 * Gracefully falls back to "paste text" on empty, scanned, encrypted, or malformed files.
 */
@Singleton
class JdDocumentReader @Inject constructor(
    private val pdfExtractor: PdfTextExtractor = DefaultPdfTextExtractor()
) {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB limit
    }

    fun read(
        inputStream: InputStream,
        mimeType: String?,
        fileName: String? = null
    ): DocumentReadResult {
        return try {
            val bytes = readBoundedBytes(inputStream, MAX_FILE_SIZE_BYTES)
                ?: return DocumentReadResult.FallbackToPaste("File exceeds maximum allowed size (10 MB). Please paste the JD text instead.")

            if (bytes.isEmpty()) {
                return DocumentReadResult.FallbackToPaste("Document is empty. Please paste the JD text instead.")
            }

            val detectedType = detectType(mimeType, fileName, bytes)

            when (detectedType) {
                FileType.TXT -> readPlainText(bytes)
                FileType.DOCX -> readDocx(bytes)
                FileType.PDF -> readPdf(bytes)
                FileType.UNSUPPORTED -> DocumentReadResult.FallbackToPaste(
                    "Unsupported document type '${fileName ?: mimeType ?: "unknown"}'. Please upload a PDF, DOCX, or TXT file, or paste the text instead."
                )
            }
        } catch (e: Exception) {
            DocumentReadResult.FallbackToPaste("Could not read document (${e.message ?: "malformed file"}). Please paste the JD text instead.")
        }
    }

    private fun readPlainText(bytes: ByteArray): DocumentReadResult {
        val text = String(bytes, StandardCharsets.UTF_8).trim()
        return if (text.isBlank()) {
            DocumentReadResult.FallbackToPaste("Document contains no text. Please paste the JD text instead.")
        } else {
            DocumentReadResult.Success(text = text, mimeType = "text/plain")
        }
    }

    private fun readDocx(bytes: ByteArray): DocumentReadResult {
        return try {
            val zis = ZipInputStream(bytes.inputStream())
            var entry: ZipEntry? = zis.nextEntry
            var documentXml: String? = null

            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var read: Int
                    while (zis.read(buffer).also { read = it } != -1) {
                        baos.write(buffer, 0, read)
                    }
                    documentXml = baos.toString("UTF-8")
                    break
                }
                entry = zis.nextEntry
            }

            if (documentXml.isNullOrBlank()) {
                return DocumentReadResult.FallbackToPaste("Malformed or empty Word document. Please paste the JD text instead.")
            }

            val text = extractTextFromDocxXml(documentXml)
            if (text.isBlank()) {
                DocumentReadResult.FallbackToPaste("Word document contains no extractable text. Please paste the JD text instead.")
            } else {
                DocumentReadResult.Success(text = text, mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            }
        } catch (e: Exception) {
            DocumentReadResult.FallbackToPaste("Could not parse DOCX file (${e.message}). Please paste the JD text instead.")
        }
    }

    private fun extractTextFromDocxXml(xml: String): String {
        val sb = StringBuilder()
        val matcher = Pattern.compile("<w:t[^>]*>(.*?)</w:t>|<w:p[ >]").matcher(xml)
        while (matcher.find()) {
            val tag = matcher.group(0) ?: ""
            if (tag.startsWith("<w:p")) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                    sb.append("\n")
                }
            } else {
                val text = matcher.group(1)
                if (!text.isNullOrEmpty()) {
                    sb.append(unescapeXml(text))
                }
            }
        }
        return sb.toString().trim()
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun readPdf(bytes: ByteArray): DocumentReadResult {
        return try {
            val text = pdfExtractor.extractText(bytes.inputStream())
            if (text.isNullOrBlank()) {
                DocumentReadResult.FallbackToPaste("No extractable text layer found in PDF (scanned, image-only, or encrypted). Please paste the JD text instead.")
            } else {
                DocumentReadResult.Success(text = text.trim(), mimeType = "application/pdf")
            }
        } catch (e: Exception) {
            DocumentReadResult.FallbackToPaste("Failed to extract text from PDF (${e.message}). Please paste the JD text instead.")
        }
    }

    private fun detectType(mimeType: String?, fileName: String?, bytes: ByteArray): FileType {
        val lowerMime = mimeType?.lowercase() ?: ""
        val lowerName = fileName?.lowercase() ?: ""

        // Check magic bytes
        if (bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) {
            return FileType.PDF
        }
        // ZIP magic bytes PK\x03\x04
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            if ("wordprocessing" in lowerMime || lowerName.endsWith(".docx")) {
                return FileType.DOCX
            }
        }

        return when {
            "pdf" in lowerMime || lowerName.endsWith(".pdf") -> FileType.PDF
            "word" in lowerMime || "docx" in lowerMime || lowerName.endsWith(".docx") -> FileType.DOCX
            "text" in lowerMime || lowerName.endsWith(".txt") -> FileType.TXT
            // If mime is null/octet-stream and is valid UTF-8 text, treat as TXT
            isProbableText(bytes) -> FileType.TXT
            else -> FileType.UNSUPPORTED
        }
    }

    private fun isProbableText(bytes: ByteArray): Boolean {
        var asciiCount = 0
        val sampleSize = minOf(bytes.size, 512)
        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt()
            if (b == 0) return false // Binary null byte
            if (b in 32..126 || b == 9 || b == 10 || b == 13) {
                asciiCount++
            }
        }
        return (asciiCount.toDouble() / sampleSize) > 0.85
    }

    private fun readBoundedBytes(inputStream: InputStream, maxBytes: Int): ByteArray? {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0

        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            totalRead += read
            if (totalRead > maxBytes) {
                return null
            }
            baos.write(buffer, 0, read)
        }
        return baos.toByteArray()
    }

    private enum class FileType { TXT, DOCX, PDF, UNSUPPORTED }
}

/**
 * Default basic PDF text extractor.
 * Attempts text extraction from plain uncompressed text streams in PDF if present;
 * returns null for scanned, encrypted, or raster-only PDFs so caller falls back to paste.
 */
class DefaultPdfTextExtractor : PdfTextExtractor {
    override fun extractText(inputStream: InputStream): String? {
        val bytes = inputStream.readBytes()
        if (bytes.size < 4 || bytes[0] != 0x25.toByte() || bytes[1] != 0x50.toByte()) {
            return null
        }

        // Search for uncompressed BT (Begin Text) ... ET (End Text) blocks or Tj / TJ text tokens
        val content = String(bytes, StandardCharsets.ISO_8859_1)
        val matcher = Pattern.compile("\\(((?:[^()\\\\]|\\\\.)*)\\)\\s*Tj|\\[([^\\]]+)\\]\\s*TJ").matcher(content)
        val sb = StringBuilder()

        while (matcher.find()) {
            val tj = matcher.group(1)
            val tjArray = matcher.group(2)
            if (!tj.isNullOrEmpty()) {
                sb.append(cleanPdfText(tj)).append(" ")
            } else if (!tjArray.isNullOrEmpty()) {
                val subMatcher = Pattern.compile("\\(((?:[^()\\\\]|\\\\.)*)\\)").matcher(tjArray)
                while (subMatcher.find()) {
                    val subText = subMatcher.group(1)
                    if (!subText.isNullOrEmpty()) {
                        sb.append(cleanPdfText(subText)).append(" ")
                    }
                }
            }
        }

        val extracted = sb.toString().trim()
        return if (extracted.length > 20) extracted else null
    }

    private fun cleanPdfText(text: String): String {
        return text.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
    }
}
