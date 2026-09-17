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
import androidx.glance.layout.fillMaxHeight
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
 * 토끼날씨 2×1 위젯
 * - OS 글자 크기(fontScale) 설정의 영향을 받지 않도록 fixedSp 보정 적용
 * - 좌측: 현재 날씨 (전체 높이 활용, 큰 아이콘 & 기온 + 강수확률 4칸 바)
 * - 우측 상단: 위치 아이콘 + 지역명 (우측 정렬)
 * - 우측 하단: 내일 및 모레 예보 (기온 + 강수확률 4칸 바)
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
            GlanceTheme {
                WeatherWidgetContent(weather, theme, fontScale)
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

    // 위젯 너비 및 높이 기반 반응형 비율 계산 (오늘 5, 내일 3, 모레 3)
    val widgetWidth = LocalSize.current.width.takeIf { it.value > 100f } ?: 174.dp
    val widgetHeight = LocalSize.current.height.takeIf { it.value > 40f } ?: 90.dp
    val isCompact = widgetWidth < 155.dp
    val isShort = widgetHeight < 80.dp

    val horizPadding = if (isCompact) 8.dp else 10.dp
    val vertPadding = if (isShort) 2.dp else 4.dp
    val vertSpacer = if (isShort) 2.dp else 3.dp

    val interColSpacer = if (isCompact) 4.dp else 6.dp
    val forecastSpacer = if (isCompact) 2.dp else 4.dp

    // 5 : 3 : 3 비율에 따른 엄격한 가로 폭 계산 (오늘 5/11, 내일 3/11, 모레 3/11)
    val totalContentWidth = (widgetWidth - (horizPadding * 2) - interColSpacer).coerceAtLeast(100.dp)
    val todayWidth = totalContentWidth * (5f / 11f)
    val rightWidth = totalContentWidth * (6f / 11f)
    val forecastItemWidth = (rightWidth - forecastSpacer) / 2f

    val todayIconSize = if (isCompact) 26.dp else 30.dp
    val todayTempSize = if (isCompact) 16 else 18
    val todayPmSize = if (isCompact) 9 else 10
    val locNameSize = if (isCompact) 9 else 10
    val forecastIconSize = if (isCompact) 16.dp else 18.dp
    val subTempSize = if (isCompact) 7.5f else 8.5f
    val popBlockSize = if (isCompact) 3.5.dp else 4.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgColor)
            .cornerRadius(16.dp)
            .padding(horizontal = horizPadding, vertical = vertPadding)
            .clickable(actionStartActivity<MainActivity>()),
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
            // [상단: 날씨 정보 행] 오늘 5 : 내일 3 : 모레 3 비율
            // defaultWeight() 대신 수학적 계산 폭(todayWidth, rightWidth)을 적용하여 엄격한 5:3:3 구현
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // [좌측: 현재 날씨] 전체 가로의 5/11 비율
                Column(
                    modifier = GlanceModifier.width(todayWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        provider = ImageProvider(weather.currentCondition.iconRes),
                        contentDescription = weather.currentCondition.label,
                        modifier = GlanceModifier.size(todayIconSize)
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        text = "${weather.currentTemp}°",
                        style = TextStyle(
                            color = textColorProvider,
                            fontSize = todayTempSize.fixedSp(fontScale),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    // 미세먼지(PM10) · 초미세먼지(PM2.5) 수치 (등급별 색상 적용)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (weather.pm10 >= 0) "${weather.pm10}" else "-",
                            style = TextStyle(
                                color = ColorProvider(weather.getPm10Color()),
                                fontSize = todayPmSize.fixedSp(fontScale),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = " · ",
                            style = TextStyle(
                                color = subTextColorProvider,
                                fontSize = (todayPmSize - 1).fixedSp(fontScale),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = if (weather.pm25 >= 0) "${weather.pm25}" else "-",
                            style = TextStyle(
                                color = ColorProvider(weather.getPm25Color()),
                                fontSize = todayPmSize.fixedSp(fontScale),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(interColSpacer))

                // [우측: 상단 우측 지역명 + 하단 내일/모레 예보] 전체 가로의 6/11 비율
                Column(
                    modifier = GlanceModifier.width(rightWidth),
                    horizontalAlignment = Alignment.End
                ) {
                    // 1. 우측 상단: 위치 아이콘 + 지역명 (상단 밀착 및 우측 5dp 여백으로 좌측 이동)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(end = 5.dp),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_location_pin),
                            contentDescription = "위치",
                            colorFilter = ColorFilter.tint(subTextColorProvider),
                            modifier = GlanceModifier.size(if (isCompact) 8.dp else 10.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(1.5.dp))
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

                    Spacer(modifier = GlanceModifier.height(3.dp))

                    // 2. 우측 하단: 내일 & 모레 예보 (각 3/11 분할로 3 : 3)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 내일
                        Column(
                            modifier = GlanceModifier.width(forecastItemWidth),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "내일",
                                style = TextStyle(
                                    color = subTextColorProvider,
                                    fontSize = (subTempSize - 0.5f).fixedSp(fontScale)
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
                                text = "${weather.tomorrowMin}~${weather.tomorrowMax}°",
                                style = TextStyle(
                                    color = textColorProvider,
                                    fontSize = subTempSize.fixedSp(fontScale)
                                ),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(forecastSpacer))

                        // 모레
                        Column(
                            modifier = GlanceModifier.width(forecastItemWidth),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "모레",
                                style = TextStyle(
                                    color = subTextColorProvider,
                                    fontSize = (subTempSize - 0.5f).fixedSp(fontScale)
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
                                text = "${weather.dayAfterMin}~${weather.dayAfterMax}°",
                                style = TextStyle(
                                    color = textColorProvider,
                                    fontSize = subTempSize.fixedSp(fontScale)
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(vertSpacer))

            // [하단: 강수확률 바 행] 오늘 / 내일 / 모레 모두 동일한 수평 baseline에 배치
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 오늘 강수확률 바 (폭 = todayWidth)
                Box(
                    modifier = GlanceModifier.width(todayWidth),
                    contentAlignment = Alignment.Center
                ) {
                    PopBar(
                        pop = weather.todayPop,
                        textColor = mainTextColor,
                        blockSize = popBlockSize
                    )
                }

                Spacer(modifier = GlanceModifier.width(interColSpacer))

                // 내일 & 모레 강수확률 바 (우측 6/11 공간)
                Row(
                    modifier = GlanceModifier.width(rightWidth),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier.width(forecastItemWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        PopBar(
                            pop = weather.tomorrowPop,
                            textColor = mainTextColor,
                            blockSize = popBlockSize
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(forecastSpacer))

                    Box(
                        modifier = GlanceModifier.width(forecastItemWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        PopBar(
                            pop = weather.dayAfterPop,
                            textColor = mainTextColor,
                            blockSize = popBlockSize
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4칸 정사각형 강수확률 가로 막대바
 * 0칸: 0~19%, 1칸: 20~39%, 2칸: 40~59%, 3칸: 60~79%, 4칸: 80~100%
 */
@Composable
private fun PopBar(
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
