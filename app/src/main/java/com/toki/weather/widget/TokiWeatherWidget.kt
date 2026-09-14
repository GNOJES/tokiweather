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

    // 위젯 너비 기반 5:3:3 비율 계산 (오늘 5, 내일 3, 모레 3)
    val widgetWidth = LocalSize.current.width.takeIf { it.value > 50f } ?: 170.dp
    val contentWidth = (widgetWidth - 26.dp).coerceAtLeast(100.dp)
    val todayWidth = contentWidth * (5f / 11f)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBgColor)
            .cornerRadius(16.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clickable(actionStartActivity<MainActivity>()),
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
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth()
            )
        } else {
            // [상단: 날씨 정보 행] 오늘 5 : 내일 3 : 모레 3 비율
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [좌측: 현재 날씨] 전체 가로의 5/11 비율
                Column(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(todayWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = weather.currentCondition.emoji,
                        style = TextStyle(fontSize = 22.fixedSp(fontScale)),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        text = "${weather.currentTemp}°",
                        style = TextStyle(
                            color = textColorProvider,
                            fontSize = 18.fixedSp(fontScale),
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
                                fontSize = 10.fixedSp(fontScale),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = " · ",
                            style = TextStyle(
                                color = subTextColorProvider,
                                fontSize = 9.fixedSp(fontScale),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = if (weather.pm25 >= 0) "${weather.pm25}" else "-",
                            style = TextStyle(
                                color = ColorProvider(weather.getPm25Color()),
                                fontSize = 10.fixedSp(fontScale),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                // [우측: 상단 우측 지역명 + 하단 내일/모레 예보] 전체 가로의 6/11 비율
                Column(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 우측 상단: 위치 아이콘 + 지역명
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_location_pin),
                            contentDescription = "위치",
                            colorFilter = ColorFilter.tint(subTextColorProvider),
                            modifier = GlanceModifier.size(10.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(2.dp))
                        Text(
                            text = weather.locationName,
                            style = TextStyle(
                                color = subTextColorProvider,
                                fontSize = 10.fixedSp(fontScale),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    // 2. 우측 하단: 내일 & 모레 예보 (각 defaultWeight -> 1:1 분할로 3 : 3)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 내일
                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "내일",
                                style = TextStyle(
                                    color = subTextColorProvider,
                                    fontSize = 9.fixedSp(fontScale)
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(1.dp))
                            Text(
                                text = weather.tomorrowCondition.emoji,
                                style = TextStyle(fontSize = 13.fixedSp(fontScale)),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(1.dp))
                            Text(
                                text = "${weather.tomorrowMin}~${weather.tomorrowMax}°",
                                style = TextStyle(
                                    color = textColorProvider,
                                    fontSize = 9.fixedSp(fontScale)
                                ),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(4.dp))

                        // 모레
                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "모레",
                                style = TextStyle(
                                    color = subTextColorProvider,
                                    fontSize = 9.fixedSp(fontScale)
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(1.dp))
                            Text(
                                text = weather.dayAfterCondition.emoji,
                                style = TextStyle(fontSize = 13.fixedSp(fontScale)),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(1.dp))
                            Text(
                                text = "${weather.dayAfterMin}~${weather.dayAfterMax}°",
                                style = TextStyle(
                                    color = textColorProvider,
                                    fontSize = 9.fixedSp(fontScale)
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

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
                        blockSize = 4.dp
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                // 내일 & 모레 강수확률 바 (우측 6/11 공간)
                Row(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        PopBar(
                            pop = weather.tomorrowPop,
                            textColor = mainTextColor,
                            blockSize = 4.dp
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(4.dp))

                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        PopBar(
                            pop = weather.dayAfterPop,
                            textColor = mainTextColor,
                            blockSize = 4.dp
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
