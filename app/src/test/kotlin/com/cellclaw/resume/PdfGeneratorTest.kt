package com.cellclaw.resume

import com.cellclaw.jd.DefaultPdfTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class PdfGeneratorTest {

    private val generator = PdfGenerator()

    @Test
    fun `generate produces valid text-selectable PDF with correct naming and hashes`() {
        val resume = ResumeContent(
            name = "Asha Sharma",
            email = "asha@example.invalid",
            phone = "+91 9876543210",
            links = listOf("https://github.com/ashasharma", "https://linkedin.com/in/ashasharma"),
            sections = listOf(
                ResumeSection(
                    title = "Education",
                    lines = listOf(
                        ResumeLine("edu-1", "B.Tech in Computer Science, IIT Bombay (2026)", "fact-edu"),
                        ResumeLine("edu-2", "CGPA: 9.1/10.0", "fact-cgpa")
                    )
                ),
                ResumeSection(
                    title = "Skills",
                    lines = listOf(
                        ResumeLine("sk-1", "Kotlin, Jetpack Compose, Coroutines", "fact-sk1"),
                        ResumeLine("sk-2", "Android SDK, Room, Retrofit", "fact-sk2")
                    )
                ),
                ResumeSection(
                    title = "Projects",
                    lines = listOf(
                        ResumeLine("pr-1", "Placement Assistant: Autonomous Android tool using Compose and Keystore", "fact-pr1")
                    )
                )
            ),
            evidenceMap = EvidenceMap(
                mapOf(
                    "edu-1" to "fact-edu",
                    "edu-2" to "fact-cgpa",
                    "sk-1" to "fact-sk1",
                    "sk-2" to "fact-sk2",
                    "pr-1" to "fact-pr1"
                )
            )
        )

        val result = generator.generate(
            resume = resume,
            company = "Google",
            role = "Android Engineer",
            jdHash = "jd-hash-12345",
            version = 1
        )

        // 1. File naming: Name_Company_Role_vN.pdf
        assertEquals("AshaSharma_Google_AndroidEngineer_v1.pdf", result.fileName)
        assertEquals("jd-hash-12345", result.jdHash)

        // 2. Standard PDF header and footer
        assertTrue(result.bytes.size > 100)
        assertTrue(result.bytes.size < 50_000) // reasonable size < 50KB
        val pdfStr = String(result.bytes, StandardCharsets.ISO_8859_1)
        assertTrue(pdfStr.startsWith("%PDF-1.4"))
        assertTrue(pdfStr.trimEnd().endsWith("%%EOF"))

        // 3. SHA-256 matches actual byte digest
        assertEquals(64, result.sha256.length)
        val expectedSha = java.security.MessageDigest.getInstance("SHA-256")
            .digest(result.bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expectedSha, result.sha256)

        // 4. Text is extractable via DefaultPdfTextExtractor
        val extractor = DefaultPdfTextExtractor()
        val extractedText = extractor.extractText(ByteArrayInputStream(result.bytes))
        assertNotNull(extractedText)
        assertTrue(extractedText!!.contains("Asha Sharma"))
        assertTrue(extractedText.contains("B.Tech in Computer Science"))
        assertTrue(extractedText.contains("Kotlin"))
        assertTrue(extractedText.contains("Placement Assistant"))

        // 5. Valid link annotations present
        assertTrue(pdfStr.contains("/Subtype /Link"))
        assertTrue(pdfStr.contains("https://github.com/ashasharma"))

        // 6. Reasonable page count (1 page for this concise resume)
        assertEquals(1, result.pageCount)
    }

    @Test
    fun `handles Unicode punctuation and special characters cleanly`() {
        val resume = ResumeContent(
            name = "Ravi O'Connor",
            email = "ravi@example.invalid",
            sections = listOf(
                ResumeSection(
                    title = "Experience",
                    lines = listOf(
                        ResumeLine("line-1", "Built “Smart-AI” feature – reduced latency by 30% (verified)", "fact-1"),
                        ResumeLine("line-2", "Handled edge cases: special chars \\ and () safely", "fact-2")
                    )
                )
            )
        )

        val result = generator.generate(
            resume = resume,
            company = "Meta",
            role = "Software Engineer",
            jdHash = "jd-hash-special"
        )

        assertEquals("RaviOConnor_Meta_SoftwareEngineer_v1.pdf", result.fileName)
        val pdfStr = String(result.bytes, StandardCharsets.ISO_8859_1)
        assertTrue(pdfStr.contains("%%EOF"))

        val extractor = DefaultPdfTextExtractor()
        val extracted = extractor.extractText(ByteArrayInputStream(result.bytes))
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("Smart-AI"))
        assertTrue(extracted.contains("reduced latency"))
    }

    @Test
    fun `paginates long resumes cleanly across multiple pages without overflow`() {
        val longLines = (1..60).map { i ->
            ResumeLine("line-$i", "Detailed bullet point number $i explaining important achievements in engineering and development", "fact-$i")
        }

        val resume = ResumeContent(
            name = "Candidate With Many Experiences",
            email = "many@example.invalid",
            sections = listOf(
                ResumeSection(title = "Extensive Projects", lines = longLines)
            )
        )

        val result = generator.generate(
            resume = resume,
            company = "EnterpriseCorp",
            role = "Lead Architect",
            jdHash = "jd-hash-long"
        )

        // Must paginate to 2 or more pages without overflow
        assertTrue(result.pageCount >= 2)
        val pdfStr = String(result.bytes, StandardCharsets.ISO_8859_1)
        assertTrue(pdfStr.contains("/Count ${result.pageCount}"))
    }
}
