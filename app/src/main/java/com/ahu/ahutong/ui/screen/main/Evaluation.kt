package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.data.model.EvalQuestion
import com.ahu.ahutong.data.model.EvalTask
import com.ahu.ahutong.data.model.EvalTeacher
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppHeaderIconButton
import com.ahu.ahutong.ui.components.AppLazyPageLayout
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.EvaluationViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Settings

@Composable
fun Evaluation(
    viewModel: EvaluationViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        viewModel.loadSemesters()
    }

    val context = LocalContext.current
    val errorMessage by viewModel.errorMessage.collectAsState()
    val presetActionMessage by viewModel.presetActionMessage.collectAsState()
    val currentTask by viewModel.currentTask.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(presetActionMessage) {
        presetActionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.presetActionMessage.value = null
        }
    }

    if (currentTask != null) {
        EvaluationFormScreen(viewModel)
    } else {
        EvaluationListScreen(viewModel, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvaluationListScreen(
    viewModel: EvaluationViewModel,
    onBack: (() -> Unit)?
) {
    val semesters by viewModel.semesters.collectAsState()
    val selectedSemesterId by viewModel.selectedSemesterId.collectAsState()
    val taskItems by viewModel.taskItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val isBulkSubmitting by viewModel.isBulkSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var presetDialogShown by remember { mutableStateOf(false) }
    var confirmBulkSubmitShown by remember { mutableStateOf(false) }
    val presetTargetCount = remember(taskItems) {
        taskItems.sumOf { item ->
            item.taskList.sumOf { task ->
                if (!task.timeStatus) {
                    0
                } else {
                    task.teachers.count { teacher -> teacher.status == "TO_REVIEW" }
                }
            }
        }
    }
    val hasPresetTargets = presetTargetCount > 0

    if (presetDialogShown) {
        EvaluationPresetDialog(
            viewModel = viewModel,
            onDismiss = { presetDialogShown = false }
        )
    }
    if (confirmBulkSubmitShown) {
        val dialogShape = SmoothRoundedCornerShape(28.dp)
        AlertDialog(
            modifier = Modifier.appLiquidGlassSurface(
                shape = dialogShape,
                fallbackColor = 100.n1 withNight 20.n1,
                level = LiquidGlassSurfaceLevel.Floating,
                backdropSamplingEnabled = false
            ),
            onDismissRequest = { confirmBulkSubmitShown = false },
            shape = dialogShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            titleContentColor = 0.n1 withNight 100.n1,
            textContentColor = 30.n1 withNight 90.n1,
            title = { Text("确认批量评教") },
            text = {
                Text(
                    text = "将按当前预设提交 $presetTargetCount 项评教，提交后通常不能撤回。",
                    color = 0.n1 withNight 100.n1
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBulkSubmitShown = false
                        viewModel.submitAllWithPreset()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = 40.a1 withNight 80.a1
                    )
                ) {
                    Text("确认提交")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmBulkSubmitShown = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = 40.a1 withNight 80.a1
                    )
                ) {
                    Text("取消")
                }
            }
        )
    }

    AppLazyPageLayout(
        title = "评教",
        onBack = onBack,
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1),
        bottomPadding = 48.dp,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        actions = {
            AppHeaderIconButton(
                imageVector = Icons.Filled.Settings,
                miuixImageVector = MiuixIcons.Useful.Settings,
                contentDescription = "评教预设",
                onClick = { presetDialogShown = true }
            )
        }
    ) {
        item(key = "semester") {
            AppSelectField(
                label = "选择学期",
                selected = selectedSemesterId,
                options = semesters.map { semester ->
                    AppSelectOption(semester.id, semester.nameZh)
                },
                onSelected = { semesterId ->
                    viewModel.selectedSemesterId.value = semesterId
                    viewModel.loadEvaluationList()
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                enabled = !isLoading && !isSubmitting && !isBulkSubmitting,
                miuixStandalone = true
            )
        }

        item(key = "bulk-submit") {
            AppButton(
                onClick = { confirmBulkSubmitShown = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = hasPresetTargets && !isLoading && !isSubmitting && !isBulkSubmitting,
                variant = AppButtonVariant.Secondary
            ) {
                if (isBulkSubmitting) {
                    AppCircularProgressIndicator(
                        size = 16.dp,
                        strokeWidth = 2.dp,
                        color = 40.a1 withNight 80.a1
                    )
                } else {
                    Text("按预设完成全部")
                }
            }
        }

        if (isLoading && taskItems.isEmpty()) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppCircularProgressIndicator()
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item(key = "error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .appLiquidGlassSurface(
                            shape = SmoothRoundedCornerShape(16.dp),
                            fallbackColor = MaterialTheme.colorScheme.errorContainer,
                            level = LiquidGlassSurfaceLevel.Panel
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    AppButton(
                        onClick = {
                            if (semesters.isEmpty()) viewModel.loadSemesters()
                            else viewModel.loadEvaluationList()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = AppButtonVariant.Secondary
                    ) { Text("重试") }
                }
            }
        }

        taskItems.forEachIndexed { itemIndex, taskItem ->
            taskItem.taskList.forEachIndexed { taskIndex, task ->
                task.teachers.forEachIndexed { teacherIndex, teacher ->
                    item(
                        key = "${taskItem.lessonId}:${task.stdSumTaskId}:${teacher.teacherId}:$itemIndex:$taskIndex:$teacherIndex"
                    ) {
                        EvaluationCard(
                            task = task,
                            teacher = teacher,
                            courseName = taskItem.courseName,
                            lessonName = taskItem.lessonNameZh,
                            onClick = {
                                viewModel.enterEvaluation(
                                    task = task,
                                    teacher = teacher,
                                    courseName = taskItem.courseName,
                                    lessonName = taskItem.lessonNameZh
                                )
                            },
                            onPresetClick = {
                                viewModel.quickSubmitWithPreset(
                                    task = task,
                                    teacher = teacher,
                                    courseName = taskItem.courseName,
                                    lessonName = taskItem.lessonNameZh
                                )
                            },
                            presetEnabled = !isSubmitting && !isBulkSubmitting
                        )
                    }
                }
            }
        }

        if (!isLoading && taskItems.isEmpty() && errorMessage.isNullOrBlank()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无待评教课程",
                        color = 40.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun EvaluationCard(
    task: EvalTask,
    teacher: EvalTeacher,
    courseName: String,
    lessonName: String,
    onClick: () -> Unit,
    onPresetClick: () -> Unit,
    presetEnabled: Boolean
) {
    val reviewed = teacher.status != "TO_REVIEW"

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = SmoothRoundedCornerShape(20.dp),
        enabled = !reviewed && task.timeStatus,
        onClick = onClick
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = courseName,
                    modifier = Modifier.weight(1f),
                    color = 0.n1 withNight 100.n1,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                val (statusText, statusColor) = when {
                    reviewed -> "已评" to (50.n1 withNight 70.n1)
                    !task.timeStatus -> "未开始" to Color(0xFF2E7D32)
                    else -> "待评" to (90.a1 withNight 90.a1)
                }
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = 50.n1 withNight 70.n1
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = teacher.teacherName,
                    color = 30.n1 withNight 90.n1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "$lessonName · ${task.evaluationQuestionnaireName}",
                color = 50.n1 withNight 80.n1,
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AppButton(
                    onClick = onPresetClick,
                    enabled = !reviewed && task.timeStatus && presetEnabled,
                    variant = AppButtonVariant.Secondary
                ) {
                    Text(
                        text = "按预设完成",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun EvaluationFormScreen(viewModel: EvaluationViewModel) {
    val questions by viewModel.questions.collectAsState()
    val currentTeacher by viewModel.currentTeacher.collectAsState()
    val currentCourseName by viewModel.currentCourseName.collectAsState()
    val currentLessonName by viewModel.currentLessonName.collectAsState()
    val answers by viewModel.answers.collectAsState()
    val textAnswers by viewModel.textAnswers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val submitSuccess by viewModel.submitSuccess.collectAsState()
    val submitMessage by viewModel.submitMessage.collectAsState()

    val context = LocalContext.current
    var presetDialogShown by remember { mutableStateOf(false) }

    if (presetDialogShown) {
        EvaluationPresetDialog(
            viewModel = viewModel,
            onDismiss = { presetDialogShown = false }
        )
    }

    LaunchedEffect(submitMessage) {
        submitMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            if (submitSuccess) {
                viewModel.backToList()
            } else {
                viewModel.submitMessage.value = null
            }
        }
    }

    AppLazyPageLayout(
        title = currentCourseName.ifBlank { "课程评教" },
        onBack = { viewModel.backToList() },
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1),
        bottomPadding = 48.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        actions = {
            AppHeaderIconButton(
                imageVector = Icons.Filled.Settings,
                miuixImageVector = MiuixIcons.Useful.Settings,
                contentDescription = "评教预设",
                onClick = { presetDialogShown = true }
            )
        }
    ) {
        item(key = "teacher") {
            Text(
                text = "${currentTeacher?.teacherName.orEmpty()} · $currentLessonName",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (isLoading && questions.isEmpty()) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) { AppCircularProgressIndicator() }
            }
        } else {
            item(key = "preset-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppButton(
                        onClick = { viewModel.applyPresetToCurrent() },
                        modifier = Modifier.weight(1f),
                        enabled = questions.isNotEmpty() && !isSubmitting,
                        variant = AppButtonVariant.Secondary
                    ) { Text("套用预设") }
                    AppButton(
                        onClick = { viewModel.submitCurrentWithPreset() },
                        modifier = Modifier.weight(1f),
                        enabled = questions.isNotEmpty() && !isSubmitting
                    ) { Text("预设提交") }
                }
            }
            questions.forEachIndexed { index, question ->
                item(key = "question:${question.attribute.id}:$index") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        QuestionCard(
                            question = question,
                            selectedOptionId = answers[question.attribute.id.toString()],
                            textAnswer = textAnswers[question.attribute.id.toString()].orEmpty(),
                            onSelect = { optionId ->
                                viewModel.setAnswer(question.attribute.id.toString(), optionId)
                            },
                            onTextChange = { text ->
                                viewModel.setTextAnswer(question.attribute.id.toString(), text)
                            }
                        )
                    }
                }
            }
        }

        item(key = "submit-actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButton(
                    onClick = { viewModel.backToList() },
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.Secondary
                ) {
                    Text("取消")
                }
                AppButton(
                    onClick = { viewModel.submit(false) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && questions.isNotEmpty()
                ) {
                    if (isSubmitting) {
                        AppCircularProgressIndicator(
                            size = 16.dp,
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("提交")
                    }
                }
                AppButton(
                    onClick = { viewModel.submit(true) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && questions.isNotEmpty()
                ) {
                    Text("匿名")
                }
            }
        }
    }
}

@Composable
private fun EvaluationPresetDialog(
    viewModel: EvaluationViewModel,
    onDismiss: () -> Unit
) {
    val preset by viewModel.preset.collectAsState()
    val presetQuestions by viewModel.presetQuestions.collectAsState()
    val isPresetLoading by viewModel.isPresetLoading.collectAsState()
    val taskItems by viewModel.taskItems.collectAsState()
    var radioIndexes by remember(preset, presetQuestions) {
        mutableStateOf(viewModel.presetRadioOptionIndexesFor(presetQuestions, preset))
    }
    var presetTextAnswers by remember(preset, presetQuestions) {
        mutableStateOf(viewModel.presetTextAnswersFor(presetQuestions, preset))
    }
    var anonymous by remember(preset) {
        mutableStateOf(preset.anonymous)
    }

    LaunchedEffect(taskItems, presetQuestions.isEmpty()) {
        viewModel.loadPresetQuestions()
    }

    val dialogShape = SmoothRoundedCornerShape(28.dp)
    AlertDialog(
        modifier = Modifier.appLiquidGlassSurface(
            shape = dialogShape,
            fallbackColor = 100.n1 withNight 20.n1,
            level = LiquidGlassSurfaceLevel.Floating,
            backdropSamplingEnabled = false
        ),
        onDismissRequest = onDismiss,
        shape = dialogShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = 0.n1 withNight 100.n1,
        textContentColor = 30.n1 withNight 90.n1,
        title = {
            Text("评教预设")
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "匿名提交",
                        color = 0.n1 withNight 100.n1,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AppToggle(
                        checked = anonymous,
                        onCheckedChange = { anonymous = it },
                        contentDescription = "匿名提交"
                    )
                }

                when {
                    isPresetLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AppCircularProgressIndicator()
                        }
                    }
                    presetQuestions.isEmpty() -> {
                        Text(
                            text = "暂无可编辑的评教题目",
                            color = 50.n1 withNight 80.n1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            presetQuestions.forEach { question ->
                                val questionId = question.attribute.id.toString()
                                PresetQuestionEditor(
                                    question = question,
                                    selectedOptionIndex = radioIndexes[questionId],
                                    textAnswer = presetTextAnswers[questionId].orEmpty(),
                                    onOptionIndexChange = { optionIndex ->
                                        radioIndexes = radioIndexes + (questionId to optionIndex)
                                    },
                                    onTextChange = { text ->
                                        presetTextAnswers = presetTextAnswers + (questionId to text)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = presetQuestions.isNotEmpty(),
                onClick = {
                    viewModel.savePreset(
                        radioOptionIndexes = radioIndexes,
                        textAnswers = presetTextAnswers,
                        anonymous = anonymous
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = 40.a1 withNight 80.a1,
                    disabledContentColor = 60.n1 withNight 50.n1
                )
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = 40.a1 withNight 80.a1
                )
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PresetQuestionEditor(
    question: EvalQuestion,
    selectedOptionIndex: Int?,
    textAnswer: String,
    onOptionIndexChange: (Int) -> Unit,
    onTextChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${question.index}. ${question.attribute.name}",
            color = 0.n1 withNight 100.n1,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )

        when (question.attribute.typeId) {
            1 -> {
                question.options.forEachIndexed { optionIndex, option ->
                    val selected = selectedOptionIndex == optionIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmoothRoundedCornerShape(10.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onOptionIndexChange(optionIndex) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onOptionIndexChange(optionIndex) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = option.value,
                            modifier = Modifier.weight(1f),
                            color = 0.n1 withNight 100.n1,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (question.attribute.enableScore) {
                            Text(
                                text = "${option.optionScore}分",
                                color = 50.n1 withNight 70.n1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            4 -> {
                OutlinedTextField(
                    value = textAnswer,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = 0.n1 withNight 100.n1,
                        unfocusedTextColor = 0.n1 withNight 100.n1,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = 90.n1 withNight 30.n1,
                        cursorColor = 90.a1 withNight 90.a1
                    ),
                    shape = SmoothRoundedCornerShape(12.dp)
                )
            }
            else -> {
                Text(
                    text = "暂不支持该题型",
                    color = 50.n1 withNight 80.n1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: EvalQuestion,
    selectedOptionId: String?,
    textAnswer: String,
    onSelect: (String) -> Unit,
    onTextChange: (String) -> Unit
) {
    val attr = question.attribute

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SmoothRoundedCornerShape(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${question.index}.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = attr.name,
                    modifier = Modifier.weight(1f),
                    color = 0.n1 withNight 100.n1,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (attr.required) {
                    Text(
                        text = "*",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            }

            when (attr.typeId) {
                1 -> {
                    question.options.forEach { option ->
                        val optionId = option.optionId.toString()
                        val selected = selectedOptionId == optionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SmoothRoundedCornerShape(12.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { onSelect(optionId) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelect(optionId) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = option.value,
                                    color = 0.n1 withNight 100.n1,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (attr.enableScore) {
                                Text(
                                    text = "${option.optionScore}分",
                                    color = 50.n1 withNight 70.n1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
                4 -> {
                    OutlinedTextField(
                        value = textAnswer,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "请输入评语",
                                color = 50.n1 withNight 70.n1
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = 0.n1 withNight 100.n1,
                            unfocusedTextColor = 0.n1 withNight 100.n1,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = 90.n1 withNight 30.n1,
                            focusedPlaceholderColor = 50.n1 withNight 70.n1,
                            unfocusedPlaceholderColor = 50.n1 withNight 70.n1,
                            cursorColor = 90.a1 withNight 90.a1
                        ),
                        shape = SmoothRoundedCornerShape(12.dp)
                    )
                }
                else -> {
                    Text(
                        text = "暂不支持该题型",
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (attr.enableScore) {
                Text(
                    text = "本题 ${attr.score} 分",
                    color = 50.n1 withNight 70.n1,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
