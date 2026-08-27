package com.ahu.ahutong.ui.screen.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.CardRechargeBank
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.CardAccountState
import com.ahu.ahutong.ui.state.CardBalanceDepositViewModel
import com.ahu.ahutong.ui.state.PaymentState
import com.kyant.monet.a1
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
    var rechargeMethodMenuExpanded by remember { mutableStateOf(selectedRechargeBank == null) }

    val context = LocalContext.current
    val rechargeMethodMenuMinWidth = LocalConfiguration.current.screenWidthDp.dp * 0.5f
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
        rechargeMethodMenuExpanded = false
        AHUCache.setCardRechargeBank(bank)
        if (bank == CardRechargeBank.ALIPAY) copyCampusCardInfo = false
        viewModel.resetPaymentState()
        CmbRechargeAutomationController.resetPaymentState()
        CmbRechargeAutomationController.onBankSelected(context, bank)
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
            viewModel.load()
        }
    }

    LaunchedEffect(paymentState, selectedRechargeBank) {
        if (paymentState is PaymentState.Success || paymentState is PaymentState.Error) {
            delay(PAYMENT_RESULT_DISPLAY_DURATION_MS)
            if (selectedRechargeBank == CardRechargeBank.CHINA_MERCHANTS_BANK) {
                CmbRechargeAutomationController.resetPaymentState()
            } else {
                viewModel.resetPaymentState()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        Text(
            text = "校园卡充值",
            modifier = Modifier.padding(24.dp, 32.dp),
            style = MaterialTheme.typography.headlineMedium
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(16.dp))
                .background(100.n1 withNight 20.n1)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "充值方式",
                    style = MaterialTheme.typography.titleMedium
                )
                ExposedDropdownMenuBox(
                    expanded = rechargeMethodMenuExpanded,
                    onExpandedChange = {
                        if (paymentState != PaymentState.Loading) {
                            rechargeMethodMenuExpanded = !rechargeMethodMenuExpanded
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = paymentState != PaymentState.Loading
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedRechargeBank?.displayName ?: "请选择",
                            color = 30.n1 withNight 70.n1
                        )
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = rechargeMethodMenuExpanded
                        )
                    }
                    ExposedDropdownMenu(
                        expanded = rechargeMethodMenuExpanded,
                        onDismissRequest = {
                            if (selectedRechargeBank != null) rechargeMethodMenuExpanded = false
                        },
                        modifier = Modifier.widthIn(min = rechargeMethodMenuMinWidth),
                        matchAnchorWidth = false,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        CardRechargeBank.entries.forEach { method ->
                            val isSelected = method == selectedRechargeBank
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = method.displayName,
                                        color = 10.n1 withNight 90.n1
                                    )
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    } else {
                                        Color.Transparent
                                    }
                                ),
                                onClick = { selectRechargeBank(method) }
                            )
                        }
                    }
                }
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
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
                            color = Color.Red
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
                    .fillMaxWidth()
                    .clip(SmoothRoundedCornerShape(16.dp))
                    .background(100.n1 withNight 20.n1),
            ) {

            Text(
                text = "充值金额",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium

            )

                TextField(
                    value = amount,
                    onValueChange = { newText ->
                        if (newText.isEmpty()) {
                            amount = newText
                            return@TextField
                        }

                        val regex = Regex("^\\d*\\.?\\d{0,2}$")
                        if (regex.matches(newText)) {
                            amount = newText
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    placeholder = { Text("请输入金额", color = 30.n1 withNight 70.n1) },
                    textStyle = TextStyle(fontSize = 16.sp, color = 10.n1 withNight 90.n1),

                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true
                )
            }
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (selectedRechargeBank == CardRechargeBank.ALIPAY) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.End
            }
        ) {
            if (selectedRechargeBank == CardRechargeBank.ALIPAY) {
                Row(
                    modifier = Modifier
                        .clip(SmoothRoundedCornerShape(12.dp))
                        .toggleable(
                            value = copyCampusCardInfo,
                            role = Role.Checkbox,
                            onValueChange = { copyCampusCardInfo = it }
                        )
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = copyCampusCardInfo,
                        onCheckedChange = null
                    )
                    Text(
                        text = "复制校园卡信息",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(SmoothRoundedCornerShape(32.dp))
                    .background(
                        animateColorAsState(
                            targetValue = when (paymentState) {
                                PaymentState.Idle -> 90.a1 withNight 85.a1
                                PaymentState.Loading -> 70.a1 withNight 60.a1
                                is PaymentState.Error -> Color.Red
                                is PaymentState.Success -> 70.a1 withNight 60.a1
                            }
                        ).value
                    )
                    .animateContentSize(spring(stiffness = Spring.StiffnessLow))
            ) {
                when (val state = paymentState) {
                    PaymentState.Idle -> {
                        CompositionLocalProvider(LocalIndication provides ripple(color = 0.n1)) {
                            Text(
                                text = if (selectedRechargeBank == CardRechargeBank.ALIPAY) {
                                    "前往支付宝"
                                } else {
                                    "确认"
                                },
                                modifier = Modifier
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            when (selectedRechargeBank) {
                                                CardRechargeBank.ALIPAY -> {
                                                    showAlipayConfirmDialog = true
                                                }

                                                CardRechargeBank.CHINA_MERCHANTS_BANK -> {
                                                    if (amount.isNotEmpty() &&
                                                        accountState is CardAccountState.Ready
                                                    ) {
                                                        behaviorReporter.organic(
                                                            AppActionId.SUBMIT_CMB_CARD_RECHARGE
                                                        )
                                                        CmbRechargeAutomationController.submit(
                                                            context = context,
                                                            amount = amount
                                                        )
                                                    } else if (amount.isNotEmpty()) {
                                                        Toast.makeText(
                                                            context,
                                                            "校园卡账户仍在加载，请稍后重试",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }

                                                CardRechargeBank.AGRICULTURAL_BANK -> {
                                                    if (amount.isNotEmpty() &&
                                                        accountState is CardAccountState.Ready
                                                    ) {
                                                        behaviorReporter.organic(
                                                            AppActionId.SUBMIT_CARD_RECHARGE
                                                        )
                                                        viewModel.charge(amount)
                                                    } else if (amount.isNotEmpty()) {
                                                        Toast.makeText(
                                                            context,
                                                            "校园卡账户仍在加载，请稍后重试",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }

                                                null -> {
                                                    rechargeMethodMenuExpanded = true
                                                }
                                            }
                                        }
                                    )
                                    .padding(24.dp, 16.dp),
                                color = 0.n1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }


                    PaymentState.Loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(56.dp),
                                color = 100.n1,
                                strokeWidth = 6.dp
                            )
                            Text(
                                text = "支付中",
                                modifier = Modifier.padding(4.dp),
                                color = 100.n1,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }

                    is PaymentState.Error -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = 100.n1
                            )
                            Text(
                                text = "支付失败！错误信息：${state.message}",
                                modifier = Modifier.padding(4.dp),
                                color = 100.n1,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }

                    is PaymentState.Success -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {


                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = 100.n1
                            )
                            Text(
                                text = if (
                                    selectedRechargeBank == CardRechargeBank.CHINA_MERCHANTS_BANK
                                ) {
                                    "支付成功！请刷卡将过渡余额转入校园卡"
                                } else {
                                    "支付成功！订单号：${state.orderId}"
                                },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable {
                                        if (
                                            selectedRechargeBank ==
                                            CardRechargeBank.CHINA_MERCHANTS_BANK
                                        ) {
                                            CmbRechargeAutomationController.resetPaymentState()
                                        } else {
                                            viewModel.resetPaymentState()
                                        }
                                    },
                                color = 100.n1,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }

                }
            }
        }


        if (showAlipayConfirmDialog) {
            AlertDialog(
                containerColor = 100.n1 withNight 20.n1,
                titleContentColor = 10.n1 withNight 90.n1,
                onDismissRequest = { showAlipayConfirmDialog = false },
                title = { Text("前往支付宝充值") },
                text = {
                    Text(
                        "确认打开支付宝校园卡充值页面？",
                        color = 40.n1 withNight 60.n1
                    )
                },
                confirmButton = {
                    Text(
                        text = "确认",
                        modifier = Modifier
                            .clickable {
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
                            .padding(8.dp),
                        color = 10.n1 withNight 90.n1
                    )
                },
                dismissButton = {
                    Text(
                        text = "取消",
                        modifier = Modifier
                            .clickable { showAlipayConfirmDialog = false }
                            .padding(8.dp),
                        color = 10.n1 withNight 90.n1
                    )
                }
            )
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
