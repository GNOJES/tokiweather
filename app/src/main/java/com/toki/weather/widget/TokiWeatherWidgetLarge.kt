package com.toki.weather.widget

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
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.WidgetThemeConfig
import kotlinx.coroutines.flow.first

/**
 * 3×2 대형 위젯 (노바런처 8×8 등 세로 공간이 있는 홈 화면 전용)
 * - 상단: 오늘 날씨 (지역명 + 큰 3D 아이콘 + 기온 + 미세먼지 + 오늘 강수확률 바)
 * - 하단: 내일 / 모레 2열 예보 (아이콘 + 기온 + 강수확률 바)
 * - 런타임 dp 추측에 의존하지 않고 항상 세로 2단 레이아웃을 100% 보장
 */
class TokiWeatherWidgetLarge : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dataStore = WeatherDataStore(context)
        val weather = dataStore.weatherFlow.first()
        val theme = dataStore.themeFlow.first()
        val fontScale = context.resources.configuration.fontScale.takeIf { it > 0f } ?: 1.0f

        provideContent {
            GlanceTheme {
                LargeWidgetLayout(weather, theme, fontScale)
            }
        }
    }
}

private fun Float.fixedSp(fontScale: Float): TextUnit = (this / fontScale).sp
private fun Int.fixedSp(fontScale: Float): TextUnit = (this.toFloat() / fontScale).sp

@Composable
private fun LargeWidgetLayout(
    weather: CachedWeather,
    theme: WidgetThemeConfig,
    fontScale: Float
) {
    val hasData = weather.lastUpdated > 0L

    // 1. 배경색 및 글자색 계산
    val baseBgInt = try {
        android.graphics.Color.parseColor(theme.backgroundColorHex)
    } catch (_: Exception) {
        android.graphics.Color.parseColor("#261643")
    }
    val widgetBgColor = ComposeColor(baseBgInt).copy(alpha = theme.backgroundAlpha)

    val baseTextInt = try {
        android.graphics.Color.parseColor(theme.textColorHex)
    } catch (_: Exception) {
        android.graphics.Color.WHITE
    }
    val mainTextColor = ComposeColor(baseTextInt)
    val subTextColor = mainTextColor.copy(alpha = 0.75f)

    val textColorProvider = ColorProvider(mainTextColor)
    val subTextColorProvider = ColorProvider(subTextColor)

    // 가용 너비 및 패딩
    val currentWidth = LocalSize.current.width.takeIf { it.value > 80f } ?: 130.dp
    val isNarrow = currentWidth < 140.dp

    val horizPadding = if (isNarrow) 8.dp else 12.dp
    val vertPadding = 8.dp
    val forecastColSpacer = 6.dp

    // 하단 내일/모레 각 열 너비 (여백 제외 50% 균등 분할)
    val contentWidth = (currentWidth - (horizPadding * 2)).coerceAtLeast(100.dp)
    val forecastItemWidth = (contentWidth - forecastColSpacer) / 2f

    val todayIconSize = if (isNarrow) 44.dp else 48.dp
    val todayTempSize = if (isNarrow) 22 else 24
    val todayPmSize = if (isNarrow) 8.5f else 9f
    val locNameSize = if (isNarrow) 9.5f else 10f
    val forecastIconSize = if (isNarrow) 24.dp else 26.dp
    val forecastTempSize = if (isNarrow) 9.5f else 10.5f
    val forecastLabelSize = if (isNarrow) 8.5f else 9.5f
    val popBlockSize = if (isNarrow) 3.5.dp else 4.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgColor)
            .cornerRadius(16.dp)
            .padding(start = 7.dp, end = 7.dp, top = 3.dp, bottom = 6.dp)
            .clickable(
                actionStartActivity<MainActivity>(
                    androidx.glance.action.actionParametersOf(
                        androidx.glance.action.ActionParameters.Key<String>("widget_type") to "3x2"
                    )
                )
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasData) {
            Text(
                text = "${weather.locationName} · 날씨 불러오는 중…",
                style = TextStyle(
                    color = textColorProvider,
                    fontSize = 11.fixedSp(fontScale),
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth()
            )
        } else {
            // ─── 1. 상단: 우측 정렬 지역명 (최상단 밀착) ───
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
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
                    style = TextStyle(
                        color = subTextColorProvider,
                        fontSize = locNameSize.fixedSp(fontScale),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            // ─── 2. 오늘 날씨: [아이콘] + [기온 & 강수확률] + [미세/초미세 동그라미 2열] (가운데 정렬) ───
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(weather.currentCondition.iconRes),
                    contentDescription = weather.currentCondition.label,
                    modifier = GlanceModifier.size(todayIconSize)
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${weather.currentTemp}°",
                        style = TextStyle(
                            color = textColorProvider,
                            fontSize = todayTempSize.fixedSp(fontScale),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    // 강수확률: [비 아이콘] + %
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_rain_drop),
                            contentDescription = "강수확률",
                            colorFilter = ColorFilter.tint(ColorProvider(ComposeColor(0xFF4AA3FF))),
                            modifier = GlanceModifier.size(10.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(2.dp))
                        Text(
                            text = "${weather.todayPop}%",
                            style = TextStyle(
                                color = subTextColorProvider,
                                fontSize = (todayPmSize + 1.5f).fixedSp(fontScale),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.width(10.dp))
                // 미세먼지(위), 초미세먼지(아래) 작은 컬러 동그라미 2열
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_circle_dot),
                        contentDescription = "미세먼지",
                        colorFilter = ColorFilter.tint(ColorProvider(weather.getPm10Color())),
                        modifier = GlanceModifier.size(7.dp)
                    )
                    Spacer(modifier = GlanceModifier.height(5.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_circle_dot),
                        contentDescription = "초미세먼지",
                        colorFilter = ColorFilter.tint(ColorProvider(weather.getPm25Color())),
                        modifier = GlanceModifier.size(7.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // ─── 3. 하단: 내일 & 모레 예보 (좌우 50% 균등 분할) ───
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 내일 (좌측 50%)
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "내일",
                        style = TextStyle(
                            color = subTextColorProvider,
                            fontSize = forecastLabelSize.fixedSp(fontScale)
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Image(
                        provider = ImageProvider(weather.tomorrowCondition.iconRes),
                        contentDescription = weather.tomorrowCondition.label,
                        modifier = GlanceModifier.size(forecastIconSize)
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        text = "${weather.tomorrowMin}° / ${weather.tomorrowMax}°",
                        style = TextStyle(
                            color = textColorProvider,
                            fontSize = forecastTempSize.fixedSp(fontScale),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    WidgetPopBar(
                        pop = weather.tomorrowPop,
                        textColor = mainTextColor,
                        blockSize = popBlockSize
                    )
                }

                // 모레 (우측 50%)
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "모레",
                        style = TextStyle(
                            color = subTextColorProvider,
                            fontSize = forecastLabelSize.fixedSp(fontScale)
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Image(
                        provider = ImageProvider(weather.dayAfterCondition.iconRes),
                        contentDescription = weather.dayAfterCondition.label,
                        modifier = GlanceModifier.size(forecastIconSize)
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        text = "${weather.dayAfterMin}° / ${weather.dayAfterMax}°",
                        style = TextStyle(
                            color = textColorProvider,
                            fontSize = forecastTempSize.fixedSp(fontScale),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    WidgetPopBar(
                        pop = weather.dayAfterPop,
                        textColor = mainTextColor,
                        blockSize = popBlockSize
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPopBar(
    pop: Int,
    textColor: ComposeColor,
    blockSize: Dp
) {
    val filled = when {
        pop < 20 -> 0
        pop < 40 -> 1
        pop < 60 -> 2
        pop < 80 -> 3
        else -> 4
    }

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
