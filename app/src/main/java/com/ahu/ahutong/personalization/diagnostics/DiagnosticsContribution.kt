package com.ahu.ahutong.personalization.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime

interface DiagnosticsContribution {
    fun isDiagnosticsRoute(route: String?): Boolean = false

    fun installRoutes(
        builder: NavGraphBuilder,
        navController: NavHostController,
        runtime: BehaviorPredictionRuntime
    )

    @Composable
    fun BoxScope.Overlay(
        navController: NavHostController,
        runtime: BehaviorPredictionRuntime,
        blocked: Boolean
    )
}
