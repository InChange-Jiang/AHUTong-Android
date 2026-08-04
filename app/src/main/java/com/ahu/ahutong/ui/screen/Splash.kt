package com.ahu.ahutong.ui.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.SplashViewModel
import com.ahu.ahutong.ui.state.BootstrapTrainingOnboardingState
import com.ahu.ahutong.ui.state.TelemetryOnboardingState
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun Splash(
    navController: NavController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    var dialogRevision by remember { mutableIntStateOf(0) }
    val telemetryState by viewModel.telemetryOnboardingState.collectAsState()
    val bootstrapTrainingState by viewModel.bootstrapTrainingOnboardingState.collectAsState()
    val activity = LocalActivity.current

    val agreementAccepted = AHUCache.isAgreementAccepted()
    val privacyAccepted = AHUCache.isPrivacyAccepted()
    val businessAccepted = AHUCache.isBusinessAccepted()
    val telemetryChoice = (telemetryState as? TelemetryOnboardingState.Ready)?.choice
    val bootstrapTrainingChoice =
        (bootstrapTrainingState as? BootstrapTrainingOnboardingState.Ready)?.choice

    LaunchedEffect(dialogRevision, telemetryState, bootstrapTrainingState) {
        if (agreementAccepted && privacyAccepted && businessAccepted &&
            telemetryChoice != null && bootstrapTrainingChoice != null
        ) {
            if (AHUCache.isLogin()) {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            } else {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    when {
        !agreementAccepted -> AgreementDialog(
            onAgree = {
                AHUCache.setAgreementAccepted()
                dialogRevision++
            },
            onDisagree = { activity?.finish() }
        )
        !privacyAccepted -> PrivacyDialog(
            onAgree = {
                AHUCache.setPrivacyAccepted()
                dialogRevision++
            },
            onDisagree = { activity?.finish() }
        )
        !businessAccepted -> BusinessDialog(
            onAgree = {
                AHUCache.setBusinessAccepted()
                dialogRevision++
            },
            onDisagree = { activity?.finish() }
        )
        telemetryState is TelemetryOnboardingState.Ready && telemetryChoice == null ->
            ModelQualityTelemetryOnboardingDialog(
                onAgree = { viewModel.chooseModelQualityTelemetry(true) },
                onSkip = { viewModel.chooseModelQualityTelemetry(false) }
            )
        bootstrapTrainingState is BootstrapTrainingOnboardingState.Ready &&
            bootstrapTrainingChoice == null -> BootstrapTrainingOnboardingDialog(
                onAgree = { includeHistorical ->
                    viewModel.chooseBootstrapTraining(true, includeHistorical)
                },
                onSkip = { viewModel.chooseBootstrapTraining(false, false) }
            )
    }
}

@Composable
private fun BootstrapTrainingOnboardingDialog(
    onAgree: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    var includeHistorical by remember { mutableStateOf(false) }
    OnboardingDialogTemplate(
        title = "帮助训练通用预测模型",
        body = "开启后，应用会把本机已经生成、可直接用于训练的去标识化样本上传到安大通服务器，包括下一步和多跳预测的数值特征、候选可用性、目标标签，以及参数推荐的候选排序特征和低权重反馈。不会上传学号、账号、设备标识、原始页面轨迹、完整旅程、设置值、参数内容、presetId、指纹或模型权重。每次授权会生成随机参与者编号，只用于按用户隔离训练/验证/测试集和将来的联邦学习模拟；关闭后会停止收集并请求删除该编号下已上传的数据。暂不开启不影响本地预测或其他功能。",
        confirmText = "同意贡献",
        dismissText = "暂不开启",
        onConfirm = { onAgree(includeHistorical) },
        onDismiss = onSkip,
        onDismissRequest = onSkip,
        buttonWidth = 104.dp,
        extraContent = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = includeHistorical,
                    onCheckedChange = { includeHistorical = it }
                )
                Text(
                    text = "同时贡献最近 30 天已有的兼容样本（默认关闭）",
                    style = MaterialTheme.typography.bodySmall,
                    color = 10.n1 withNight 90.n1
                )
            }
        }
    )
}

@Composable
private fun ModelQualityTelemetryOnboardingDialog(
    onAgree: () -> Unit,
    onSkip: () -> Unit
) {
    OnboardingDialogTemplate(
        title = "帮助改进模型质量",
        body = "你可以选择帮助评估端侧个性化功能。开启后，每类任务至少积累 64 条新有效样本时，应用每天最多一次上传去标识化聚合指标，包括普通下一步、多跳目标、参数排序和候选模型的准确率、误差与置信度分桶，以及针对性/普通建议的聚合展示、点击、完成、关闭、超时和门禁计数。自然 holdout 评估与推荐辅助反馈会分开统计；单个动作等细分项不足 30 条不会上传。不会上传原始行为、页面或旅程序列、逐次标签、逐次概率、特征向量、设置值、参数内容或指纹、模型权重、学号、账号或硬件标识。服务端仅按版本和任务聚合，初始保存期限最多 90 天。暂不开启不影响本地预测、训练或其他功能。",
        confirmText = "同意开启",
        dismissText = "暂不开启",
        onConfirm = onAgree,
        onDismiss = onSkip,
        onDismissRequest = onSkip,
        buttonWidth = 104.dp
    )
}


@Composable
fun AgreementDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    OnboardingDialogTemplate(
        title = "温馨提示与免责声明",
        body = """
            1. 本项目完全开源，任何人均可基于本项目进行二次开发或分发。
            2. 由于开源特性，非官方渠道下载或安装的应用可能存在安全风险，请务必确保应用来源可信。
            3. 本项目不会默认上传用户的个人或学业数据；只有在用户另行、明确选择模型质量评估或通用模型训练数据贡献后，才按对应说明处理去标识化聚合指标或模型就绪样本，并可随时撤回。
            4. 用户在使用本项目或其二次开发版本时，应自行判断安全性并承担相应风险。因非官方或非正版应用造成的财产损失，开发者不承担任何责任。
            5. 使用本应用即表示您已阅读并理解本免责声明，并同意自行承担使用风险。
        """.trimIndent(),
        confirmText = "同意",
        dismissText = "拒绝",
        onConfirm = onAgree,
        onDismiss = onDisagree
    )
}

@Composable
fun PrivacyDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    OnboardingDialogTemplate(
        title = "隐私政策",
        body = """
            1. 安大通不会把学号、账号、课表、成绩、交易内容等个人或学业数据上传到自有云服务器。
            2. 个性化学习记录默认只保存在本机。“帮助改进模型质量”只上传达到门槛的去标识化聚合指标；“帮助训练通用预测模型”是另一项独立、可选的授权，会上传模型就绪的数值特征、标签、候选可用性和分级反馈。
            3. 训练数据贡献不会上传原始页面轨迹、完整旅程、设置值、参数内容、presetId、指纹、学号、账号、设备标识或模型权重。每个授权周期使用随机参与者编号，关闭后停止收集并请求删除该编号下的数据。
            4. 两项可选上传均可拒绝，不影响本地预测和其他功能，并可在设置中随时关闭和删除已上传数据。
            5. 您的个人数据不会被分享给第三方；学校业务接口仅用于完成您主动发起的学校服务请求。
        """.trimIndent(),
        confirmText = "同意",
        dismissText = "拒绝",
        onConfirm = onAgree,
        onDismiss = onDisagree
    )
}

@Composable
fun BusinessDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    OnboardingDialogTemplate(
        title = "商业合作",
        body = """
            目前安大通的商业价值处于探索阶段，为了持久化发展、优化广大同学的体验，急需几名大一/大二的同学做发展规划。
            如果您有兴趣，欢迎联系我们！QQ群1006203134
            另外，如果您对安大通有任何想法或建议，也欢迎加群反馈！
        """.trimIndent(),
        confirmText = "同意",
        dismissText = "拒绝",
        onConfirm = onAgree,
        onDismiss = onDisagree
    )
}

@Composable
private fun OnboardingDialogTemplate(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDismissRequest: () -> Unit = {},
    buttonWidth: Dp = 88.dp,
    extraContent: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = 10.n1 withNight 90.n1
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = 10.n1 withNight 90.n1
                )
                if (extraContent != null) {
                    Box(modifier = Modifier.padding(top = 12.dp)) { extraContent() }
                }
            }
        },
        shape = SmoothRoundedCornerShape(32.dp),
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                modifier = Modifier.size(buttonWidth, 56.dp),
                shape = SmoothRoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = 90.a1 withNight 85.a1,
                    contentColor = 0.n1
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.size(buttonWidth, 56.dp),
                shape = SmoothRoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = 90.a1 withNight 85.a1,
                    contentColor = 0.n1
                )
            ) {
                Text(dismissText)
            }
        },
        containerColor = 100.n1 withNight 20.n1
    )
}
