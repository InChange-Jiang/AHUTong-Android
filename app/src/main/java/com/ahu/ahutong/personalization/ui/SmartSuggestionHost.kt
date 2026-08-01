package com.ahu.ahutong.personalization.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.runtime.PredictionUiState

@Composable
fun SmartSuggestionHost(
    runtime: BehaviorPredictionRuntime,
    blocked: Boolean,
    onSuggestionClick: (PredictionUiState.Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by runtime.uiState.collectAsState()
    LaunchedEffect(blocked) {
        if (blocked) runtime.hideSuggestion()
    }
    if (blocked) return
    val suggestion = state as? PredictionUiState.Suggestion ?: return
    Surface(
        onClick = { onSuggestionClick(suggestion) },
        modifier = modifier
            .semantics { contentDescription = "猜你想用：${suggestion.title}" },
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text("猜你想用 · ${suggestion.title}", style = MaterialTheme.typography.titleSmall)
                Text(suggestion.reason, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = runtime::suppressSuggestedActionByUser) {
                Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = "30 天内不再推荐此功能")
            }
            IconButton(onClick = runtime::dismissSuggestionByUser) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭建议")
            }
        }
    }
}
