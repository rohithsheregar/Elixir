package com.cellclaw.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM unit test suite verifying animated screen transitions and list reordering animations (U7).
 */
class MotionTransitionTest {

    @Test
    fun testMainActivityNavHostConfiguresAnimatedTransitions() {
        val file = File("src/main/kotlin/com/cellclaw/ui/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "Must import AnimatedContentTransitionScope",
            content.contains("import androidx.compose.animation.AnimatedContentTransitionScope")
        )
        assertTrue(
            "Must import fadeIn and fadeOut",
            content.contains("import androidx.compose.animation.fadeIn") &&
                content.contains("import androidx.compose.animation.fadeOut")
        )
        assertTrue(
            "Must import tween animationSpec",
            content.contains("import androidx.compose.animation.core.tween")
        )
        assertTrue(
            "NavHost must configure enterTransition with slideIntoContainer",
            content.contains("enterTransition = {") &&
                content.contains("slideIntoContainer(")
        )
        assertTrue(
            "NavHost must configure exitTransition with slideOutOfContainer",
            content.contains("exitTransition = {") &&
                content.contains("slideOutOfContainer(")
        )
        assertTrue(
            "NavHost must configure popEnterTransition",
            content.contains("popEnterTransition = {")
        )
        assertTrue(
            "NavHost must configure popExitTransition",
            content.contains("popExitTransition = {")
        )
        assertTrue(
            "Transitions must use consistent 300ms tween specification",
            content.contains("tween(300)")
        )
    }

    @Test
    fun testTrackerScreenConfiguresListReorderingAnimation() {
        val file = File("src/main/kotlin/com/cellclaw/ui/screens/TrackerScreen.kt")
        assertTrue("TrackerScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "TrackerScreen LazyColumn items must specify item key",
            content.contains("items(uiState.applications, key = { it.id })")
        )
        assertTrue(
            "TrackerScreen items must apply animateItem modifier",
            content.contains("Modifier.animateItem()")
        )
        assertTrue(
            "ApplicationCard must accept modifier parameter with default value",
            content.contains("modifier: Modifier = Modifier")
        )
    }

    @Test
    fun testProfileScreenConfiguresListReorderingAndItemAnimations() {
        val file = File("src/main/kotlin/com/cellclaw/ui/screens/ProfileScreen.kt")
        assertTrue("ProfileScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "ProfileScreen must import AnimatedVisibility",
            content.contains("import androidx.compose.animation.AnimatedVisibility")
        )
        assertTrue(
            "ProfileScreen proposals must specify item key",
            content.contains("items(uiState.proposals, key = { it.proposed.id })")
        )
        assertTrue(
            "ProfileScreen confirmed facts must specify item key",
            content.contains("items(uiState.confirmedFacts, key = { it.id })")
        )
        assertTrue(
            "ProfileScreen items must apply animateItem modifier",
            content.contains("Modifier.animateItem()")
        )
        assertTrue(
            "ProposalDiffCard must accept modifier parameter",
            content.contains("fun ProposalDiffCard(") &&
                content.contains("modifier: Modifier = Modifier")
        )
        assertTrue(
            "ConfirmedFactCard must accept modifier parameter",
            content.contains("fun ConfirmedFactCard(") &&
                content.contains("modifier: Modifier = Modifier")
        )
    }
}

