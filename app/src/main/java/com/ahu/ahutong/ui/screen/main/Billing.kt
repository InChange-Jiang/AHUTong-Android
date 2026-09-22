package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.state.BillingViewModel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.util.Locale

/**
 * 账单（校园卡交易流水）：本月收支汇总 + 分页流水列表。
 * 金额：分→元整数运算后格式化；支出 -红系 / 收入 +绿系以外的中性色（与充值记录区分）。
 * 时间：直接用服务端本地化的 effectdateStr。
 */
@Composable
fun Billing(
    onBack: () -> Unit,
    viewModel: BillingViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    var detailRecord by remember { mutableStateOf<TurnoverRecord?>(null) }

    AppPageScaffold(
        title = "账单",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        actions = listOf(
            TrailingAction(Icons.Outlined.Refresh, "刷新") { viewModel.refresh() }
        ),
        lazyContent = {
            // 汇总头
            item(key = "summary") {
                BillingSummaryCard(
                    expensesFen = summary?.expenses,
                    incomeFen = summary?.income
                )
            }

            when (val state = listState) {
                is BillingViewModel.ListState.Loading -> {
                    item(key = "loading") {
                        AppStateCard.Loading(message = "账单加载中…")
                    }
                }
                is BillingViewModel.ListState.Error -> {
                    item(key = "error") {
                        AppStateCard.Error(
                            message = state.message,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }
                is BillingViewModel.ListState.Ready -> {
                    if (records.isEmpty()) {
                        item(key = "empty") {
                            AppStateCard.Empty(
                                message = "本月暂无账单记录",
                                subtitle = "校园卡消费与充值流水将在这里展示"
                            )
                        }
                    } else {
                        items(records, key = { it.orderId }) { record ->
                            BillingRecordItem(
                                record = record,
                                onClick = { detailRecord = record }
                            )
                        }
                        item(key = "footer") {
                            BillingListFooter(
                                hasMore = hasMore,
                                loadingMore = loadingMore,
                                onLoadMore = { viewModel.loadNextPage() }
                            )
                        }
                    }
                }
            }
        }
    )

    detailRecord?.let { record ->
        BillingDetailDialog(record = record, onDismiss = { detailRecord = null })
    }
}

/** 本月收支汇总头卡。金额单位：分。 */
@Composable
private fun BillingSummaryCard(expensesFen: Long?, incomeFen: Long?) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "本月支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${formatFen(expensesFen)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = 40.a1 withNight 75.a1
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "本月收入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${formatFen(incomeFen)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BillingRecordItem(record: TurnoverRecord, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp, vertical = 12.dp
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.toMerchant?.trim().orEmpty().ifBlank {
                        record.resume.orEmpty()
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.effectdateStr.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${if (record.isExpense) "-" else "+"}${formatFen(record.tranamt)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (record.isExpense) {
                    40.a1 withNight 75.a1
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

@Composable
private fun BillingListFooter(
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    if (!hasMore) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "已加载全部",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    // 进入组合即加载下一页（LazyColumn 滚动到底时 footer 入屏触发）
    LaunchedEffect(Unit) { onLoadMore() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        if (loadingMore) {
            com.ahu.ahutong.ui.components.AppCircularProgressIndicator()
        }
    }
}

/** 单笔详情弹窗（H5 详情页的字段集合）。 */
@Composable
private fun BillingDetailDialog(record: TurnoverRecord, onDismiss: () -> Unit) {
    AppDialog(
        title = record.toMerchant?.trim()?.ifBlank { null } ?: "交易详情",
        onDismiss = onDismiss,
        actions = listOf(AppDialogAction("关闭", onClick = onDismiss)),
        content = {
            BillingDetailRow("金额", "${if (record.isExpense) "-" else "+"}¥${formatFen(record.tranamt)}")
            BillingDetailRow("类型", record.consumeTypeName ?: record.turnoverType.orEmpty())
            BillingDetailRow("时间", record.effectdateStr.orEmpty())
            BillingDetailRow("交易后余额", "¥${formatFen(record.cardBalance)}")
            record.resume?.takeIf { it.isNotBlank() }?.let { BillingDetailRow("摘要", it) }
            if (record.feeAmt > 0) BillingDetailRow("手续费", "¥${formatFen(record.feeAmt)}")
        }
    )
}

@Composable
private fun BillingDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f)
        )
    }
}

/** 分 → 元，整数运算避免浮点误差，保留两位。 */
private fun formatFen(fen: Long?): String {
    val safe = fen ?: 0
    return String.format(Locale.CHINA, "%d.%02d", safe / 100, safe % 100)
}
