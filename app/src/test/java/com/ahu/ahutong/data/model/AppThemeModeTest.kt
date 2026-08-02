package com.ahu.ahutong.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun `unknown storage value falls back to follow system`() {
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, AppThemeMode.fromStorage("unknown"))
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, AppThemeMode.fromStorage(null))
    }

    @Test
    fun `stored values round trip`() {
        AppThemeMode.entries.forEach { mode ->
            assertEquals(mode, AppThemeMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun `mode resolves expected darkness`() {
        assertTrue(AppThemeMode.FOLLOW_SYSTEM.resolve(systemIsDark = true))
        assertFalse(AppThemeMode.FOLLOW_SYSTEM.resolve(systemIsDark = false))
        assertTrue(AppThemeMode.DARK.resolve(systemIsDark = false))
        assertFalse(AppThemeMode.LIGHT.resolve(systemIsDark = true))
    }
}
