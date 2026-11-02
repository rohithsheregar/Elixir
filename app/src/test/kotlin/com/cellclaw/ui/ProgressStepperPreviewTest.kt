package com.cellclaw.ui

import com.cellclaw.ui.components.PlacementStep
import com.cellclaw.ui.components.StepState
import com.cellclaw.ui.components.getStepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM unit tests for ProgressStepper logic and screen integration.
 *
 * Compose rendering tests are NEEDS_USER_DEVICE; this suite covers:
 *  - PlacementStep enum ordering and properties
 *  - getStepState() correctness for all (step, currentStep) combinations
 *  - Monotonicity: steps before current are COMPLETED, current is CURRENT, future are UPCOMING
 *  - Screen source files import ProgressStepper and PlacementStep (integration check)
 */
class ProgressStepperPreviewTest {

    // ── PlacementStep model ──────────────────────────────────────────────────

    @Test
    fun testPlacementStepOrderIsMonotonic() {
        val steps = PlacementStep.ALL
        assertEquals(4, steps.size)
        steps.forEachIndexed { index, step ->
            assertEquals(
                "Step at list position $index must have stepIndex $index",
                index,
                step.stepIndex
            )
        }
    }

    @Test
    fun testPlacementStepHumanReadableTitles() {
        assertEquals("JD Ingestion", PlacementStep.JD_INGESTION.title)
        assertEquals("Eligibility", PlacementStep.ELIGIBILITY.title)
        assertEquals("Tailored Resume", PlacementStep.TAILORED_RESUME.title)
        assertEquals("Supervised Review", PlacementStep.SUPERVISED_REVIEW.title)
    }

    @Test
    fun testPlacementStepShortTitles() {
        assertEquals("JD", PlacementStep.JD_INGESTION.shortTitle)
        assertEquals("Eligibility", PlacementStep.ELIGIBILITY.shortTitle)
        assertEquals("Resume", PlacementStep.TAILORED_RESUME.shortTitle)
        assertEquals("Review", PlacementStep.SUPERVISED_REVIEW.shortTitle)
    }

    // ── getStepState() ───────────────────────────────────────────────────────

    @Test
    fun testGetStepStateWhenCurrentIsFirst() {
        val current = PlacementStep.JD_INGESTION
        assertEquals(StepState.CURRENT, getStepState(PlacementStep.JD_INGESTION, current))
        assertEquals(StepState.UPCOMING, getStepState(PlacementStep.ELIGIBILITY, current))
        assertEquals(StepState.UPCOMING, getStepState(PlacementStep.TAILORED_RESUME, current))
        assertEquals(StepState.UPCOMING, getStepState(PlacementStep.SUPERVISED_REVIEW, current))
    }

    @Test
    fun testGetStepStateWhenCurrentIsSecond() {
        val current = PlacementStep.ELIGIBILITY
        assertEquals(StepState.COMPLETED, getStepState(PlacementStep.JD_INGESTION, current))
        assertEquals(StepState.CURRENT, getStepState(PlacementStep.ELIGIBILITY, current))
        assertEquals(StepState.UPCOMING, getStepState(PlacementStep.TAILORED_RESUME, current))
        assertEquals(StepState.UPCOMING, getStepState(PlacementStep.SUPERVISED_REVIEW, current))
    }

    @Test
    fun testGetStepStateWhenCurrentIsThird() {
        val current = PlacementStep.TAILORED_RESUME
        assertEquals(StepState.COMPLETED, getStepState(PlacementStep.JD_INGESTION, current))
        assertEquals(StepState.COMPLETED, getStepState(PlacementStep.ELIGIBILITY, current))
        assertEquals(StepState.CURRENT, getStepState(PlacementStep.TAILORED_RESUME, current))
        assertEquals(StepState.UPCOMING, getStepState(PlacementStep.SUPERVISED_REVIEW, current))
    }

    @Test
    fun testGetStepStateWhenCurrentIsLast() {
        val current = PlacementStep.SUPERVISED_REVIEW
        assertEquals(StepState.COMPLETED, getStepState(PlacementStep.JD_INGESTION, current))
        assertEquals(StepState.COMPLETED, getStepState(PlacementStep.ELIGIBILITY, current))
        assertEquals(StepState.COMPLETED, getStepState(PlacementStep.TAILORED_RESUME, current))
        assertEquals(StepState.CURRENT, getStepState(PlacementStep.SUPERVISED_REVIEW, current))
    }

    @Test
    fun testMonotonicityForAllCombinations() {
        PlacementStep.ALL.forEach { currentStep ->
            PlacementStep.ALL.forEach { step ->
                val state = getStepState(step, currentStep)
                when {
                    step.stepIndex < currentStep.stepIndex ->
                        assertEquals(
                            "step=${step.name}, current=${currentStep.name}: expected COMPLETED",
                            StepState.COMPLETED, state
                        )
                    step.stepIndex == currentStep.stepIndex ->
                        assertEquals(
                            "step=${step.name}, current=${currentStep.name}: expected CURRENT",
                            StepState.CURRENT, state
                        )
                    else ->
                        assertEquals(
                            "step=${step.name}, current=${currentStep.name}: expected UPCOMING",
                            StepState.UPCOMING, state
                        )
                }
            }
        }
    }

    // ── Screen integration checks (source file scanning) ────────────────────

    private fun assertScreenContainsStepper(relativePath: String, stepName: String) {
        val file = File("src/main/kotlin/com/cellclaw/ui/screens/$relativePath")
        assertTrue("$relativePath must exist", file.exists())
        val content = file.readText()
        assertTrue(
            "$relativePath must import PlacementStep",
            content.contains("import com.cellclaw.ui.components.PlacementStep")
        )
        assertTrue(
            "$relativePath must import ProgressStepper",
            content.contains("import com.cellclaw.ui.components.ProgressStepper")
        )
        assertTrue(
            "$relativePath must use ProgressStepper composable",
            content.contains("ProgressStepper(")
        )
        assertTrue(
            "$relativePath must use PlacementStep.$stepName",
            content.contains("PlacementStep.$stepName")
        )
    }

    @Test
    fun testNewApplicationScreenContainsStepper() {
        assertScreenContainsStepper("NewApplicationScreen.kt", "JD_INGESTION")
    }

    @Test
    fun testEligibilityReportScreenContainsStepper() {
        assertScreenContainsStepper("EligibilityReportScreen.kt", "ELIGIBILITY")
    }

    @Test
    fun testResumePreviewScreenContainsStepper() {
        assertScreenContainsStepper("ResumePreviewScreen.kt", "TAILORED_RESUME")
    }

    @Test
    fun testReviewScreenContainsStepper() {
        assertScreenContainsStepper("ReviewScreen.kt", "SUPERVISED_REVIEW")
    }

    @Test
    fun testProgressStepperComponentFileExists() {
        val file = File("src/main/kotlin/com/cellclaw/ui/components/ProgressStepper.kt")
        assertTrue("ProgressStepper.kt must exist", file.exists())
        val content = file.readText()
        assertTrue("Must declare PlacementStep enum", content.contains("enum class PlacementStep"))
        assertTrue("Must declare StepState enum", content.contains("enum class StepState"))
        assertTrue("Must declare getStepState function", content.contains("fun getStepState"))
        assertTrue("Must declare ProgressStepper composable", content.contains("fun ProgressStepper"))
        assertTrue("Must have all 4 steps", content.contains("JD_INGESTION"))
        assertTrue("Must have ELIGIBILITY step", content.contains("ELIGIBILITY"))
        assertTrue("Must have TAILORED_RESUME step", content.contains("TAILORED_RESUME"))
        assertTrue("Must have SUPERVISED_REVIEW step", content.contains("SUPERVISED_REVIEW"))
    }
}

