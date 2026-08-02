package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {

    private val _personalizationEnabled = MutableStateFlow<Boolean?>(null)
    val personalizationEnabled: StateFlow<Boolean?> = _personalizationEnabled.asStateFlow()

    private val _predictivePrefetchEnabled = MutableStateFlow<Boolean?>(null)
    val predictivePrefetchEnabled: StateFlow<Boolean?> = _predictivePrefetchEnabled.asStateFlow()

    private val _wifiOnlyPrefetch = MutableStateFlow<Boolean?>(null)
    val wifiOnlyPrefetch: StateFlow<Boolean?> = _wifiOnlyPrefetch.asStateFlow()

    private val _behaviorRetentionDays = MutableStateFlow(30)
    val behaviorRetentionDays: StateFlow<Int> = _behaviorRetentionDays.asStateFlow()

    private val _showQRCode = MutableStateFlow(false)
    val showQRCode: StateFlow<Boolean> = _showQRCode.asStateFlow()

    private val _isShowAllCourse = MutableStateFlow(false)
    val isShowAllCourse: StateFlow<Boolean> = _isShowAllCourse.asStateFlow()

    private val _useLiquidGlass = MutableStateFlow(true)
    val useLiquidGlass: StateFlow<Boolean> = _useLiquidGlass.asStateFlow()

    private val _themeColor = MutableStateFlow<String?>(null)
    val themeColor: StateFlow<String?> = _themeColor.asStateFlow()

    private val _appThemeMode = MutableStateFlow(AppThemeMode.FOLLOW_SYSTEM)
    val appThemeMode: StateFlow<AppThemeMode> = _appThemeMode.asStateFlow()

    private val _courseReminderEnabled = MutableStateFlow(false)
    val courseReminderEnabled: StateFlow<Boolean> = _courseReminderEnabled.asStateFlow()

    private val _courseReminderLiveCountdownEnabled = MutableStateFlow(false)
    val courseReminderLiveCountdownEnabled: StateFlow<Boolean> =
        _courseReminderLiveCountdownEnabled.asStateFlow()

    private val _repositoryAccelerationSource = MutableStateFlow("jsdelivr")
    val repositoryAccelerationSource: StateFlow<String> =
        _repositoryAccelerationSource.asStateFlow()

    init {
        viewModelScope.launch { preferencesManager.personalizationEnabled.collect { _personalizationEnabled.value = it } }
        viewModelScope.launch { preferencesManager.predictivePrefetchEnabled.collect { _predictivePrefetchEnabled.value = it } }
        viewModelScope.launch { preferencesManager.wifiOnlyPrefetch.collect { _wifiOnlyPrefetch.value = it } }
        viewModelScope.launch { preferencesManager.behaviorRetentionDays.collect { _behaviorRetentionDays.value = it } }
        viewModelScope.launch { preferencesManager.themeMode.collect { _appThemeMode.value = it } }
        viewModelScope.launch {
            preferencesManager.themeColor.collect {
                _themeColor.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.showQRCode.collect {
                _showQRCode.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.isShowAllCourse.collect {
                _isShowAllCourse.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.useLiquidGlass.collect {
                _useLiquidGlass.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.courseReminderEnabled.collect {
                _courseReminderEnabled.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.courseReminderLiveCountdownEnabled.collect {
                _courseReminderLiveCountdownEnabled.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.repositoryAccelerationSource.collect {
                _repositoryAccelerationSource.value = it
            }
        }
    }

    fun setPersonalizationEnabled(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPersonalizationEnabled(value)
            if (!value) behaviorRuntime.hideSuggestion()
        }
    }

    fun setPredictivePrefetchEnabled(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPredictivePrefetchEnabled(value)
            if (!value) {
                preferencesManager.setWifiOnlyPrefetch(false)
                behaviorRuntime.cancelPredictivePrefetch()
            }
        }
    }

    fun setWifiOnlyPrefetch(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWifiOnlyPrefetch(
                value && _predictivePrefetchEnabled.value == true
            )
        }
    }

    fun clearPersonalizationLearning() {
        viewModelScope.launch { behaviorRuntime.clearLearningRecord() }
    }

    fun setBehaviorRetentionDays(value: Int) {
        viewModelScope.launch { preferencesManager.setBehaviorRetentionDays(value) }
    }

    fun setShowQRCode(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setShowQRCode(value)
        }
    }

    fun setIsShowAllCourse(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setIsShowAllCourse(value)
        }
    }

    fun setUseLiquidGlass(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setUseLiquidGlass(value)
        }
    }

    fun setCourseReminderEnabled(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCourseReminderEnabled(value)
        }
    }

    fun setCourseReminderLiveCountdownEnabled(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCourseReminderLiveCountdownEnabled(value)
        }
    }

    fun setThemeColor(value: String?) {
        viewModelScope.launch {
            preferencesManager.setThemeColor(value)
        }
    }

    fun setAppThemeMode(value: AppThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(value)
        }
    }

    fun setRepositoryAccelerationSource(value: String) {
        viewModelScope.launch {
            preferencesManager.setRepositoryAccelerationSource(value)
        }
    }
}
