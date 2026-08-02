package com.ahu.ahutong.ui.state

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.weather.WeatherApi
import com.ahu.ahutong.data.weather.WeatherResponse
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

enum class WeatherHomeMode(val cacheValue: String) {
    Detailed("detailed"),
    Compact("compact");

    companion object {
        fun fromCacheValue(value: String?): WeatherHomeMode {
            return values().firstOrNull { it.cacheValue == value } ?: Detailed
        }
    }
}

data class WeatherHomeConfig(
    val showOnHome: Boolean = false,
    val mode: WeatherHomeMode = WeatherHomeMode.Detailed,
    val showTemp: Boolean = true,
    val showWeather: Boolean = true,
    val showAqi: Boolean = true,
    val showLocation: Boolean = true,
) {
    fun saveToCache() {
        AHUCache.saveWeatherShowOnHome(showOnHome)
        AHUCache.saveWeatherHomeMode(mode.cacheValue)
        AHUCache.saveWeatherHomeShowTemp(showTemp)
        AHUCache.saveWeatherHomeShowWeather(showWeather)
        AHUCache.saveWeatherHomeShowAqi(showAqi)
        AHUCache.saveWeatherHomeShowLocation(showLocation)
    }

    companion object {
        fun fromCache(): WeatherHomeConfig {
            return WeatherHomeConfig(
                showOnHome = AHUCache.getWeatherShowOnHome(),
                mode = WeatherHomeMode.fromCacheValue(AHUCache.getWeatherHomeMode()),
                showTemp = AHUCache.getWeatherHomeShowTemp(),
                showWeather = AHUCache.getWeatherHomeShowWeather(),
                showAqi = AHUCache.getWeatherHomeShowAqi(),
                showLocation = AHUCache.getWeatherHomeShowLocation(),
            )
        }
    }
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {

    var weather by mutableStateOf<WeatherResponse?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var lastCity by mutableStateOf<String?>(null)
        private set

    var lastAdcode by mutableStateOf<String?>(null)
        private set

    var lastLocationName by mutableStateOf<String?>(null)
        private set

    var homeConfig by mutableStateOf(WeatherHomeConfig())
        private set

    init {
        homeConfig = WeatherHomeConfig.fromCache()
    }

    fun fetchWeather(city: String? = null) {
        if (isLoading) return
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val result = WeatherApi.API.getWeather(city = city)
                weather = result
                lastCity = city
                lastAdcode = null
                Log.d("Weather", "Weather content loaded")
                reportReady()
            } catch (e: Exception) {
                Log.e("Weather", "Failed to fetch weather")
                errorMessage = e.message ?: "获取天气失败"
                reportError()
            } finally {
                isLoading = false
            }
        }
    }

    fun refresh() {
        if (lastAdcode != null) {
            fetchWeatherByAdcode(lastAdcode!!)
        } else {
            fetchWeather(lastCity)
        }
    }

    private fun fetchWeatherByAdcode(adcode: String) {
        if (isLoading) return
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val result = WeatherApi.API.getWeather(adcode = adcode)
                weather = result
                lastAdcode = adcode
                lastCity = null
                result.adcode?.let { AHUCache.saveWeatherAdcode(it) }
                Log.d("Weather", "Weather content loaded by saved location")
                reportReady()
            } catch (e: Exception) {
                Log.e("Weather", "Failed to fetch weather by saved location")
                errorMessage = e.message ?: "获取天气失败"
                reportError()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 通过 GPS 定位获取天气（比 IP 定位精准）
     * 降级策略：GPS → 网络定位 → Geocoder 反查城市名 → 调用 API
     */
    fun fetchWeatherByLocation(context: Context) {
        if (isLoading) return
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val cachedAdcode = AHUCache.getWeatherAdcode()
                if (cachedAdcode != null) {
                    // 有缓存的区级 adcode，直接用
                    val result = WeatherApi.API.getWeather(adcode = cachedAdcode)
                    weather = result
                    lastAdcode = cachedAdcode
                    lastCity = null
                    // 刷新缓存
                    result.adcode?.let { AHUCache.saveWeatherAdcode(it) }
                    reportReady()
                    return@launch
                }

                // 无缓存，GPS 定位获取城市名
                val city = withContext(Dispatchers.IO) { getCityNameFromGps(context) }
                if (city != null) {
                    lastCity = city
                    lastAdcode = null
                    lastLocationName = city
                    val result = WeatherApi.API.getWeather(city = city)
                    weather = result
                    // 保存 adcode 供后续精准查询
                    result.adcode?.let { AHUCache.saveWeatherAdcode(it) }
                    reportReady()
                } else {
                    Log.w("Weather", "GPS failed, fallback to IP")
                    val result = WeatherApi.API.getWeather()
                    weather = result
                    result.adcode?.let { AHUCache.saveWeatherAdcode(it) }
                    reportReady()
                }
            } catch (e: Exception) {
                Log.e("Weather", "Failed to fetch weather by location")
                try {
                    val result = WeatherApi.API.getWeather()
                    weather = result
                    errorMessage = null
                    reportReady()
                } catch (e2: Exception) {
                    errorMessage = e2.message ?: "获取天气失败"
                    reportError()
                }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * GPS 定位 → Geocoder 反查城市名
     * 尝试获取区级名称（locality = 蜀山区），否则市（subAdminArea = 合肥市）
     */
    private fun getCityNameFromGps(context: Context): String? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }.getOrNull() ?: runCatching {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull() ?: return null

        val geocoder = Geocoder(context, Locale.CHINA)
        val addresses = runCatching {
            geocoder.getFromLocation(location.latitude, location.longitude, 1)
        }.getOrNull() ?: return null

        val address = addresses.firstOrNull() ?: return null
        // 优先市名（API 更稳定），区名做 fallback
        return address.subAdminArea ?: address.locality
    }

    fun updateHomeConfig(config: WeatherHomeConfig) {
        val previous = homeConfig
        if (previous == config) return
        homeConfig = config
        config.saveToCache()
        behaviorRuntime.recordCommittedMutationAsync(
            MutationId.WEATHER_HOME_CONFIG_CHANGED,
            previous,
            config,
            coarseValueBucket = "CONFIG_CHANGED"
        )
    }

    private fun reportReady() {
        behaviorRuntime.onContentStateChanged(
            SemanticDomain.WEATHER,
            ContentStateBucket.READY,
            freshnessBucket = 0,
            resultCount = ResultCountBucket.ONE_TO_FIVE
        )
    }

    private fun reportError() {
        behaviorRuntime.onContentStateChanged(
            SemanticDomain.WEATHER,
            ContentStateBucket.ERROR,
            freshnessBucket = 7,
            resultCount = ResultCountBucket.ZERO,
            errorType = ErrorTypeBucket.NETWORK
        )
    }

    val locationName: String
        get() {
            // 优先 GPS 反查的区位名
            lastLocationName?.let { return it }
            val w = weather ?: return ""
            return listOfNotNull(w.district, w.city, w.province)
                .firstOrNull { it.isNotBlank() } ?: ""
        }
}
