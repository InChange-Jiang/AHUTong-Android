package com.ahu.ahutong.personalization.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.runtime.PredictionUiState
import kotlinx.coroutines.launch

@Composable
fun SmartSuggestionHost(
    runtime: BehaviorPredictionRuntime,
    navController: NavHostController,
    paymentQrCommands: PaymentQrOpenCommandStore,
    blocked: Boolean,
    modifier: Modifier = Modifier
) {
    val state by runtime.uiState.collectAsState()
    LaunchedEffect(blocked) {
        if (blocked) runtime.hideSuggestion()
    }
    if (blocked) return
    val suggestion = state as? PredictionUiState.Suggestion ?: return
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier
            .semantics { contentDescription = "猜你想用：${suggestion.title}" }
            .clickable {
                scope.launch {
                    val action = runtime.acceptSuggestion(suggestion.executionId) ?: return@launch
                    if (action == AppActionId.OPEN_PAYMENT_QR) {
                        paymentQrCommands.publish(suggestion.executionId, suggestion.decisionId, com.ahu.ahutong.personalization.action.ActionSource.SUGGESTION)
                        runtime.suppressNextRoute("home")
                        navController.navigate("home") { launchSingleTop = true }
                    } else {
                        AppActionCatalog.spec(action).route?.let { navController.navigate(it) { launchSingleTop = true } }
                    }
                }
            },
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
