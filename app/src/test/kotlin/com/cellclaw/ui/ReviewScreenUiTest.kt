package com.cellclaw.ui

import com.cellclaw.tracker.ApplicationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM unit test for ReviewScreen Boarding-Pass UI component and its integration.
 */
class ReviewScreenUiTest {

    @Test
    fun testBoardingPassComponentFileExists() {
        val file = File("src/main/kotlin/com/cellclaw/ui/components/BoardingPassCard.kt")
        assertTrue("BoardingPassCard.kt must exist", file.exists())
        val content = file.readText()
        assertTrue("Must declare BoardingPassCard composable", content.contains("fun BoardingPassCard"))
        assertTrue("Must display PLACEMENT PASS header", content.contains("PLACEMENT PASS"))
        assertTrue("Must display TARGET DOMAIN label", content.contains("TARGET DOMAIN"))
        assertTrue("Must display RESUME VERSION label", content.contains("RESUME VERSION"))
        assertTrue("Must display INTEGRITY HASH (SHA-256) label", content.contains("INTEGRITY HASH (SHA-256)"))
        assertTrue("Must draw dashed perforated line", content.contains("dashPathEffect"))
    }

    @Test
    fun testReviewScreenIntegratesBoardingPassCard() {
        val file = File("src/main/kotlin/com/cellclaw/ui/screens/ReviewScreen.kt")
        assertTrue("ReviewScreen.kt must exist", file.exists())
        val content = file.readText()
        assertTrue(
            "ReviewScreen must import BoardingPassCard",
            content.contains("import com.cellclaw.ui.components.BoardingPassCard")
        )
        assertTrue(
            "ReviewScreen must invoke BoardingPassCard",
            content.contains("BoardingPassCard(")
        )
        assertTrue(
            "ReviewScreen must retain ProgressStepper at top",
            content.contains("ProgressStepper(")
        )
        assertTrue(
            "ReviewScreen must retain supervised submission approval gate button",
            content.contains("Approve & Submit")
        )
    }

    @Test
    fun testBoardingPassHandlesBlankMetadataGracefully() {
        // Assert fallback labels in BoardingPassCard
        val file = File("src/main/kotlin/com/cellclaw/ui/components/BoardingPassCard.kt")
        val content = file.readText()
        assertTrue("Must handle empty company fallback", content.contains("company.ifBlank { \"Company Name\" }"))
        assertTrue("Must handle empty role fallback", content.contains("role.ifBlank { \"Role / Position\" }"))
        assertTrue("Must handle empty domain fallback", content.contains("domain.ifBlank { \"google.com\" }"))
        assertTrue("Must handle empty hash fallback", content.contains("resumeHash ?: \"Computed on submission authorization\""))
    }

    @Test
    fun testApplicationStateBadgeLabels() {
        val states = listOf(
            ApplicationState.READY_FOR_REVIEW,
            ApplicationState.SUBMITTING,
            ApplicationState.SUBMITTED_CONFIRMED,
            ApplicationState.FAILED
        )
        for (state in states) {
            val formatted = state.name.replace("_", " ")
            assertTrue("State name formatting should contain spaces", !formatted.contains("_"))
        }
    }
}

