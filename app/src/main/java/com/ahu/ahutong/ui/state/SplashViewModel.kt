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

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {
    private val _telemetryOnboardingState =
        MutableStateFlow<TelemetryOnboardingState>(TelemetryOnboardingState.Loading)
    val telemetryOnboardingState: StateFlow<TelemetryOnboardingState> =
        _telemetryOnboardingState.asStateFlow()

    init {
        viewModelScope.launch {
            _telemetryOnboardingState.value = TelemetryOnboardingState.Ready(
                preferencesManager.modelQualityTelemetryOnboardingChoice.first()
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
}
