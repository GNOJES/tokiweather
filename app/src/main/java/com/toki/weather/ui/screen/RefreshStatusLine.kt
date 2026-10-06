package com.toki.weather.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun timeLabel(time: Long?): String = time?.takeIf { it > 0 }?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
} ?: "—"

@Composable
fun RefreshStatusLine(weather: CachedWeather, now: Long) {
    val context = LocalContext.current
    val store = remember { WeatherDataStore(context) }
    val events by store.diagnosticsFlow.collectAsState(initial = emptyList())
    val address by store.addressDiagnosticsFlow.collectAsState(initial = null)
    val interval by store.updateIntervalFlow.collectAsState(initial = 30)
    var details by remember { mutableStateOf(false) }
    val summary = buildList {
        add(if (weather.lastUpdated > 0) "${timeLabel(weather.lastUpdated)} 갱신" else "갱신 자료 없음")
        if (weather.usesPreviousWeather(now, interval, events)) add("이전 자료")
        if (weather.refresh.usedSavedLocation) add("마지막 확인 지역")
    }.joinToString(" · ")
    Text("$summary ⓘ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { details = true }.padding(top = 2.dp))
    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text("갱신 정보") },
        confirmButton = { TextButton(onClick = { details = false }) { Text("닫기") } },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("날씨 저장: ${timeLabel(weather.lastUpdated)}\n날씨 관측: ${timeLabel(weather.refresh.weatherObservedAt)}\n대기질 관측: ${timeLabel(weather.pmObservedAt)}\n위치 확인: ${timeLabel(weather.refresh.locationConfirmedAt)}", fontSize = 12.sp)
                Text(when {
                    weather.refresh.airQualityPending -> "대기질 조회 중"
                    weather.pm10 < 0 && weather.pm25 < 0 -> "대기질 자료 없음"
                    weather.refresh.airQualityRetained -> "대기질: 가까운 지역의 이전 관측값 사용"
                    else -> "대기질: 수신한 관측값 사용"
                }, fontSize = 12.sp)
                if (weather.refresh.usedSavedLocation) Text("새 위치를 얻지 못해 마지막 확인 지역의 날씨를 조회했습니다.", fontSize = 12.sp)
                address?.let { diagnostic ->
                    Text("지역명 판단: ${diagnostic.selection.name} · ${timeLabel(diagnostic.at)}", fontSize = 12.sp)
                    diagnostic.selection.candidates.forEach { candidate ->
                        val reason = when (candidate.reason) {
                            com.toki.weather.util.AddressReason.SELECTED -> "선택"
                            com.toki.weather.util.AddressReason.OUTSIDE_200M -> "200m 초과로 동 제외"
                            com.toki.weather.util.AddressReason.INVALID_COORDINATES -> "후보 좌표 없음"
                            com.toki.weather.util.AddressReason.NO_DONG -> "동 이름 없음"
                            com.toki.weather.util.AddressReason.NEARBY_UNUSED -> "더 가까운 후보 선택"
                        }
                        Text("${candidate.region ?: "지역명 없음"} · ${candidate.distanceMeters?.let { "${it}m" } ?: "거리 없음"} · $reason", fontSize = 11.sp)
                    }
                    Text("가까운 동 후보가 없으면 구·시로 표시합니다. 동 검증 거리: 200m", fontSize = 10.sp)
                }
                Text("최근 갱신 기록", fontSize = 13.sp)
                events.takeLast(18).reversed().forEach { event ->
                    Text("${timeLabel(event.at)} · ${sourceLabel(event.source)}\n${stageLabel(event.stage)} · ${stateLabel(event.state)}${if (event.durationMs > 0) " · ${event.durationMs}ms" else ""}", fontSize = 11.sp)
                }
                Text("위젯 ‘표시 요청’은 화면 갱신 요청을 보낸 기록입니다.", fontSize = 10.sp)
            }
        })
}

private fun sourceLabel(value: RefreshSource) = when(value) {
    RefreshSource.MANUAL -> "수동"; RefreshSource.INITIAL -> "최초 조회"; RefreshSource.AUTOMATIC -> "자동"
    RefreshSource.SETTINGS -> "설정"; RefreshSource.DISPLAY -> "위젯 표시"
}
private fun stageLabel(value: RefreshStage) = when(value) {
    RefreshStage.REQUEST -> "전체 요청"; RefreshStage.LOCATION -> "위치 확인"
    RefreshStage.OBSERVATION -> "현재 날씨"; RefreshStage.SHORT_FORECAST -> "단기예보"
    RefreshStage.PREVIOUS_FORECAST -> "이전 발표 보완"; RefreshStage.ULTRA_FORECAST -> "초단기예보"
    RefreshStage.WEATHER_SAVE -> "날씨 저장"; RefreshStage.AIR_STATIONS -> "측정소 조회"
    RefreshStage.AIR_MEASUREMENT -> "대기질 관측 조회"; RefreshStage.AIR_QUALITY -> "대기질"
    RefreshStage.AIR_SAVE -> "대기질 저장"; RefreshStage.WIDGET -> "위젯"
}
private fun stateLabel(value: RefreshState) = when(value) {
    RefreshState.STARTED -> "시작"; RefreshState.SUCCESS -> "성공"; RefreshState.FAILED -> "실패"
    RefreshState.TIMEOUT -> "시간 초과"; RefreshState.CANCELLED -> "중단"; RefreshState.SUPERSEDED -> "최신 요청으로 대체"
    RefreshState.REQUESTED -> "표시 요청"; RefreshState.MISSING -> "결측"; RefreshState.RETAINED -> "이전 관측 유지"
}
