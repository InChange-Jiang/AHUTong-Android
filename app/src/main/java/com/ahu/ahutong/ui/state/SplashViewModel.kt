package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface TelemetryOnboardingState {
    data object Loading : TelemetryOnboardingState
    data class Ready(val choice: Boolean?) : TelemetryOnboardingState
}

sealed interface BootstrapTrainingOnboardingState {
    data object Loading : BootstrapTrainingOnboardingState
    data class Ready(val choice: Boolean?) : BootstrapTrainingOnboardingState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {
    private val _telemetryOnboardingState =
        MutableStateFlow<TelemetryOnboardingState>(TelemetryOnboardingState.Loading)
    val telemetryOnboardingState: StateFlow<TelemetryOnboardingState> =
        _telemetryOnboardingState.asStateFlow()
    private val _bootstrapTrainingOnboardingState =
        MutableStateFlow<BootstrapTrainingOnboardingState>(BootstrapTrainingOnboardingState.Loading)
    val bootstrapTrainingOnboardingState: StateFlow<BootstrapTrainingOnboardingState> =
        _bootstrapTrainingOnboardingState.asStateFlow()

    init {
        viewModelScope.launch {
            _telemetryOnboardingState.value = TelemetryOnboardingState.Ready(
                preferencesManager.modelQualityTelemetryOnboardingChoice.first()
            )
            _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Ready(
                preferencesManager.bootstrapTrainingOnboardingChoice.first()
            )
        }
    }

    fun chooseModelQualityTelemetry(enabled: Boolean) {
        val current = _telemetryOnboardingState.value as? TelemetryOnboardingState.Ready ?: return
        if (current.choice != null) return
        _telemetryOnboardingState.value = TelemetryOnboardingState.Loading
        viewModelScope.launch {
            preferencesManager.setModelQualityTelemetryOnboardingChoice(enabled)
            runCatching { behaviorRuntime.setTelemetryConsent(enabled) }
            _telemetryOnboardingState.value = TelemetryOnboardingState.Ready(enabled)
        }
    }


    fun chooseBootstrapTraining(enabled: Boolean, includeHistorical: Boolean) {
        val current = _bootstrapTrainingOnboardingState.value as?
            BootstrapTrainingOnboardingState.Ready ?: return
        if (current.choice != null) return
        _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Loading
        viewModelScope.launch {
            preferencesManager.setBootstrapTrainingOnboardingChoice(enabled, includeHistorical)
            runCatching { behaviorRuntime.setBootstrapTrainingConsent(enabled, includeHistorical) }
            _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Ready(enabled)
        }
    }

    fun acceptUnifiedPrivacyPolicy() {
        _telemetryOnboardingState.value = TelemetryOnboardingState.Loading
        _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Loading
        viewModelScope.launch {
            preferencesManager.setModelQualityTelemetryOnboardingChoice(true)
            preferencesManager.setBootstrapTrainingOnboardingChoice(
                value = true,
                includeHistorical = true
            )
            runCatching { behaviorRuntime.setTelemetryConsent(true) }
            runCatching {
                behaviorRuntime.setBootstrapTrainingConsent(
                    enabled = true,
                    includeHistorical = true
                )
            }
            _telemetryOnboardingState.value = TelemetryOnboardingState.Ready(true)
            _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Ready(true)
        }
    }
}
