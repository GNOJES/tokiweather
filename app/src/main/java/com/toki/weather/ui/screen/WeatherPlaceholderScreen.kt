package com.toki.weather.ui.screen

import com.toki.weather.data.model.formatTemperatureRange

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toki.weather.R
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.HalfDayForecast
import com.toki.weather.data.model.HourlyForecast
import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.repository.ThreeHourForecast
import com.toki.weather.data.repository.summarizeThreeHours
import com.toki.weather.util.DateTimeUtils
import com.toki.weather.util.SolarTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherPlaceholderScreen(
    weather: CachedWeather,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    hourlyIntervalHours: Int = 1,
    onHourlyIntervalSelected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val latitude = weather.airQualityLatitude ?: 37.5665
    val longitude = weather.airQualityLongitude ?: 126.9780

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 상단 헤더: 지역명 + 날짜 + 새로고침
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location_pin),
                        contentDescription = "현재 위치",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = weather.locationName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DateTimeUtils.todayDateWithDayOfWeekString(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = "새로고침",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── 1. 현재 날씨 ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(weatherIconRes(weather.currentCondition, LocalDateTime.now(ZoneId.of("Asia/Seoul")), latitude, longitude)),
                            contentDescription = weather.currentCondition.label,
                            modifier = Modifier.size(78.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = if (weather.lastUpdated > 0L) "${weather.currentTemp}°" else "—",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = weather.currentCondition.label,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "습도 ${weather.currentHumidity?.let { "$it%" } ?: "—"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_rain_drop),
                                    contentDescription = "오늘 남은 시간 최고 강수확률",
                                    tint = Color(0xFF4AA3FF),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("${weather.todayPop}%", fontSize = 11.sp, color = Color(0xFF4AA3FF))
                            }
                        }
                    }
                }
            }

            // ─── 2. 시간별 예보 (옆으로 스크롤) ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("기상청 시간별 예보", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        weather.hourlyForecastIssuedAt?.let {
                            Text("$it 발표", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        listOf(3, 1).forEach { interval ->
                            val selected = hourlyIntervalHours == interval
                            Text(
                                text = "${interval}시간",
                                fontSize = 10.sp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                                    .clickable { onHourlyIntervalSelected(interval) }
                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    if (weather.hourlyForecasts.isEmpty()) {
                        Text("시간별 예보를 불러오는 중…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (hourlyIntervalHours == 3) {
                                val periods = summarizeThreeHours(weather.hourlyForecasts)
                                periods.forEachIndexed { index, item ->
                                    if (index > 0 && periods[index - 1].date != item.date) ForecastDateDivider()
                                    ThreeHourForecastCell(item, index == 0 || periods[index - 1].date != item.date, latitude, longitude)
                                }
                            } else {
                                weather.hourlyForecasts.forEachIndexed { index, item ->
                                    if (index > 0 && weather.hourlyForecasts[index - 1].date != item.date) ForecastDateDivider()
                                    HourlyForecastCell(item, index == 0 || weather.hourlyForecasts[index - 1].date != item.date, latitude, longitude)
                                }
                            }
                        }
                    }
                }
            }

            // ─── 3. 내일·모레 오전·오후 예보 ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("기상청 일별 예보", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("내일", "모레").forEachIndexed { index, day ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                    .padding(5.dp)
                            ) {
                                Text(day, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                                Spacer(modifier = Modifier.height(1.dp))
                                val offset = (index + 1) * 2
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    HalfDayForecastCell("오전", weather.halfDayForecasts.getOrNull(offset), Modifier.weight(1f))
                                    HalfDayForecastCell("오후", weather.halfDayForecasts.getOrNull(offset + 1), Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ─── 4. 대기질 (미세먼지 / 초미세먼지) 카드 ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "실시간 대기질 · 에어코리아",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AirQualityItem(
                            title = "미세먼지 (PM10)",
                            value = if (weather.pm10 >= 0) "${weather.pm10} ㎍/㎥" else "자료 없음",
                            color = weather.getPm10Color()
                        )
                        AirQualityItem(
                            title = "초미세먼지 (PM2.5)",
                            value = if (weather.pm25 >= 0) "${weather.pm25} ㎍/㎥" else "자료 없음",
                            color = weather.getPm25Color()
                        )
                    }
                }
            }

            // ─── 5. 기상청 날씨누리 바로가기 ───
            Card(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.weather.go.kr/w/index.do"))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_tab_weather),
                            contentDescription = "기상청 날씨누리",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "홈 - 기상청 날씨누리",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "https://www.weather.go.kr/w/index.do",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_open_in_new),
                        contentDescription = "바로가기",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

        }
    }
}

@Composable
private fun HourlyForecastCell(forecast: HourlyForecast, showDate: Boolean, latitude: Double, longitude: Double) {
    Column(modifier = Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (showDate && forecast.date.length == 8) "${forecast.date.substring(4, 6).toInt()}/${forecast.date.substring(6, 8).toInt()}" else " ",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("${forecast.time.take(2)}시", fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Image(
            painter = painterResource(weatherIconRes(forecast.condition, forecastDateTime(forecast.date, forecast.time), latitude, longitude)),
            contentDescription = forecast.condition.label,
            modifier = Modifier.size(30.dp)
        )
        Text(forecast.temperature?.let { "$it°" } ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(forecast.pop?.let { "$it%" } ?: "—", fontSize = 11.sp, color = Color(0xFF4AA3FF))
    }
}

@Composable
private fun ForecastDateDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 14.dp)
            .width(1.dp)
            .height(68.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    )
}

private val forecastDateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

private fun forecastDateTime(date: String, time: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(date + time, forecastDateTimeFormat) }.getOrNull()

private fun weatherIconRes(
    condition: WeatherCondition,
    dateTime: LocalDateTime?,
    latitude: Double,
    longitude: Double
): Int {
    if (dateTime == null || !SolarTime.isNight(dateTime, latitude, longitude)) return condition.iconRes
    return when (condition) {
        WeatherCondition.CLEAR -> R.drawable.ic_weather_clear_night
        WeatherCondition.CLOUDY -> R.drawable.ic_weather_cloudy_night
        else -> condition.iconRes
    }
}

@Composable
private fun ThreeHourForecastCell(forecast: ThreeHourForecast, showDate: Boolean, latitude: Double, longitude: Double) {
    Column(modifier = Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (showDate && forecast.date.length == 8) "${forecast.date.substring(4, 6).toInt()}/${forecast.date.substring(6, 8).toInt()}" else " ",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("${forecast.startTime.take(2)}시", fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Image(
            painter = painterResource(weatherIconRes(forecast.condition, forecastDateTime(forecast.date, forecast.startTime)?.plusHours(1), latitude, longitude)),
            contentDescription = forecast.condition.label,
            modifier = Modifier.size(30.dp)
        )
        val temperature = when {
            forecast.minTemp == null -> "—"
            forecast.minTemp == forecast.maxTemp -> "${forecast.minTemp}°"
            else -> "${forecast.minTemp}~${forecast.maxTemp}°"
        }
        Text(temperature, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(forecast.pop?.let { "$it%" } ?: "—", fontSize = 11.sp, color = Color(0xFF4AA3FF))
    }
}

@Composable
private fun HalfDayForecastCell(label: String, forecast: HalfDayForecast?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (forecast != null) {
            Image(
                painter = painterResource(forecast.condition.iconRes),
                contentDescription = "$label ${forecast.condition.label}",
                modifier = Modifier.size(33.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(33.dp))
        }
        Text(
            forecast?.let { formatTemperatureRange(it.minTemp, it.maxTemp) } ?: "자료 없음",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(forecast?.pop?.let { "$it%" } ?: "—", fontSize = 11.sp, color = Color(0xFF4AA3FF))
    }
}

@Composable
private fun AirQualityItem(
    title: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
