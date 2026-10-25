package com.cellclaw.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ThemeTest {

    @Test
    fun testLightAndDarkBrandColorsAreDistinct() {
        assertNotEquals(PrimaryLight, PrimaryDark)
        assertNotEquals(BackgroundLight, BackgroundDark)
        assertNotEquals(SurfaceLight, SurfaceDark)
        assertNotEquals(SurfaceVariantLight, SurfaceVariantDark)
        assertNotEquals(OutlineLight, OutlineDark)
    }

    @Test
    fun testLightSemanticColorsAreDistinctAndValid() {
        val light = LightElixirColors
        assertNotEquals("Success must differ from error", light.success, light.error)
        assertNotEquals("Warning must differ from info", light.warning, light.info)
        assertNotEquals("Success container must differ from success", light.success, light.successContainer)
        assertNotEquals("Error container must differ from error", light.error, light.errorContainer)
        assertNotEquals("Eligible badge must differ from not eligible badge", light.badgeEligible, light.badgeNotEligible)
        assertNotEquals("Eligible badge must differ from unknown badge", light.badgeEligible, light.badgeUnknown)
        assertNotNull(light.cardBorder)
        assertNotNull(light.divider)
    }

    @Test
    fun testDarkSemanticColorsAdaptForDarkTheme() {
        val dark = DarkElixirColors
        val light = LightElixirColors

        assertNotEquals("Dark success must adapt from light success", light.success, dark.success)
        assertNotEquals("Dark warning must adapt from light warning", light.warning, dark.warning)
        assertNotEquals("Dark error must adapt from light error", light.error, dark.error)
        assertNotEquals("Dark info must adapt from light info", light.info, dark.info)
        assertNotEquals("Dark card border must adapt from light card border", light.cardBorder, dark.cardBorder)
        assertNotEquals("Dark divider must adapt from light divider", light.divider, dark.divider)

        assertNotEquals("Dark success must differ from dark error", dark.success, dark.error)
        assertNotEquals("Dark badge eligible must differ from dark badge not eligible", dark.badgeEligible, dark.badgeNotEligible)
    }

    @Test
    fun testSpacingHierarchyIncreasesMonotonically() {
        val spacing = Spacing()
        assertEquals(0.dp, spacing.none)
        assertTrue(spacing.none < spacing.extraSmall)
        assertTrue(spacing.extraSmall < spacing.small)
        assertTrue(spacing.small < spacing.medium)
        assertTrue(spacing.medium < spacing.large)
        assertTrue(spacing.large < spacing.extraLarge)
        assertTrue(spacing.extraLarge < spacing.huge)
    }

    @Test
    fun testShapeTokensAreDefinedAndProperlyConfigured() {
        assertNotNull(ElixirShapes.extraSmall)
        assertNotNull(ElixirShapes.small)
        assertNotNull(ElixirShapes.medium)
        assertNotNull(ElixirShapes.large)
        assertNotNull(ElixirShapes.extraLarge)

        assertNotNull(ElixirShapeTokens.Badge)
        assertNotNull(ElixirShapeTokens.Card)
        assertNotNull(ElixirShapeTokens.Dialog)
        assertNotNull(ElixirShapeTokens.Button)
        assertNotNull(ElixirShapeTokens.Input)
        assertNotNull(ElixirShapeTokens.Pill)
    }

    @Test
    fun testTypographyHierarchyMaintainsStandardScale() {
        val typo = ElixirTypography
        assertTrue(typo.displayLarge.fontSize.value > typo.headlineLarge.fontSize.value)
        assertTrue(typo.headlineLarge.fontSize.value > typo.titleLarge.fontSize.value)
        assertTrue(typo.titleLarge.fontSize.value >= typo.bodyLarge.fontSize.value)
        assertTrue(typo.bodyLarge.fontSize.value > typo.bodySmall.fontSize.value)
        assertTrue(typo.labelLarge.fontSize.value > typo.labelSmall.fontSize.value)
        assertTrue(typo.labelSmall.fontSize.value > 0f)
    }
}
