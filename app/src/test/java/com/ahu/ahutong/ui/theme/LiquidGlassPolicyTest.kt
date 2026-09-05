package com.ahu.ahutong.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidGlassPolicyTest {
    @Test
    fun `disabled preference always uses stable material fallback`() {
        listOf(26, 30, 31, 32, 33, 36).forEach { sdkInt ->
            assertEquals(
                LiquidGlassQuality.Disabled,
                resolveLiquidGlassQuality(enabled = false, sdkInt = sdkInt)
            )
        }
    }

    @Test
    fun `enabled preference selects capability safe quality by sdk`() {
        assertEquals(LiquidGlassQuality.Tinted, resolveLiquidGlassQuality(true, 26))
        assertEquals(LiquidGlassQuality.Tinted, resolveLiquidGlassQuality(true, 30))
        assertEquals(LiquidGlassQuality.Blurred, resolveLiquidGlassQuality(true, 31))
        assertEquals(LiquidGlassQuality.Blurred, resolveLiquidGlassQuality(true, 32))
        assertEquals(LiquidGlassQuality.Refractive, resolveLiquidGlassQuality(true, 33))
        assertEquals(LiquidGlassQuality.Refractive, resolveLiquidGlassQuality(true, 36))
    }

    @Test
    fun `only supported qualities capture blur or refract`() {
        assertFalse(LiquidGlassQuality.Tinted.supportsBackdrop)
        assertFalse(LiquidGlassQuality.Tinted.supportsBlur)
        assertFalse(LiquidGlassQuality.Blurred.supportsRefraction)
        assertTrue(LiquidGlassQuality.Blurred.supportsBackdrop)
        assertTrue(LiquidGlassQuality.Blurred.supportsBlur)
        assertTrue(LiquidGlassQuality.Refractive.supportsRefraction)
    }
}
