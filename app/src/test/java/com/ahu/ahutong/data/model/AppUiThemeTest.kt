package com.ahu.ahutong.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppUiThemeTest {
    @Test
    fun `stored theme wins over legacy liquid glass preference`() {
        assertEquals(AppUiTheme.MIUIX, AppUiTheme.fromStorage("miuix", false))
    }

    @Test
    fun `legacy preference migrates without changing appearance`() {
        assertEquals(AppUiTheme.MATERIAL, AppUiTheme.fromStorage(null, false))
        assertEquals(AppUiTheme.LIQUID_GLASS, AppUiTheme.fromStorage(null, true))
        assertEquals(AppUiTheme.LIQUID_GLASS, AppUiTheme.fromStorage(null, null))
    }
}
