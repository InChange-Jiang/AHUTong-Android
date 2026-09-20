package com.ahu.ahutong.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.ahu.ahutong.data.model.AppUiTheme

val LocalIsLiquidGlassEnabled = compositionLocalOf { false }

val LocalAppUiTheme = compositionLocalOf { AppUiTheme.MATERIAL }
