package com.toki.weather.widget

import com.toki.weather.data.model.forDisplay
import com.toki.weather.util.rememberWeatherNow
import com.toki.weather.data.model.formatTemperatureRange

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.toki.weather.MainActivity
import com.toki.weather.R
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.util.DateTimeUtils
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.WidgetThemeConfig
import com.toki.weather.util.currentWeatherIconRes
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 토끼날씨 2×1 위젯
 * - OS 글자 크기(fontScale) 설정의 영향을 받지 않도록 fixedSp 보정 적용
 * - 상단: 날짜 + 위치
 * - 좌측: 현재 날씨 아이콘·기온·최고 강수확률·대기질 색상 점
 * - 우측: 내일·모레 아이콘·최저/최고 기온·강수확률 4칸 바
 */
class TokiWeatherWidget : GlanceAppWidget() {

    // 런처의 실제 물리 크기를 반영하기 위해 SizeMode.Exact 선언 (SizeMode.Single의 minWidth 130dp 고정 방지)
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dataStore = WeatherDataStore(context)
        val weather = dataStore.weatherFlow.first()
        val theme = dataStore.themeFlow.first()
        val fontScale = context.resources.configuration.fontScale.takeIf { it > 0f } ?: 1.0f

        provideContent {
            val currentWeather by dataStore.weatherFlow.collectAsState(initial = weather)
            val currentTheme by dataStore.themeFlow.collectAsState(initial = theme)
            GlanceTheme {
                WeatherWidgetContent(currentWeather, currentTheme, fontScale)
            }
        }
    }
}

// 시스템 글씨 크기 설정을 상쇄하여 항상 의도된 고정 크기로 렌더링하는 헬퍼 함수
private fun Float.fixedSp(fontScale: Float): TextUnit = (this / fontScale).sp
private fun Int.fixedSp(fontScale: Float): TextUnit = (this.toFloat() / fontScale).sp

@Composable
private fun WeatherWidgetContent(
    weather: CachedWeather,
    theme: WidgetThemeConfig,
    fontScale: Float
) {
    val now by rememberWeatherNow()
    val weather = weather.forDisplay(now)
    val hasData = weather.lastUpdated > 0L

    // 1. 배경색 및 투명도 계산 (ComposeColor 사용 -> ResourceNotFoundException 방지)
    val baseBgInt = try {
        android.graphics.Color.parseColor(theme.backgroundColorHex)
    } catch (_: Exception) {
        android.graphics.Color.parseColor("#261643")
    }
    val widgetBgColor = ComposeColor(baseBgInt).copy(alpha = theme.backgroundAlpha)

    // 2. 글자색 계산
    val baseTextInt = try {
        android.graphics.Color.parseColor(theme.textColorHex)
    } catch (_: Exception) {
        android.graphics.Color.WHITE
    }
    val mainTextColor = ComposeColor(baseTextInt)
    val subTextColor = mainTextColor.copy(alpha = 0.75f)

    val textColorProvider = ColorProvider(mainTextColor)
    val subTextColorProvider = ColorProvider(subTextColor)

    // One UI 4×7의 실제 할당 크기(약 235×99dp)를 기준으로 가로 배치한다.
    val widgetWidth = LocalSize.current.width
    val widgetHeight = LocalSize.current.height
    val isCompact = widgetWidth < 210.dp || widgetHeight < 90.dp
    val horizontalPadding = if (isCompact) 7.dp else 9.dp
    val contentWidth = widgetWidth - horizontalPadding * 2
    val columnGap = 5.dp
    val currentWidth = (contentWidth - columnGap) * 0.52f
    val forecastWidth = (contentWidth - columnGap - currentWidth - 3.dp) / 2f
    val currentIconSize = if (isCompact) 36.dp else 44.dp
    val currentTempSize = if (isCompact) 23 else 26
    val forecastIconSize = if (isCompact) 22.dp else 26.dp
    val forecastTempSize = if (isCompact) 9f else 10.5f
    val headerSize = if (isCompact) 9f else 10.5f

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgColor)
            .cornerRadius(16.dp)
            .padding(horizontal = horizontalPadding, vertical = 5.dp)
            .clickable(
                actionStartActivity<MainActivity>(
                    androidx.glance.action.actionParametersOf(
                        androidx.glance.action.ActionParameters.Key<String>("widget_type") to "2x1"
                    )
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!hasData) {
            Text(
                text = "${weather.locationName} · 날씨 불러오는 중…",
                style = TextStyle(
                    color = textColorProvider,
                    fontSize = 11.fixedSp(fontScale),
                    textAlign = TextAlign.Center
                ),
                maxLines = 2,
                modifier = GlanceModifier.fillMaxWidth()
            )
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateTimeUtils.todayDateWithDayOfWeekString(now.toLocalDate()),
                    style = TextStyle(color = subTextColorProvider,
                        fontSize = headerSize.fixedSp(fontScale), fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Row(
                    modifier = GlanceModifier.width(contentWidth * 0.42f),
                    horizontalAlignment = Alignment.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_location_pin),
                        contentDescription = "위치",
                        colorFilter = ColorFilter.tint(subTextColorProvider),
                        modifier = GlanceModifier.size(8.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        text = weather.locationName,
                        style = TextStyle(color = subTextColorProvider,
                            fontSize = headerSize.fixedSp(fontScale), fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(3.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier.width(currentWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(currentWeatherIconRes(
                            weather.currentCondition,
                            now,
                            weather.refresh.latitude ?: weather.airQualityLatitude ?: 37.5665,
                            weather.refresh.longitude ?: weather.airQualityLongitude ?: 126.9780
                        )),
                        contentDescription = "현재 날씨 ${weather.currentCondition.label}",
                        modifier = GlanceModifier.size(currentIconSize)
                    )
                    Spacer(modifier = GlanceModifier.width(3.dp))
                    Column {
                        Text(
                            text = "${weather.currentTemp}°",
                            style = TextStyle(color = textColorProvider,
                                fontSize = currentTempSize.fixedSp(fontScale), fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_rain_drop),
                                contentDescription = "오늘 최고 강수확률",
                                colorFilter = ColorFilter.tint(ColorProvider(ComposeColor(0xFF4AA3FF))),
                                modifier = GlanceModifier.size(9.dp)
                            )
                            Spacer(modifier = GlanceModifier.width(2.dp))
                            Text(
                                text = weather.todayPop?.let { "$it%" } ?: "—",
                                style = TextStyle(color = subTextColorProvider,
                                    fontSize = 10.fixedSp(fontScale), fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_circle_dot),
                            contentDescription = if (weather.pm10 >= 0) "미세먼지 ${weather.pm10}" else "미세먼지 자료 없음",
                            colorFilter = ColorFilter.tint(ColorProvider(weather.getPm10Color())),
                            modifier = GlanceModifier.size(7.dp)
                        )
                        Spacer(modifier = GlanceModifier.height(5.dp))
                        Image(
                            provider = ImageProvider(R.drawable.ic_circle_dot),
                            contentDescription = if (weather.pm25 >= 0) "초미세먼지 ${weather.pm25}" else "초미세먼지 자료 없음",
                            colorFilter = ColorFilter.tint(ColorProvider(weather.getPm25Color())),
                            modifier = GlanceModifier.size(7.dp)
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.width(columnGap))
                CompactForecast(
                    "내일", weather.tomorrowCondition, weather.tomorrowMin, weather.tomorrowMax,
                    weather.tomorrowPop, forecastWidth, forecastIconSize, forecastTempSize,
                    mainTextColor, fontScale
                )
                Spacer(modifier = GlanceModifier.width(3.dp))
                CompactForecast(
                    "모레", weather.dayAfterCondition, weather.dayAfterMin, weather.dayAfterMax,
                    weather.dayAfterPop, forecastWidth, forecastIconSize, forecastTempSize,
                    mainTextColor, fontScale
                )
            }
        }
    }
}

@Composable
private fun CompactForecast(
    label: String,
    condition: WeatherCondition,
    minimum: Int?,
    maximum: Int?,
    pop: Int?,
    width: Dp,
    iconSize: Dp,
    temperatureSize: Float,
    textColor: ComposeColor,
    fontScale: Float
) {
    Column(
        modifier = GlanceModifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.75f)),
                fontSize = 9.fixedSp(fontScale)),
            maxLines = 1
        )
        Image(provider = ImageProvider(condition.iconRes), contentDescription = condition.label,
            modifier = GlanceModifier.size(iconSize))
        Text(
            text = formatTemperatureRange(minimum, maximum),
            style = TextStyle(color = ColorProvider(textColor),
                fontSize = temperatureSize.fixedSp(fontScale), fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        PopBar(pop = pop, textColor = textColor, blockSize = 4.dp)
    }
}

/**
 * 4칸 정사각형 강수확률 가로 막대바
 * 0칸: 0~19%, 1칸: 20~39%, 2칸: 40~59%, 3칸: 60~79%, 4칸: 80~100%
 */
@Composable
private fun PopBar(
    pop: Int?,
    textColor: ComposeColor,
    blockSize: Dp
) {
    if (pop == null) {
        Text("—", style = TextStyle(color = ColorProvider(textColor), fontSize = 9.sp))
        return
    }
    val filled = when {
        pop < 20 -> 0
        pop < 40 -> 1
        pop < 60 -> 2
        pop < 80 -> 3
        else -> 4
    }

    // 채워진 칸: 시원한 강수 블루(#4AA3FF), 비어있는 칸: 은은한 반투명
    val activeColor = ColorProvider(ComposeColor(0xFF4AA3FF))
    val inactiveColor = ColorProvider(textColor.copy(alpha = 0.25f))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (i in 0 until 4) {
            Box(
                modifier = GlanceModifier
                    .size(blockSize)
                    .background(if (i < filled) activeColor else inactiveColor)
                    .cornerRadius(1.dp)
            ) {}
            if (i < 3) {
                Spacer(modifier = GlanceModifier.width(1.5.dp))
            }
        }
    }
}
