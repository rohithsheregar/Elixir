package com.cellclaw.resume

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class GeneratedPdf(
    val fileName: String,
    val bytes: ByteArray,
    val sha256: String,
    val jdHash: String,
    val pageCount: Int
)

/**
 * Pure JVM PDF generator producing text-selectable, A4 compliant PDFs.
 * Does not depend on the Android SDK or external libraries.
 */
class PdfGenerator {

    companion object {
        const val PAGE_WIDTH = 595 // A4 width in pt
        const val PAGE_HEIGHT = 842 // A4 height in pt
        const val MARGIN_LEFT = 50
        const val MARGIN_RIGHT = 545
        const val MARGIN_TOP = 800
        const val MARGIN_BOTTOM = 50
        const val USABLE_WIDTH = MARGIN_RIGHT - MARGIN_LEFT // 495 pt
    }

    fun generate(
        resume: ResumeContent,
        company: String,
        role: String,
        jdHash: String,
        version: Int = 1
    ): GeneratedPdf {
        val safeName = sanitize(resume.name)
        val safeCompany = sanitize(company)
        val safeRole = sanitize(role)
        val fileName = "${safeName}_${safeCompany}_${safeRole}_v$version.pdf"

        val layout = paginateResume(resume)
        val pdfBytes = renderPdf(layout, resume)
        val sha256 = computeSha256(pdfBytes)

        return GeneratedPdf(
            fileName = fileName,
            bytes = pdfBytes,
            sha256 = sha256,
            jdHash = jdHash,
            pageCount = layout.size
        )
    }

    private data class TextItem(
        val text: String,
        val font: String, // F1 (Regular), F2 (Bold)
        val size: Int,
        val x: Int,
        val y: Int,
        val linkUri: String? = null
    )

    private data class PageLayout(
        val items: List<TextItem>
    )

    private fun paginateResume(resume: ResumeContent): List<PageLayout> {
        val pages = mutableListOf<PageLayout>()
        var currentItems = mutableListOf<TextItem>()
        var y = MARGIN_TOP

        fun checkPageBreak(neededHeight: Int) {
            if (y - neededHeight < MARGIN_BOTTOM) {
                pages.add(PageLayout(currentItems))
                currentItems = mutableListOf()
                y = MARGIN_TOP
            }
        }

        // Header: Name (Bold 16pt)
        currentItems.add(
            TextItem(
                text = sanitizeText(resume.name),
                font = "F2",
                size = 16,
                x = MARGIN_LEFT,
                y = y
            )
        )
        y -= 22

        // Contact Info (Regular 10pt)
        val contactParts = mutableListOf<String>()
        contactParts.add(resume.email)
        resume.phone?.let { contactParts.add(it) }
        contactParts.addAll(resume.links)
        val contactText = contactParts.joinToString(" | ")

        for (wrappedLine in wrapText(contactText, 80)) {
            checkPageBreak(14)
            currentItems.add(
                TextItem(
                    text = sanitizeText(wrappedLine),
                    font = "F1",
                    size = 10,
                    x = MARGIN_LEFT,
                    y = y,
                    linkUri = resume.links.firstOrNull { wrappedLine.contains(it) }
                )
            )
            y -= 14
        }
        y -= 10 // separator spacing

        // Sections
        for (section in resume.sections) {
            checkPageBreak(30)
            // Section Title (Bold 12pt)
            currentItems.add(
                TextItem(
                    text = sanitizeText(section.title.uppercase()),
                    font = "F2",
                    size = 12,
                    x = MARGIN_LEFT,
                    y = y
                )
            )
            y -= 16

            for (line in section.lines) {
                val bulletPrefix = "• "
                val wrapped = wrapText(line.text, 75)
                for ((idx, wLine) in wrapped.withIndex()) {
                    checkPageBreak(13)
                    val lineText = if (idx == 0) "$bulletPrefix$wLine" else "  $wLine"
                    currentItems.add(
                        TextItem(
                            text = sanitizeText(lineText),
                            font = "F1",
                            size = 10,
                            x = MARGIN_LEFT,
                            y = y
                        )
                    )
                    y -= 13
                }
            }
            y -= 8
        }

        if (currentItems.isNotEmpty() || pages.isEmpty()) {
            pages.add(PageLayout(currentItems))
        }

        return pages
    }

    private fun renderPdf(pages: List<PageLayout>, resume: ResumeContent): ByteArray {
        val baos = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        fun write(str: String) {
            baos.write(str.toByteArray(StandardCharsets.ISO_8859_1))
        }

        // Header
        write("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        val totalObjects = 4 + (pages.size * 2)
        val catalogObj = 1
        val pagesObj = 2
        val fontRegularObj = 3
        val fontBoldObj = 4

        // Obj 1: Catalog
        offsets.add(baos.size())
        write("$catalogObj 0 obj\n<< /Type /Catalog /Pages $pagesObj 0 R >>\nendobj\n")

        // Obj 2: Pages
        offsets.add(baos.size())
        val pageObjNumbers = pages.indices.map { 5 + (it * 2) }
        val kidsStr = pageObjNumbers.joinToString(" ") { "$it 0 R" }
        write("$pagesObj 0 obj\n<< /Type /Pages /Kids [ $kidsStr ] /Count ${pages.size} >>\nendobj\n")

        // Obj 3: Font Regular (Helvetica)
        offsets.add(baos.size())
        write("$fontRegularObj 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")

        // Obj 4: Font Bold (Helvetica-Bold)
        offsets.add(baos.size())
        write("$fontBoldObj 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")

        // Page and Content objects
        for ((idx, page) in pages.withIndex()) {
            val pageNum = 5 + (idx * 2)
            val contentNum = pageNum + 1

            // Build annotations for links
            val linkAnnots = mutableListOf<String>()
            for (item in page.items) {
                if (item.linkUri != null) {
                    val rect = "${item.x} ${item.y - 2} ${item.x + (item.text.length * 6)} ${item.y + item.size}"
                    linkAnnots.add("<< /Type /Annot /Subtype /Link /Rect [ $rect ] /Border [0 0 0] /A << /S /URI /URI (${escapePdfString(item.linkUri)}) >> >>")
                }
            }
            val annotsEntry = if (linkAnnots.isNotEmpty()) "/Annots [ ${linkAnnots.joinToString(" ")} ]" else ""

            // Page Object
            offsets.add(baos.size())
            write("$pageNum 0 obj\n<< /Type /Page /Parent $pagesObj 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] /Resources << /Font << /F1 $fontRegularObj 0 R /F2 $fontBoldObj 0 R >> >> /Contents $contentNum 0 R $annotsEntry >>\nendobj\n")

            // Stream Content
            val streamContent = buildString {
                for (item in page.items) {
                    append("BT\n")
                    append("/${item.font} ${item.size} Tf\n")
                    append("${item.x} ${item.y} Td\n")
                    append("(${escapePdfString(item.text)}) Tj\n")
                    append("ET\n")
                }
            }

            val streamBytes = streamContent.toByteArray(StandardCharsets.ISO_8859_1)
            offsets.add(baos.size())
            write("$contentNum 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n")
            baos.write(streamBytes)
            write("\nendstream\nendobj\n")
        }

        // Xref Table
        val startXref = baos.size()
        write("xref\n0 ${totalObjects + 1}\n")
        write("0000000000 65535 f \n")
        for (offset in offsets) {
            write(String.format("%010d 00000 n \n", offset))
        }

        // Trailer
        write("trailer\n<< /Size ${totalObjects + 1} /Root $catalogObj 0 R >>\nstartxref\n$startXref\n%%EOF\n")

        return baos.toByteArray()
    }

    private fun wrapText(text: String, maxCharsPerLine: Int): List<String> {
        if (text.length <= maxCharsPerLine) return listOf(text)

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            if (current.length + word.length + 1 > maxCharsPerLine && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(word)
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    private fun sanitizeText(input: String): String {
        return input
            .replace("–", "-")
            .replace("—", "-")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("‘", "'")
            .replace("’", "'")
            .replace("•", "*")
            .replace("\r", "")
            .replace("\n", " ")
    }

    private fun escapePdfString(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
    }

    private fun sanitize(input: String): String {
        val cleaned = input.replace(Regex("[^A-Za-z0-9]"), "")
        return cleaned.ifBlank { "Unknown" }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun computePageCount(resume: ResumeContent): Int {
        return paginateResume(resume).size
    }
}
