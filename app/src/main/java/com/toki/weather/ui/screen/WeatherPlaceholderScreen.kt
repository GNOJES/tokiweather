package com.toki.weather.ui.screen

import com.toki.weather.data.model.formatTemperatureRange

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.toki.weather.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherPlaceholderScreen(
    weather: CachedWeather,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
            // ─── 1. 현재 날씨 메인 카드 ───
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(weather.currentCondition.iconRes),
                        contentDescription = weather.currentCondition.label,
                        modifier = Modifier.size(88.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${weather.currentTemp}°",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = weather.currentCondition.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_rain_drop),
                                contentDescription = "강수확률",
                                tint = Color(0xFF4AA3FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "강수확률 ${weather.todayPop}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ─── 2. 단기 예보 요약 & 상세 예보 준비 안내 카드 ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "예보 요약",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ForecastSimpleItem(
                            dayLabel = "내일",
                            condition = weather.tomorrowCondition.label,
                            iconRes = weather.tomorrowCondition.iconRes,
                            tempRange = formatTemperatureRange(weather.tomorrowMin, weather.tomorrowMax),
                            pop = weather.tomorrowPop
                        )
                        ForecastSimpleItem(
                            dayLabel = "모레",
                            condition = weather.dayAfterCondition.label,
                            iconRes = weather.dayAfterCondition.iconRes,
                            tempRange = formatTemperatureRange(weather.dayAfterMin, weather.dayAfterMax),
                            pop = weather.dayAfterPop
                        )
                    }
                }
            }

            // ─── 3. 대기질 (미세먼지 / 초미세먼지) 카드 ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "실시간 대기질 · 에어코리아",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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

            // ─── 4. 기상청 날씨누리 바로가기 ───
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

@Composable
private fun ForecastSimpleItem(
    dayLabel: String,
    condition: String,
    iconRes: Int,
    tempRange: String,
    pop: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = dayLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Image(
            painter = painterResource(iconRes),
            contentDescription = condition,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = tempRange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(text = "💧 $pop%", fontSize = 11.sp, color = Color(0xFF4AA3FF))
    }
}
