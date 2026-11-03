package com.cellclaw.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationWiringTest {

    private fun loadSourceFile(relativePath: String): String {
        val file = sequenceOf(
            File(relativePath),
            File("app/$relativePath")
        ).firstOrNull { it.exists() } ?: error("File not found: $relativePath")
        return file.readText()
    }

    @Test
    fun `tracker is the primary start destination`() {
        assertEquals("Start destination must be tracker", "tracker", NavigationRoutes.START_DESTINATION)
        assertEquals("Tracker route must be tracker", "tracker", NavigationRoutes.TRACKER)
    }

    @Test
    fun `navigation routes define all core Elixir destinations`() {
        assertEquals("profile", NavigationRoutes.PROFILE)
        assertEquals("settings", NavigationRoutes.SETTINGS)
        assertEquals("chat", NavigationRoutes.CHAT)
        assertEquals("new_application", NavigationRoutes.NEW_APPLICATION)
        assertEquals("eligibility_report/{appId}", NavigationRoutes.ELIGIBILITY_REPORT)
        assertEquals("resume_preview/{appId}", NavigationRoutes.RESUME_PREVIEW)
        assertEquals("review/{appId}", NavigationRoutes.REVIEW)

        assertEquals("eligibility_report/app-123", NavigationRoutes.eligibilityReportRoute("app-123"))
        assertEquals("resume_preview/app-123", NavigationRoutes.resumePreviewRoute("app-123"))
        assertEquals("review/app-123", NavigationRoutes.reviewRoute("app-123"))
    }

    @Test
    fun `MainActivity wires tracker as start destination with access to profile, settings and legacy chat`() {
        val mainActivitySource = loadSourceFile("src/main/kotlin/com/cellclaw/ui/MainActivity.kt")

        assertTrue(
            "MainActivity must use NavigationRoutes.START_DESTINATION as startDestination",
            mainActivitySource.contains("val startDest = NavigationRoutes.START_DESTINATION")
        )
        assertTrue(
            "MainActivity NavHost must wire startDestination = startDest",
            mainActivitySource.contains("navController = navController") &&
                mainActivitySource.contains("startDestination = startDest")
        )
        assertTrue(
            "MainActivity must wire TrackerScreen with onNavigateToSettings",
            mainActivitySource.contains("onNavigateToSettings = { navController.navigate(NavigationRoutes.SETTINGS) }")
        )
        assertTrue(
            "MainActivity must wire TrackerScreen with onNavigateToChat",
            mainActivitySource.contains("onNavigateToChat = { navController.navigate(NavigationRoutes.CHAT) }")
        )
        assertTrue(
            "MainActivity must wire TrackerScreen with onNavigateToProfile",
            mainActivitySource.contains("onNavigateToProfile = { navController.navigate(NavigationRoutes.PROFILE) }")
        )
        assertTrue(
            "MainActivity must wire TrackerScreen with onNavigateToNewApp",
            mainActivitySource.contains("onNavigateToNewApp = { navController.navigate(NavigationRoutes.NEW_APPLICATION) }")
        )
    }

    @Test
    fun `TrackerScreen declares navigation callbacks and actions for profile, settings and legacy chat`() {
        val trackerSource = loadSourceFile("src/main/kotlin/com/cellclaw/ui/screens/TrackerScreen.kt")

        assertTrue("TrackerScreen must have onNavigateToProfile parameter", trackerSource.contains("onNavigateToProfile: () -> Unit"))
        assertTrue("TrackerScreen must have onNavigateToSettings parameter", trackerSource.contains("onNavigateToSettings: () -> Unit"))
        assertTrue("TrackerScreen must have onNavigateToChat parameter", trackerSource.contains("onNavigateToChat: () -> Unit"))
        assertTrue("TrackerScreen must have onNavigateToNewApp parameter", trackerSource.contains("onNavigateToNewApp: () -> Unit"))

        assertTrue("TrackerScreen must render Profile action icon", trackerSource.contains("contentDescription = \"Profile\""))
        assertTrue("TrackerScreen must render Settings action icon", trackerSource.contains("contentDescription = \"Settings\""))
        assertTrue("TrackerScreen must render Chat action icon", trackerSource.contains("contentDescription = \"Legacy Chat\""))
    }

    @Test
    fun `ChatScreen declares navigation route to Elixir Tracker`() {
        val chatSource = loadSourceFile("src/main/kotlin/com/cellclaw/ui/screens/ChatScreen.kt")

        assertTrue("ChatScreen must have onNavigateToTracker parameter", chatSource.contains("onNavigateToTracker: () -> Unit"))
        assertTrue("ChatScreen must render Elixir Tracker action button", chatSource.contains("contentDescription = \"Elixir Tracker\"") || chatSource.contains("\"Elixir Tracker\""))
    }
}
