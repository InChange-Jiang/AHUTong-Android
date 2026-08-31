package com.ahu.ahutong.ui.screen.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.model.CardRechargeBank
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppScrollablePageLayout
import com.ahu.ahutong.ui.components.AppComponentTokens
import com.ahu.ahutong.ui.components.AppDialogSurface
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.ui.components.SettingsChoice
import com.ahu.ahutong.ui.components.SettingsSelectRow
import com.ahu.ahutong.ui.state.CardAccountState
import com.ahu.ahutong.ui.state.CardBalanceDepositViewModel
import com.ahu.ahutong.ui.state.PaymentState
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.n1
import com.kyant.monet.withNight
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.personalization.action.AppActionId
import kotlinx.coroutines.delay

private const val ALIPAY_CAMPUS_CARD_SCHEME =
    "alipays://platformapi/startapp?appId=2019090967125695&page=pages%2Findex%2Findex&chInfo=ch_share__chsub_CopyLink"
private const val ALIPAY_CAMPUS_CARD_FALLBACK_URL = "https://www.wmslz.com/s/M6KARh485j3"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardBalanceDeposit(
    navController: NavController,
    viewModel: CardBalanceDepositViewModel = viewModel()
) {
    val behaviorReporter = rememberBehaviorActionReporter()

    var amount by remember { mutableStateOf("") }

    val cardInfo = viewModel.cardInfo.collectAsState()
    val accountState by viewModel.accountState.collectAsState()

    val agriculturalPaymentState by viewModel.paymentState.collectAsState()
    val cmbRechargeState by CmbRechargeAutomationController.state.collectAsState()

    var showAlipayConfirmDialog by remember { mutableStateOf(false) }
    var copyCampusCardInfo by remember { mutableStateOf(false) }
    var selectedRechargeBank by remember { mutableStateOf(AHUCache.getCardRechargeBank()) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()
    val currentUser = remember { AHUCache.getCurrentUser() }
    val campusCardUserName = currentUser?.name.orEmpty()
    val campusCardStudentId = currentUser?.xh.orEmpty()
    val paymentState = when (selectedRechargeBank) {
        CardRechargeBank.CHINA_MERCHANTS_BANK -> cmbRechargeState.toPaymentState()
        CardRechargeBank.AGRICULTURAL_BANK -> agriculturalPaymentState
        CardRechargeBank.ALIPAY,
        null -> PaymentState.Idle
    }

    fun selectRechargeBank(bank: CardRechargeBank) {
        if (paymentState == PaymentState.Loading) return
        selectedRechargeBank = bank
        AHUCache.setCardRechargeBank(bank)
        if (bank == CardRechargeBank.ALIPAY) copyCampusCardInfo = false
        viewModel.resetPaymentState()
        CmbRechargeAutomationController.resetPaymentState()
        CmbRechargeAutomationController.onBankSelected(context, bank)
    }

    fun submitRecharge() {
        when (selectedRechargeBank) {
            CardRechargeBank.ALIPAY -> showAlipayConfirmDialog = true
            CardRechargeBank.CHINA_MERCHANTS_BANK -> {
                behaviorReporter.organic(AppActionId.SUBMIT_CMB_CARD_RECHARGE)
                CmbRechargeAutomationController.submit(context = context, amount = amount)
            }
            CardRechargeBank.AGRICULTURAL_BANK -> {
                behaviorReporter.organic(AppActionId.SUBMIT_CARD_RECHARGE)
                viewModel.charge(amount)
            }
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
        if (selectedRechargeBank == CardRechargeBank.CHINA_MERCHANTS_BANK) {
            (context as? android.app.Activity)?.let(CmbRechargeAutomationController::schedulePreload)
        }
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
            viewModel.load()
        }
    }

    LaunchedEffect(paymentState, selectedRechargeBank) {
        if (paymentState is PaymentState.Success) {
            delay(1_000L)
            viewModel.load()
            delay(PAYMENT_RESULT_DISPLAY_DURATION_MS - 1_000L)
            if (selectedRechargeBank == CardRechargeBank.CHINA_MERCHANTS_BANK) {
                CmbRechargeAutomationController.resetPaymentState()
            } else {
                viewModel.resetPaymentState()
            }
        } else if (paymentState is PaymentState.Error) {
            delay(PAYMENT_RESULT_DISPLAY_DURATION_MS)
            if (selectedRechargeBank == CardRechargeBank.CHINA_MERCHANTS_BANK) {
                CmbRechargeAutomationController.resetPaymentState()
            } else {
                viewModel.resetPaymentState()
            }
        }
    }
    val canConfirm = paymentState == PaymentState.Idle && when (selectedRechargeBank) {
        CardRechargeBank.ALIPAY -> true
        CardRechargeBank.CHINA_MERCHANTS_BANK,
        CardRechargeBank.AGRICULTURAL_BANK -> {
            amount.toDoubleOrNull()?.let { it > 0.0 } == true &&
                accountState is CardAccountState.Ready
        }
        null -> false
    }

    AppScrollablePageLayout(
        title = "校园卡充值",
        onBack = { navController.popBackStack() },
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1),
        bottomPadding = 48.dp
    ) {
        if (LocalAppUiTheme.current != AppUiTheme.MATERIAL) {
            AppSelectField(
                label = "充值方式",
                selected = selectedRechargeBank,
                options = CardRechargeBank.entries.map { method ->
                    AppSelectOption(method, method.displayName)
                },
                onSelected = ::selectRechargeBank,
                modifier = Modifier.padding(horizontal = 16.dp),
                enabled = paymentState != PaymentState.Loading,
                valueTextAlign = TextAlign.End,
                miuixInsideMargin = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
                miuixStandalone = true
            )
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .appLiquidGlassSurface(
                    shape = AppComponentTokens.CardShape,
                    fallbackColor = 100.n1 withNight 20.n1,
                    level = LiquidGlassSurfaceLevel.Panel
                )
        ) {
            if (LocalAppUiTheme.current == AppUiTheme.MATERIAL) {
                SettingsSelectRow(
                    title = "充值方式",
                    selected = selectedRechargeBank,
                    choices = CardRechargeBank.entries.map { method ->
                        SettingsChoice<CardRechargeBank?>(method, method.displayName)
                    },
                    onSelected = { method -> method?.let(::selectRechargeBank) },
                    showDivider = false
                )
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = "校园卡账户",
                    style = MaterialTheme.typography.titleMedium
                )

                when (val state = accountState) {
                    CardAccountState.Loading -> {
                        AppCircularProgressIndicator(
                            size = 18.dp,
                            strokeWidth = 2.dp,
                            color = 30.n1 withNight 70.n1
                        )
                    }

                    is CardAccountState.Ready -> {
                        val accountInfo = state.cardInfo.data.card.getOrNull(0)?.accinfo
                            ?.getOrNull(0)
                        Text(
                            text = accountInfo?.let { "${it.name} ${it.type}" } ?: "--"
                        )
                    }

                    is CardAccountState.Error -> {
                        Text(
                            text = "加载失败",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "账户余额", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = cardInfo.value?.data?.card?.getOrNull(0)
                        ?.accinfo?.getOrNull(0)?.balance?.let { String.format("￥%.2f", it / 100.0) }
                        ?: "￥--",
                    style = MaterialTheme.typography.titleMedium
                )
            }

        }

        if (selectedRechargeBank != CardRechargeBank.ALIPAY) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "充值金额",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AppTextField(
                    value = amount,
                    onValueChange = { newText ->
                        if (newText.isEmpty()) {
                            amount = newText
                            return@AppTextField
                        }

                        val regex = Regex("^\\d*\\.?\\d{0,2}$")
                        if (regex.matches(newText)) {
                            amount = newText
                        }
                    },
                    label = "金额（元）",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedRechargeBank == CardRechargeBank.ALIPAY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "复制校园卡信息",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AppToggle(
                        checked = copyCampusCardInfo,
                        onCheckedChange = { copyCampusCardInfo = it },
                        contentDescription = "复制校园卡信息"
                    )
                }
            }
            when (val state = paymentState) {
                PaymentState.Idle -> Unit
                PaymentState.Loading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppCircularProgressIndicator(size = 24.dp, strokeWidth = 3.dp)
                    Text("正在提交充值", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is PaymentState.Error -> Text(
                    text = "充值失败：${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                is PaymentState.Success -> Text(
                    text = if (selectedRechargeBank == CardRechargeBank.CHINA_MERCHANTS_BANK) {
                        "充值成功，请刷卡将过渡余额转入校园卡"
                    } else {
                        "充值成功，订单号：${state.orderId}"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            AppButton(
                onClick = ::submitRecharge,
                modifier = Modifier.fillMaxWidth(),
                enabled = canConfirm,
                variant = AppButtonVariant.Primary
            ) {
                Text(
                    when {
                        paymentState == PaymentState.Loading -> "正在充值"
                        selectedRechargeBank == CardRechargeBank.ALIPAY -> "前往支付宝"
                        else -> "确认充值"
                    }
                )
            }
        }

        if (showAlipayConfirmDialog) {
            AppDialogSurface(
                onDismissRequest = { showAlipayConfirmDialog = false }
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("前往支付宝充值", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "确认打开支付宝校园卡充值页面？",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        AppButton(
                            onClick = { showAlipayConfirmDialog = false },
                            variant = AppButtonVariant.Secondary
                        ) { Text("取消") }
                        AppButton(
                            onClick = {
                                behaviorReporter.organic(AppActionId.SUBMIT_CARD_RECHARGE)
                                if (copyCampusCardInfo) {
                                    val identityState = copyCampusCardIdentity(
                                        context = context,
                                        name = campusCardUserName,
                                        studentId = campusCardStudentId
                                    )
                                    val message = when (identityState) {
                                        CampusCardIdentityCopyState.Complete -> "已复制姓名和学号"
                                        CampusCardIdentityCopyState.Partial -> "本地信息不完整，已复制可用信息"
                                        CampusCardIdentityCopyState.Empty -> "本地未找到姓名和学号，请在支付宝中手动填写"
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                                openAlipayCampusCard(context)
                                showAlipayConfirmDialog = false
                            }
                        ) { Text("确认") }
                    }
                }
            }
        }

        if (cmbRechargeState.phase == CmbRechargePaymentPhase.PASSWORD_REQUIRED) {
            CmbRechargeQueryPasswordDialog(
                onCancel = CmbRechargeAutomationController::cancelPassword,
                onConfirm = CmbRechargeAutomationController::submitPassword
            )
        }

    }

}

private val CardRechargeBank.displayName: String
    get() = when (this) {
        CardRechargeBank.AGRICULTURAL_BANK -> "中国农业银行"
        CardRechargeBank.CHINA_MERCHANTS_BANK -> "招商银行"
        CardRechargeBank.ALIPAY -> "支付宝"
    }

private fun CmbRechargeAutomationState.toPaymentState(): PaymentState = when (phase) {
    CmbRechargePaymentPhase.IDLE,
    CmbRechargePaymentPhase.PASSWORD_REQUIRED -> PaymentState.Idle

    CmbRechargePaymentPhase.LOADING -> PaymentState.Loading
    CmbRechargePaymentPhase.SUCCESS -> PaymentState.Success("招商银行")
    CmbRechargePaymentPhase.ERROR -> PaymentState.Error(
        errorMessage ?: "招商银行充值失败，请重试"
    )
}

private enum class CampusCardIdentityCopyState {
    Complete,
    Partial,
    Empty
}

private fun copyCampusCardIdentity(
    context: Context,
    name: String,
    studentId: String
): CampusCardIdentityCopyState {
    val trimmedName = name.trim()
    val trimmedStudentId = studentId.trim()
    if (trimmedName.isEmpty() && trimmedStudentId.isEmpty()) {
        return CampusCardIdentityCopyState.Empty
    }

    val clipText = buildList {
        if (trimmedName.isNotEmpty()) add("姓名：$trimmedName")
        if (trimmedStudentId.isNotEmpty()) add("学号：$trimmedStudentId")
    }.joinToString("\n")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("校园卡信息", clipText))

    return if (trimmedName.isNotEmpty() && trimmedStudentId.isNotEmpty()) {
        CampusCardIdentityCopyState.Complete
    } else {
        CampusCardIdentityCopyState.Partial
    }
}

private const val PAYMENT_RESULT_DISPLAY_DURATION_MS = 3_000L

private fun openAlipayCampusCard(context: Context) {
    val openedAlipay = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_CAMPUS_CARD_SCHEME))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    if (openedAlipay) return

    val openedFallback = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_CAMPUS_CARD_FALLBACK_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    if (!openedFallback) {
        Toast.makeText(context, "无法打开支付宝校园卡，请稍后重试", Toast.LENGTH_SHORT).show()
    }
}
