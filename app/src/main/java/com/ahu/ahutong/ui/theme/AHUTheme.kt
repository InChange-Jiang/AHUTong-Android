package com.ahu.ahutong.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.dao.DEFAULT_THEME_COLOR
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.kyant.monet.dynamicColorScheme
import com.kyant.monet.n1
import com.kyant.monet.toColor
import com.kyant.monet.toSrgb
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun AHUTheme(content: @Composable () -> Unit) {
    val preferencesViewModel: PreferencesViewModel = hiltViewModel()
    val themeColorHex by preferencesViewModel.themeColor.collectAsState()
    val themeMode by preferencesViewModel.appThemeMode.collectAsState()
    val appUiTheme by preferencesViewModel.appUiTheme.collectAsState()
    val isUiThemePreferenceReady by
        preferencesViewModel.isUiThemePreferenceReady.collectAsState()
    val isDarkTheme = themeMode.resolve(isSystemInDarkTheme())
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val themeConfiguration = remember(configuration, isDarkTheme) {
        Configuration(configuration).apply {
            val nightMode = if (isDarkTheme) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
    }
    val view = LocalView.current

    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    val usesBuiltInDefaultColor =
        appUiTheme == AppUiTheme.MIUIX && themeColorHex == DEFAULT_THEME_COLOR
    val customKeyColor = remember(themeColorHex, usesBuiltInDefaultColor) {
        themeColorHex
            ?.takeUnless { it == DEFAULT_THEME_COLOR }
            ?.let { value ->
            runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
        }
    }
    val keyColor = when {
        usesBuiltInDefaultColor -> Color(0xFF3482FF)
        customKeyColor != null -> customKeyColor
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            colorResource(id = android.R.color.system_accent1_500)
        else -> Color(0xFF007FAC)
    }
    val tonalPalettes = remember(keyColor) {
        keyColor.toSrgb().toColor().toTonalPalettes()
    }

    CompositionLocalProvider(
        LocalConfiguration provides themeConfiguration,
        LocalTonalPalettes provides tonalPalettes
    ) {
        val colorScheme = if (
            customKeyColor == null &&
            !usesBuiltInDefaultColor &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            if (isDarkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        } else {
            val generated = dynamicColorScheme(isLight = !isDarkTheme)
            if (isDarkTheme) {
                generated.copy(
                    background = 6.n1,
                    onBackground = 90.n1,
                    surface = 6.n1,
                    onSurface = 90.n1,
                    surfaceVariant = 30.n1,
                    onSurfaceVariant = 80.n1,
                    inverseSurface = 90.n1,
                    inverseOnSurface = 20.n1,
                    outline = 60.n1,
                    outlineVariant = 30.n1,
                    surfaceBright = 24.n1,
                    surfaceDim = 6.n1,
                    surfaceContainerLowest = 4.n1,
                    surfaceContainerLow = 10.n1,
                    surfaceContainer = 12.n1,
                    surfaceContainerHigh = 17.n1,
                    surfaceContainerHighest = 22.n1
                )
            } else {
                generated.copy(
                    background = 98.n1,
                    onBackground = 10.n1,
                    surface = 98.n1,
                    onSurface = 10.n1,
                    surfaceVariant = 90.n1,
                    onSurfaceVariant = 30.n1,
                    inverseSurface = 20.n1,
                    inverseOnSurface = 95.n1,
                    outline = 50.n1,
                    outlineVariant = 80.n1,
                    surfaceBright = 98.n1,
                    surfaceDim = 87.n1,
                    surfaceContainerLowest = 100.n1,
                    surfaceContainerLow = 96.n1,
                    surfaceContainer = 94.n1,
                    surfaceContainerHigh = 92.n1,
                    surfaceContainerHighest = 90.n1
                )
            }
        }
        val miuixUsesSystemColor = themeColorHex == null ||
            (themeColorHex == DEFAULT_THEME_COLOR && appUiTheme != AppUiTheme.MIUIX)
        val miuixColorSchemeMode = when {
            // Miuix's own fixed palettes are the HyperOS defaults: #3482FF in light mode and
            // #277AF7 in dark mode. Generating a Monet palette from that blue changes the control
            // colors and makes "默认" look like a system-derived theme instead.
            usesBuiltInDefaultColor && isDarkTheme -> ColorSchemeMode.Dark
            usesBuiltInDefaultColor -> ColorSchemeMode.Light
            miuixUsesSystemColor -> ColorSchemeMode.MonetSystem
            isDarkTheme -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetLight
        }
        val miuixController = remember(keyColor, isDarkTheme, miuixColorSchemeMode) {
            ThemeController(
                colorSchemeMode = miuixColorSchemeMode,
                keyColor = keyColor.takeUnless { miuixUsesSystemColor },
                isDark = isDarkTheme
            )
        }
        MaterialTheme(colorScheme = colorScheme) {
            val liquidGlassTokens = rememberLiquidGlassTokens(
                enabled = isUiThemePreferenceReady && appUiTheme == AppUiTheme.LIQUID_GLASS
            )
            MiuixTheme(controller = miuixController) {
                CompositionLocalProvider(
                    LocalContentColor provides if (isDarkTheme) 100.n1 else 0.n1,
                    LocalAppUiTheme provides appUiTheme,
                    LocalIsLiquidGlassEnabled provides liquidGlassTokens.enabled,
                    LocalLiquidGlassTokens provides liquidGlassTokens
                ) {
                    // Keep the root node stable so switching UI libraries never recreates the
                    // navigation subtree. The transparent scaffold is also Miuix's popup host.
                    MiuixScaffold(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { content() }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
