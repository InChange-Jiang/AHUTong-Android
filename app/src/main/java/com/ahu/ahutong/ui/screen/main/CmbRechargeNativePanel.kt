package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.component.SecurePaymentPasswordDialog
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import java.util.Locale

internal data class CmbRechargeNativeData(
    val studentNumber: String,
    val balance: Double,
    val paymentMethods: List<CmbRechargePaymentMethod>
)

internal data class CmbRechargePaymentMethod(
    val pageIndex: Int,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CmbRechargeNativePanel(
    data: CmbRechargeNativeData?,
    errorMessage: String?,
    isSubmitting: Boolean,
    onRetry: () -> Unit,
    onManagePaymentMethods: () -> Unit,
    onSubmit: (amount: String, paymentMethodIndex: Int) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedPaymentMethodIndex by remember { mutableIntStateOf(-1) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(data?.paymentMethods) {
        val methods = data?.paymentMethods.orEmpty()
        if (methods.none { it.pageIndex == selectedPaymentMethodIndex }) {
            selectedPaymentMethodIndex = methods.firstOrNull()?.pageIndex ?: -1
        }
    }

    val amountValue = amount.toDoubleOrNull()
    val amountError = when {
        amount.isBlank() -> null
        amountValue == null || amountValue <= 0.0 -> "请输入有效的充值金额"
        amountValue > CMB_RECHARGE_MAX_AMOUNT -> "单次充值金额不能超过 1000 元"
        else -> null
    }
    val selectedMethod = data?.paymentMethods
        ?.firstOrNull { it.pageIndex == selectedPaymentMethodIndex }
    val canSubmit = data != null &&
        selectedMethod != null &&
        amountValue != null &&
        amountValue > 0.0 &&
        amountValue <= CMB_RECHARGE_MAX_AMOUNT &&
        !isSubmitting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            data == null && errorMessage != null -> NativeRechargeLoadState(
                title = "充值信息加载失败",
                message = errorMessage,
                onRetry = onRetry
            )

            data == null -> NativeRechargeLoadState(
                title = "正在加载充值信息",
                message = "正在安全连接校园卡充值服务，请稍候。"
            )

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (errorMessage != null) {
                        item {
                            NativeRechargeMessageCard(
                                title = "本次充值未完成",
                                message = errorMessage,
                                actionText = "重新加载",
                                onAction = onRetry
                            )
                        }
                    }

                    item { NativeRechargeAccountCard(data = data) }

                    item {
                        NativeRechargeSection(title = "充值金额") {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { value ->
                                    if (value.matches(Regex("^\\d{0,4}(\\.\\d{0,2})?$"))) {
                                        amount = value
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("充值金额") },
                                prefix = { Text("¥ ") },
                                placeholder = { Text("请输入金额") },
                                supportingText = amountError?.let { message -> { Text(message) } },
                                isError = amountError != null,
                                singleLine = true,
                                enabled = !isSubmitting,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                )
                            )

                            CMB_RECHARGE_PRESETS.chunked(2).forEach { presets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    presets.forEach { preset ->
                                        OutlinedButton(
                                            onClick = {
                                                amount = preset
                                                focusManager.clearFocus()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            enabled = !isSubmitting,
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text("¥$preset")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        NativeRechargeSection(title = "支付方式") {
                            if (data.paymentMethods.isEmpty()) {
                                Text(
                                    text = "尚未绑定可用的免密支付方式，请先前往学校支付页面完成绑定。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = onManagePaymentMethods,
                                    enabled = !isSubmitting
                                ) {
                                    Text("管理免密支付方式")
                                }
                            } else {
                                AppSelectField(
                                    label = "扣款方式",
                                    selected = selectedMethod?.pageIndex,
                                    options = data.paymentMethods.map { method ->
                                        AppSelectOption(method.pageIndex, method.name)
                                    },
                                    onSelected = { selectedPaymentMethodIndex = it },
                                    enabled = !isSubmitting
                                )
                                TextButton(
                                    onClick = onManagePaymentMethods,
                                    enabled = !isSubmitting
                                ) {
                                    Text("管理支付方式")
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "充值金额将先进入过渡余额，刷卡后转入校园卡。银行卡授权与验证码只在官方页面完成；如需校园卡查询密码，本页会将其安全转交当前校方充值页面。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit(amount, selectedPaymentMethodIndex)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                        enabled = canSubmit
                    ) {
                        if (isSubmitting) {
                            AppCircularProgressIndicator(
                                size = 24.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("确认充值")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NativeRechargeLoadState(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onRetry == null) {
                AppCircularProgressIndicator(size = 36.dp)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            onRetry?.let { retry ->
                Button(onClick = retry) { Text("重试") }
            }
        }
    }
}

@Composable
private fun NativeRechargeAccountCard(data: CmbRechargeNativeData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "校园卡当前余额",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = String.format(Locale.CHINA, "¥ %.2f", data.balance),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium
            )
            NativeRechargeInfoRow(
                label = "学工号",
                value = data.studentNumber.ifBlank { "未获取到" }
            )
        }
    }
}

@Composable
private fun NativeRechargeSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun NativeRechargeInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NativeRechargeMessageCard(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.titleMedium
            )
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

private const val CMB_RECHARGE_MAX_AMOUNT = 1_000.0
private val CMB_RECHARGE_PRESETS = listOf("50", "100", "200", "500")

@Composable
internal fun CmbRechargeQueryPasswordDialog(
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    SecurePaymentPasswordDialog(
        password = password,
        onPasswordChange = { password = it },
        title = "输入校园卡查询密码",
        onDismissRequest = onCancel,
        onConfirm = onConfirm
    )
}

@Composable
internal fun CmbRechargeNativeSuccessPanel(
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "充值成功",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "订单已由招商银行免密支付完成，余额将在刷卡后转入校园卡。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("返回校园卡")
        }
    }
}
