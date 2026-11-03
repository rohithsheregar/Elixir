package com.cellclaw.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM unit test suite verifying the EmptyState component implementation and its
 * integration across TrackerScreen and ProfileScreen.
 */
class EmptyStateComponentTest {

    @Test
    fun testEmptyStateComponentFileExistsAndStructured() {
        val file = File("src/main/kotlin/com/cellclaw/ui/components/EmptyState.kt")
        assertTrue("EmptyState.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("Must declare EmptyState composable", content.contains("fun EmptyState("))
        assertTrue("Must accept icon parameter", content.contains("icon: ImageVector"))
        assertTrue("Must accept title parameter", content.contains("title: String"))
        assertTrue("Must accept description parameter", content.contains("description: String"))
        assertTrue("Must accept optional actionLabel", content.contains("actionLabel: String?"))
        assertTrue("Must accept optional onAction", content.contains("onAction: (() -> Unit)?"))
        assertTrue("Must accept optional tip parameter", content.contains("tip: String?"))
        assertTrue("Must render circular illustration badge", content.contains("CircleShape"))
    }

    @Test
    fun testTrackerScreenIntegratesEmptyState() {
        val file = File("src/main/kotlin/com/cellclaw/ui/screens/TrackerScreen.kt")
        assertTrue("TrackerScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "TrackerScreen must import EmptyState",
            content.contains("import com.cellclaw.ui.components.EmptyState")
        )
        assertTrue(
            "TrackerScreen must invoke EmptyState",
            content.contains("EmptyState(")
        )
        assertTrue(
            "TrackerScreen must handle filtered empty state with clear filter action",
            content.contains("Clear Filter")
        )
        assertTrue(
            "TrackerScreen must handle unfiltered empty state with new application action",
            content.contains("No applications tracked yet")
        )
    }

    @Test
    fun testProfileScreenIntegratesEmptyState() {
        val file = File("src/main/kotlin/com/cellclaw/ui/screens/ProfileScreen.kt")
        assertTrue("ProfileScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "ProfileScreen must import EmptyState",
            content.contains("import com.cellclaw.ui.components.EmptyState")
        )
        assertTrue(
            "ProfileScreen must invoke EmptyState for empty confirmed facts",
            content.contains("EmptyState(")
        )
        assertTrue(
            "ProfileScreen must offer primary action to add profile fact",
            content.contains("Add Profile Fact")
        )
        assertTrue(
            "ProfileScreen must highlight zero-fabrication ground truth",
            content.contains("Zero-fabrication")
        )
    }
}

