package com.toki.weather.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toki.weather.R
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.formatTemperatureRange
import com.toki.weather.util.DateTimeUtils
import java.time.LocalDateTime

/** Compose counterpart of the measured One UI 234.67×98.67dp widget. */
@Composable
fun CompactWidgetPreview(weather: CachedWeather, location: String, icon: Int, background: Color,
                         text: Color, now: LocalDateTime) {
    val sub = text.copy(alpha = .75f)
    val currentWidth = (234.67f - 18f - 5f) * .52f
    val forecastWidth = (234.67f - 18f - 5f - currentWidth - 3f) / 2f
    Column(Modifier.size(234.67.dp, 98.67.dp).clip(RoundedCornerShape(16.dp))
        .background(background).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(DateTimeUtils.todayDateWithDayOfWeekString(now.toLocalDate()), fontSize = 10.5.sp, lineHeight = 10.5.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                fontWeight = FontWeight.Bold, color = sub, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(Modifier.width((216.67f * .42f).dp), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_location_pin), "위치", tint = sub, modifier = Modifier.size(8.dp))
                Spacer(Modifier.width(2.dp))
                Text(location, fontSize = 10.5.sp, lineHeight = 10.5.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), fontWeight = FontWeight.Bold, color = sub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(currentWidth.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), "현재 날씨 ${weather.currentCondition.label}", Modifier.size(44.dp))
                Spacer(Modifier.width(3.dp))
                Column {
                    Text("${weather.currentTemp}°", fontSize = 26.sp, lineHeight = 26.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), fontWeight = FontWeight.Bold, color = text, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_rain_drop), "오늘 최고 강수확률", tint = Color(0xFF4AA3FF), modifier = Modifier.size(9.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(weather.todayPop?.let { "$it%" } ?: "—", fontSize = 10.sp, lineHeight = 10.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), fontWeight = FontWeight.Bold, color = sub)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Icon(painterResource(R.drawable.ic_circle_dot), if (weather.pm10 < 0) "미세먼지 자료 없음" else "미세먼지 ${weather.pm10}",
                        tint = weather.getPm10Color(), modifier = Modifier.size(7.dp))
                    Spacer(Modifier.height(5.dp))
                    Icon(painterResource(R.drawable.ic_circle_dot), if (weather.pm25 < 0) "초미세먼지 자료 없음" else "초미세먼지 ${weather.pm25}",
                        tint = weather.getPm25Color(), modifier = Modifier.size(7.dp))
                }
            }
            Spacer(Modifier.width(5.dp))
            PreviewForecast("내일", weather.tomorrowCondition.iconRes, weather.tomorrowMin, weather.tomorrowMax, weather.tomorrowPop, forecastWidth, text)
            Spacer(Modifier.width(3.dp))
            PreviewForecast("모레", weather.dayAfterCondition.iconRes, weather.dayAfterMin, weather.dayAfterMax, weather.dayAfterPop, forecastWidth, text)
        }
    }
}

@Composable
private fun PreviewForecast(label: String, icon: Int, minimum: Int?, maximum: Int?, pop: Int?, width: Float, text: Color) {
    Column(Modifier.width(width.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, lineHeight = 9.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), color = text.copy(alpha = .75f), maxLines = 1)
        Image(painterResource(icon), label, Modifier.size(26.dp))
        Text(formatTemperatureRange(minimum, maximum), fontSize = 10.5.sp, lineHeight = 10.5.sp, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), fontWeight = FontWeight.Bold, color = text, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        ComposePopBar(pop, text)
    }
}
