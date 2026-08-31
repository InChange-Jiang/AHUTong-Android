package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.GradeEvaluationGate
import com.ahu.ahutong.data.crawler.model.jwxt.CourseGrade
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppHeaderIconButton
import com.ahu.ahutong.ui.components.AppScrollablePageLayout
import com.ahu.ahutong.ui.components.AppSearchField
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.GradeViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.personalization.action.AppActionId
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Cancel
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.useful.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Grade(
    gradeViewModel: GradeViewModel = hiltViewModel(),
    onNavigateToEvaluation: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    DisposableEffect(gradeViewModel) {
        onDispose { gradeViewModel.onPresetSurfaceDisposed() }
    }
    val behaviorReporter = rememberBehaviorActionReporter()
    val grade = gradeViewModel.grade
    val gpaRankInfo = gradeViewModel.gpaRankInfo
    val errorMessage = gradeViewModel.errorMessage
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
        searchQuery = ""
    }

    LaunchedEffect(Unit) {
        if (grade == null) gradeViewModel.getGarde()
        if (gpaRankInfo == null) gradeViewModel.getGpaRank()
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
            gradeViewModel.getGarde(isRefresh = true)
            gradeViewModel.getGpaRank()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            gradeViewModel.errorMessage = null
        }
    }

    val gradeData = gradeViewModel.grade?.termGradeList?.find {
        it.schoolYear == gradeViewModel.schoolYear &&
                it.term == gradeViewModel.schoolTerm
    }

    val currentRank = gpaRankInfo?.gpaSemesterSubs?.find {
        it.semesterId == gradeData?.gradeList?.firstOrNull()?.semesterId
    }

    val trimmedQuery = if (searchExpanded) searchQuery.trim() else ""

    fun fuzzyContains(text: String, query: String): Boolean {
        if (query.isBlank()) return false
        val q = query.filterNot { it.isWhitespace() }
        if (q.isEmpty()) return false
        val pattern = q.map { Regex.escape(it.toString()) }.joinToString(".*")
        return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    val searchResultsByTerm = gradeViewModel.grade?.termGradeList
        ?.mapNotNull { term ->
            val matches = term.gradeList
                ?.filter { item ->
                    val q = trimmedQuery
                    q.isNotEmpty() && (
                            fuzzyContains(item.course ?: "", q) ||
                                    fuzzyContains(item.courseNum ?: "", q) ||
                                    fuzzyContains(item.courseNature ?: "", q)
                            )
                }
                .orEmpty()
            if (matches.isEmpty()) null else term to matches
        }
        .orEmpty()

    AppScrollablePageLayout(
        title = stringResource(id = R.string.grade),
        onBack = onBack,
        scrollState = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1),
        bottomPadding = 48.dp,
        actions = {
            AppHeaderIconButton(
                imageVector = Icons.Default.Refresh,
                miuixImageVector = MiuixIcons.Useful.Refresh,
                contentDescription = "刷新成绩",
                onClick = {
                    behaviorReporter.organic(AppActionId.MANUAL_REFRESH_GRADE)
                    gradeViewModel.refreshGrade()
                }
            )
            AppHeaderIconButton(
                imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                miuixImageVector = if (searchExpanded) MiuixIcons.Useful.Cancel else MiuixIcons.Useful.Search,
                contentDescription = if (searchExpanded) "关闭搜索" else "搜索成绩",
                onClick = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) searchQuery = ""
                }
            )
        }
    ) {
        if (searchExpanded) {
            AppSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = "搜索课程"
            )
        }

            // Profile selector - shown when student has multiple profiles (micro-major/minor)
            if (!searchExpanded && gradeViewModel.studentProfiles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gradeViewModel.studentProfiles.forEachIndexed { index, profile ->
                        AppFilterChip(
                            selected = gradeViewModel.selectedProfileIndex == index,
                            onClick = { gradeViewModel.selectProfile(index) },
                            label = {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                }
            }

            // 改成学期下拉选择（替代原来的学年+学期双筛选）
            if (!searchExpanded) {
                val allTerms = gradeViewModel.grade?.termGradeList
                    ?.sortedWith(
                        compareByDescending<Grade.TermGradeListBean> {
                            // 提取学年起始值，例如 "2023-2024" -> 2023
                            it.schoolYear.substringBefore("-").toIntOrNull() ?: 0
                        }.thenByDescending {
                            it.term.toIntOrNull() ?: 0
                        }
                    )
                    .orEmpty()
                AppSelectField(
                    label = "选择学期",
                    selected = gradeViewModel.schoolYear?.let { schoolYear ->
                        gradeViewModel.schoolTerm?.let { schoolTerm -> schoolYear to schoolTerm }
                    },
                    options = allTerms.map { term ->
                        AppSelectOption(
                            value = term.schoolYear.orEmpty() to term.term.orEmpty(),
                            label = "${term.schoolYear} 第${term.term}学期"
                        )
                    },
                    onSelected = { (schoolYear, schoolTerm) ->
                        gradeViewModel.selectTerm(schoolYear, schoolTerm)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueTextAlign = if (LocalAppUiTheme.current == AppUiTheme.MATERIAL) {
                        TextAlign.Start
                    } else {
                        TextAlign.End
                    },
                    miuixStandalone = true
                )
            }

            if (!searchExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Per-profile empty state
                    val rankMsg = gradeViewModel.rankEmptyMessage
                    if (gpaRankInfo == null && !rankMsg.isNullOrBlank()) {
                        Text(
                            text = rankMsg,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = 50.n1 withNight 70.n1
                        )
                    }

                    val infoList = listOf(
                        "本学期平均绩点" to gradeViewModel.termGradePointAverage,
                        "全程平均绩点" to gradeViewModel.totalGradePointAverage,
                        "全程专业排名" to ((gpaRankInfo?.majorRank ?: "暂无").toString() + "/" + (gpaRankInfo?.majorHeadCount ?: "暂无")),
                        "该学期专业排名" to ((currentRank?.majorRank ?: "暂无").toString() + "/" + (gpaRankInfo?.majorHeadCount ?: "暂无")),
                        "最后更新时间" to (gpaRankInfo?.updatedDateTimeStr ?: "暂无")
                    )

                    infoList.forEach { (title, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = 0.n1 withNight 100.n1,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = value,
                                color = 0.n1 withNight 100.n1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            if (searchExpanded && trimmedQuery.isNotBlank()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    searchResultsByTerm.forEach { (term, items) ->
                        Text(
                            text = "${term.schoolYear} 第${term.term}学期",
                            color = 0.n1 withNight 100.n1,
                            style = MaterialTheme.typography.titleMedium
                        )

                        items.forEach { item ->
                            GradeCard(
                                item = item,
                                onNavigateToEvaluation = onNavigateToEvaluation
                            )
                        }
                    }
                }
            } else if (!searchExpanded && gradeData != null && gradeData.gradeList.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    gradeData.gradeList.forEach {
                        GradeCard(
                            item = it,
                            onNavigateToEvaluation = onNavigateToEvaluation
                        )
                    }
                }
            } else if (!searchExpanded) {
                // Show empty message specific to selected profile
                val emptyMsg = if (gradeViewModel.studentProfiles.size > 1) {
                    val p = gradeViewModel.studentProfiles.getOrNull(gradeViewModel.selectedProfileIndex)
                    if (p != null) "「${p.displayName}」暂无成绩" else "该学期目前没有任何成绩"
                } else {
                    "该学期目前没有任何成绩"
                }
                Text(
                    text = emptyMsg,
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = 50.n1 withNight 70.n1
                )
            }
    }
}

@Composable
private fun GradeCard(
    item: Grade.TermGradeListBean.GradeListBean,
    onNavigateToEvaluation: () -> Unit
) {
    val needsEvaluation = GradeEvaluationGate.isRequiredPayload(item.grade) ||
        GradeEvaluationGate.isRequiredPayload(item.gradeDetail)
    val gradeText = item.grade.stripHtml()
    val gradeDetail = item.gradeDetail.stripHtml()

    AppCard(
        modifier = Modifier
            .fillMaxWidth(),
        shape = SmoothRoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = item.course ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        if (needsEvaluation) {
            val linkColor = 40.a1 withNight 80.a1
            Text(
                text = buildAnnotatedString {
                    append("成绩: ")
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(GradeEvaluationGate.MESSAGE)
                    }
                    append("    绩点: ${item.gradePoint}    学分: ${item.credit}")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onNavigateToEvaluation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                text = "成绩: $gradeText    绩点: ${item.gradePoint}    学分: ${item.credit}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Text(
            text = "${item.courseNature ?: ""} (${item.courseNum ?: ""})",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        if (!needsEvaluation && !gradeDetail.isNullOrBlank()) {
            Text(
                text = gradeDetail,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        }
    }
}

private fun String?.stripHtml(): String {
    return orEmpty()
        .replace("&nbsp;", " ")
        .replace("&#160;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace(Regex("<[^>]*>"), "")
        .trim()
}
