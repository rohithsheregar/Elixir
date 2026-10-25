package com.cellclaw.ui

import androidx.compose.ui.graphics.Color
import com.cellclaw.ui.theme.DarkElixirColors
import com.cellclaw.ui.theme.LightElixirColors
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class DarkThemeContrastTest {

    private fun linearize(channel: Float): Double {
        return if (channel <= 0.04045f) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testLightModeSemanticTokensMeetWcagContrast() {
        val light = LightElixirColors

        // Ensure status text / container contrast is at least 3.0:1 (large text / UI component standard)
        val successRatio = contrastRatio(light.onSuccessContainer, light.successContainer)
        assertTrue("Light success container contrast must be >= 3.0:1, was $successRatio", successRatio >= 3.0)

        val warningRatio = contrastRatio(light.onWarningContainer, light.warningContainer)
        assertTrue("Light warning container contrast must be >= 3.0:1, was $warningRatio", warningRatio >= 3.0)

        val errorRatio = contrastRatio(light.onErrorContainer, light.errorContainer)
        assertTrue("Light error container contrast must be >= 3.0:1, was $errorRatio", errorRatio >= 3.0)

        val infoRatio = contrastRatio(light.onInfoContainer, light.infoContainer)
        assertTrue("Light info container contrast must be >= 3.0:1, was $infoRatio", infoRatio >= 3.0)

        val badgeEligibleRatio = contrastRatio(light.onBadgeEligible, light.badgeEligible)
        assertTrue("Light eligible badge contrast must be >= 3.0:1, was $badgeEligibleRatio", badgeEligibleRatio >= 3.0)

        val badgeNotEligibleRatio = contrastRatio(light.onBadgeNotEligible, light.badgeNotEligible)
        assertTrue("Light not eligible badge contrast must be >= 3.0:1, was $badgeNotEligibleRatio", badgeNotEligibleRatio >= 3.0)

        val badgeUnknownRatio = contrastRatio(light.onBadgeUnknown, light.badgeUnknown)
        assertTrue("Light unknown badge contrast must be >= 3.0:1, was $badgeUnknownRatio", badgeUnknownRatio >= 3.0)
    }

    @Test
    fun testDarkModeSemanticTokensMeetWcagContrast() {
        val dark = DarkElixirColors

        // In dark mode, ensure container and text contrast is at least 3.0:1
        val successRatio = contrastRatio(dark.onSuccessContainer, dark.successContainer)
        assertTrue("Dark success container contrast must be >= 3.0:1, was $successRatio", successRatio >= 3.0)

        val warningRatio = contrastRatio(dark.onWarningContainer, dark.warningContainer)
        assertTrue("Dark warning container contrast must be >= 3.0:1, was $warningRatio", warningRatio >= 3.0)

        val errorRatio = contrastRatio(dark.onErrorContainer, dark.errorContainer)
        assertTrue("Dark error container contrast must be >= 3.0:1, was $errorRatio", errorRatio >= 3.0)

        val infoRatio = contrastRatio(dark.onInfoContainer, dark.infoContainer)
        assertTrue("Dark info container contrast must be >= 3.0:1, was $infoRatio", infoRatio >= 3.0)

        val badgeEligibleRatio = contrastRatio(dark.onBadgeEligible, dark.badgeEligible)
        assertTrue("Dark eligible badge contrast must be >= 3.0:1, was $badgeEligibleRatio", badgeEligibleRatio >= 3.0)

        val badgeNotEligibleRatio = contrastRatio(dark.onBadgeNotEligible, dark.badgeNotEligible)
        assertTrue("Dark not eligible badge contrast must be >= 3.0:1, was $badgeNotEligibleRatio", badgeNotEligibleRatio >= 3.0)

        val badgeUnknownRatio = contrastRatio(dark.onBadgeUnknown, dark.badgeUnknown)
        assertTrue("Dark unknown badge contrast must be >= 3.0:1, was $badgeUnknownRatio", badgeUnknownRatio >= 3.0)
    }

    @Test
    fun testAllPlacementScreensExcludeHardcodedHexColors() {
        val screenNames = listOf(
            "TrackerScreen.kt",
            "NewApplicationScreen.kt",
            "EligibilityReportScreen.kt",
            "ResumePreviewScreen.kt",
            "ReviewScreen.kt",
            "ProfileScreen.kt",
            "RunScreen.kt",
            "ResultScreen.kt"
        )

        val screensDir = File("src/main/kotlin/com/cellclaw/ui/screens")
        val altScreensDir = File("app/src/main/kotlin/com/cellclaw/ui/screens")
        val activeDir = if (screensDir.exists()) screensDir else altScreensDir

        for (screenName in screenNames) {
            val file = File(activeDir, screenName)
            if (!file.exists()) continue

            val text = file.readText()
            // Assert that raw hex literals like Color(0xFF...) are absent from screen implementations
            val hasHardcodedHex = text.contains("Color(0xFF")
            assertTrue("Screen $screenName must not contain hardcoded Color(0xFF...) literals, use semantic theme tokens instead", !hasHardcodedHex)
        }
    }
}
