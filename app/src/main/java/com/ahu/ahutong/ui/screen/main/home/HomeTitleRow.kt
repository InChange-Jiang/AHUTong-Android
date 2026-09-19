package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.weather.WeatherApi
import com.ahu.ahutong.data.weather.WeatherResponse
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 主页标题行：左侧日期，右侧常驻天气胶囊（仅气温 + 今日有无雨）。
 * 点击胶囊进天气页。
 */
@Composable
fun HomeTitleRow(
    onOpenWeather: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = SimpleDateFormat("MM月dd日 EE", Locale.CHINA).format(DebugClock.nowDate()),
            style = MaterialTheme.typography.bodyMedium,
            color = 45.n1 withNight 75.n1
        )
        WeatherCapsule(onClick = onOpenWeather)
    }
}

@Composable
private fun WeatherCapsule(onClick: () -> Unit) {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<WeatherResponse?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                WeatherApi.API.getWeather(city = getCityFromLocation(context))
            }
        }.onSuccess { weather = it }
    }

    val w = weather
    val rainy = w?.weather?.let { it.contains("雨") || it.contains("雪") } == true ||
        (w?.precipitation ?: 0.0) > 0.0
    val text = if (w == null) {
        "--°"
    } else {
        "${w.temperature?.toInt() ?: "--"}° · ${if (rainy) "有雨" else "无雨"}"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(96.n1 withNight 14.n1)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = 10.n1 withNight 90.n1
        )
    }
}
