package com.ahu.ahutong.ui.screen

import com.ahu.ahutong.data.model.AppUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainNavigationPolicyTest {
    @Test
    fun `preferences parent follows the active navigation model`() {
        assertEquals("settings", settingsParentRoute(AppUiTheme.RADIANT))
        AppUiTheme.entries
            .filterNot { it == AppUiTheme.RADIANT }
            .forEach { theme ->
                assertEquals("home", settingsParentRoute(theme), theme.name)
            }
    }

    @Test
    fun `preferences normalizes stale parents in both theme directions`() {
        assertTrue(shouldNormalizePreferencesParent(AppUiTheme.RADIANT, "home"))
        assertTrue(shouldNormalizePreferencesParent(AppUiTheme.MATERIAL, "settings"))
        assertFalse(shouldNormalizePreferencesParent(AppUiTheme.RADIANT, "settings"))
        assertFalse(shouldNormalizePreferencesParent(AppUiTheme.MATERIAL, "home"))
    }
}
