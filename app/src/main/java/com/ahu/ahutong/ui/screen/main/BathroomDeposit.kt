package com.ahu.ahutong.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.ui.component.SecurePaymentPasswordDialog
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppComponentTokens
import com.ahu.ahutong.ui.components.AppScrollablePageLayout
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.state.BathroomDepositViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.delay

@Composable
fun BathroomDeposit(
    onBack: () -> Unit,
    viewmodel: BathroomDepositViewModel = viewModel()
) {
    val behaviorReporter = rememberBehaviorActionReporter()
    val payState by viewmodel.payState.collectAsState()
    val info by viewmodel.info.collectAsState()
    val isQuerying by viewmodel.isQuerying.collectAsState()
    val focusManager = LocalFocusManager.current

    val bathrooms = remember { listOf("竹园/龙河", "桔园/蕙园") }
    val bathroomOptions = remember(bathrooms) {
        bathrooms.map { AppSelectOption(it, it) }
    }
    var bathroom by rememberSaveable { mutableStateOf(bathrooms.first()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var phoneHasFocus by rememberSaveable { mutableStateOf(false) }
    var previousPhone by rememberSaveable { mutableStateOf<String?>(null) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        previousPhone = AHUCache.getPhone()?.takeIf(String::isNotBlank)
    }
    LaunchedEffect(bathroom, phone) {
        if (phone.length == 11) {
            delay(250)
            viewmodel.getBathroomInfo(bathroom, phone)
        }
    }
    LaunchedEffect(payState) {
        if (payState is PayState.Succeeded || payState is PayState.Failed) {
            delay(PAYMENT_RESULT_DISPLAY_DURATION_MS)
            viewmodel.resetPaymentState()
        }
    }

    val accountSummary = info?.let { response ->
        when {
            response.data.map == null -> response.data.message ?: "未查询到浴室账户"
            response.data.map!!.showData != null -> response.data.map!!.showData!!.let { data ->
                "${data.phone}  ·  现金 ${data.cashAmount} 元  ·  赠送 ${data.giftAmount} 元"
            }
            response.data.map!!.data?.message != null -> response.data.map!!.data!!.message!!
            else -> "未查询到浴室账户"
        }
    }
    val accountData = info?.data?.map?.data
    val balanceData = info?.data?.map?.showData
    val canSubmit = amount.toDoubleOrNull()?.let { it > 0.0 } == true && accountData != null &&
        payState !is PayState.InProgress

    AppScrollablePageLayout(
        title = "浴室缴费",
        onBack = onBack,
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1),
        bottomPadding = 48.dp
    ) {
        AppSelectField(
            label = "浴室",
            selected = bathroom,
            options = bathroomOptions,
            onSelected = { selected ->
                bathroom = selected
                viewmodel.clearBathroomInfo()
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            miuixInsideMargin = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                top = 16.dp,
                end = 20.dp,
                bottom = 16.dp
            ),
            miuixStandalone = true,
            liquidLabelWeight = 0.85f,
            liquidValueWeight = 1.15f
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppTextField(
                value = phone,
                onValueChange = { input ->
                    val nextPhone = input.filter(Char::isDigit).take(11)
                    if (nextPhone != phone) {
                        phone = nextPhone
                        viewmodel.clearBathroomInfo()
                    }
                },
                label = "手机号",
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState -> phoneHasFocus = focusState.isFocused },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewmodel.getBathroomInfo(bathroom, phone)
                    }
                )
            )
            AppButton(
                onClick = {
                    focusManager.clearFocus()
                    viewmodel.getBathroomInfo(bathroom, phone)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phone.length == 11 && !isQuerying
            ) {
                Text("查询")
            }

            AnimatedVisibility(visible = previousPhone != null && !phoneHasFocus) {
                AppButton(
                    onClick = {
                        val cachedPhone = previousPhone ?: return@AppButton
                        phone = cachedPhone
                        previousPhone = null
                        viewmodel.clearBathroomInfo()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppButtonVariant.Secondary
                ) {
                    Text("使用上次充值手机号 · ${previousPhone.orEmpty()}")
                }
            }
        }

        AnimatedVisibility(visible = isQuerying || info != null) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .appLiquidGlassSurface(
                        shape = AppComponentTokens.CardShape,
                        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        level = LiquidGlassSurfaceLevel.Control
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("浴室账户", style = MaterialTheme.typography.titleMedium)
                if (isQuerying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppCircularProgressIndicator(size = 22.dp, strokeWidth = 3.dp)
                        Text("正在查询账户与余额", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (balanceData != null) {
                    Text(
                        text = accountData?.name?.takeIf(String::isNotBlank)
                            ?: accountData?.identifier?.takeIf(String::isNotBlank)
                            ?: balanceData.phone,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "现金 ${balanceData.cashAmount} 元  ·  赠送 ${balanceData.giftAmount} 元",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = accountSummary ?: "未查询到浴室账户",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "缴费金额",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            AppTextField(
                value = amount,
                onValueChange = { input ->
                    if (input.isEmpty() || Regex("^\\d*\\.?\\d{0,2}$").matches(input)) {
                        amount = input
                    }
                },
                label = "金额（元）",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = payState) {
                PayState.Idle -> Unit
                PayState.InProgress -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppCircularProgressIndicator(size = 24.dp, strokeWidth = 3.dp)
                    Text("  正在提交缴费", style = MaterialTheme.typography.bodyLarge)
                }
                is PayState.Failed -> Text(
                    text = "缴费失败：${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                is PayState.Succeeded -> Text(
                    text = "缴费成功，订单号：${state.message}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            AppButton(
                onClick = { showPasswordDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit
            ) {
                Text(if (payState is PayState.InProgress) "正在支付" else "确认缴费")
            }
        }
    }

    if (showPasswordDialog) {
        SecurePaymentPasswordDialog(
            password = password,
            onPasswordChange = {
                password = it
                passwordError = null
            },
            title = "请输入校园卡密码",
            errorMessage = passwordError,
            onDismissRequest = {
                showPasswordDialog = false
                password = ""
                passwordError = null
            },
            onConfirm = { confirmedPassword ->
                if (confirmedPassword.length == 6) {
                    showPasswordDialog = false
                    behaviorReporter.organic(AppActionId.CONFIRM_BATHROOM_PAYMENT)
                    viewmodel.pay(
                        bathroom = bathroom,
                        amount = amount,
                        password = confirmedPassword
                    )
                } else {
                    passwordError = "密码必须是 6 位数字"
                }
            }
        )
    }
}

private const val PAYMENT_RESULT_DISPLAY_DURATION_MS = 3_000L
