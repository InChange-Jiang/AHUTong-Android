package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard

/**
 * 账单（校园卡交易清单）——标准空页面占位，等开发文档到位后再填充实现。
 */
@Composable
fun Billing(
    onBack: () -> Unit
) {
    AppPageScaffold(
        title = "账单",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        freeContent = {
            AppStateCard.Empty(
                message = "账单功能开发中",
                subtitle = "校园卡交易清单将在这里展示",
                icon = Icons.Outlined.ReceiptLong,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}
