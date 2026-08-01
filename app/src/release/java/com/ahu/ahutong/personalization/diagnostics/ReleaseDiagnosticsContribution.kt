package com.ahu.ahutong.personalization.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseDiagnosticsContribution @Inject constructor() : DiagnosticsContribution {
    override fun installRoutes(builder: NavGraphBuilder, navController: NavHostController, runtime: BehaviorPredictionRuntime) = Unit

    @Composable
    override fun BoxScope.Overlay(navController: NavHostController, runtime: BehaviorPredictionRuntime, blocked: Boolean) = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseDiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindDiagnostics(implementation: ReleaseDiagnosticsContribution): DiagnosticsContribution
}
