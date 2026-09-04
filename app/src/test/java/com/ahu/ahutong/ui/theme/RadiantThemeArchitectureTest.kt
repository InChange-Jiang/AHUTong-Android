package com.ahu.ahutong.ui.theme

import com.ahu.ahutong.data.model.AppUiTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadiantThemeArchitectureTest {
    @Test
    fun `radiant owns a distinct home and bottom navigation layout`() {
        assertTrue(AppUiTheme.LIQUID_GLASS.usesLiquidGlass)
        assertTrue(AppUiTheme.RADIANT.usesLiquidGlass)

        val home = source("com/ahu/ahutong/ui/screen/main/Home.kt")
        val bottomBar = source("com/ahu/ahutong/ui/screen/BottomNavBar.kt")
        val main = source("com/ahu/ahutong/ui/screen/Main.kt")

        assertTrue(home.contains("val radiant = isRadiantUi"))
        assertTrue(home.contains("HomeWidgetRegistry.slotCountRadiant"))
        assertTrue(home.contains("HomeDateRow("))
        assertTrue(bottomBar.contains("if (isRadiantUi)"))
        assertTrue(bottomBar.contains("\"xuexiaotong\""))
        assertTrue(main.contains("currentRoute == \"xuexiaotong\""))
    }

    @Test
    fun `xuexiaotong login uses controls that dispatch all app themes`() {
        val login = source("com/ahu/ahutong/ui/screen/xuexiaotong/XuexiaotongLoginScreen.kt")

        assertTrue(login.contains("AppCard("))
        assertTrue(login.contains("AppTextField("))
        assertTrue(login.contains("AppButton("))
        assertTrue(login.contains("AppCircularProgressIndicator("))
        assertFalse(login.contains("androidx.compose.material3.OutlinedTextField"))
        assertFalse(login.contains("androidx.compose.material3.Button"))
    }

    private fun source(relativePath: String): String = File(
        repositoryRoot(),
        "app/src/main/java/$relativePath"
    ).readText()

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
