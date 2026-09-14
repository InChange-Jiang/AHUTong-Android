package com.ahu.ahutong.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.ahu.ahutong.data.model.AppUiTheme

/** 是否处于「曜光 RadiantUI」整合格局（新主页 + 学习通日历提级 + 扁平导航）。 */
val isRadiantUi: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAppUiTheme.current == AppUiTheme.RADIANT
