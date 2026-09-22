package com.toki.weather.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 기상청 API 호출에 필요한 날짜/시간 유틸리티
 */
object DateTimeUtils {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm")
    private val DATE_WITH_DAY_FORMAT = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)

    /**
     * 단기예보 발표 시각 목록 (하루 8회)
     */
    private val VILAGE_FCST_BASE_TIMES = listOf(
        "0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300"
    )

    /**
     * 현재 시각 기준 초단기실황 base_date, base_time 계산
     * 매시 40분 이후 발표 → 현재 시각의 정시를 base_time으로 사용
     * 40분 이전이면 이전 시각 사용
     */
    fun getUltraSrtNcstBaseDateTime(now: LocalDateTime = LocalDateTime.now()): Pair<String, String> {
        val adjusted = if (now.minute < 40) {
            now.minusHours(1)
        } else {
            now
        }
        val baseDate = adjusted.format(DATE_FORMAT)
        val baseTime = String.format("%02d00", adjusted.hour)
        return Pair(baseDate, baseTime)
    }

    /**
     * 현재 시각 기준 단기예보 base_date, base_time 계산
     * 가장 최근 발표 시각을 찾아 반환
     * 각 발표 시각의 +10분 이후부터 데이터 제공 (예: 0200 발표 → 0210 이후 조회 가능)
     */
    fun getVilageFcstBaseDateTime(now: LocalDateTime = LocalDateTime.now()): Pair<String, String> {
        val currentTimeStr = now.format(TIME_FORMAT)
        val currentMinutes = now.hour * 60 + now.minute

        // 각 발표시각 + 10분(API 제공 지연) 기준으로 가장 최근 발표시각 찾기
        var baseTime: String? = null
        var baseDate = now.format(DATE_FORMAT)

        for (time in VILAGE_FCST_BASE_TIMES.reversed()) {
            val hour = time.substring(0, 2).toInt()
            val availableMinutes = hour * 60 + 10 // 발표 후 10분
            if (currentMinutes >= availableMinutes) {
                baseTime = time
                break
            }
        }

        // 0200 이전이면 전날 2300 사용
        if (baseTime == null) {
            baseTime = "2300"
            baseDate = now.minusDays(1).format(DATE_FORMAT)
        }

        return Pair(baseDate, baseTime)
    }

    /**
     * 오늘 날짜 문자열 (YYYYMMDD)
     */
    fun todayString(): String = LocalDate.now().format(DATE_FORMAT)

    /**
     * 오늘 날짜 및 요일 문자열 (M/d(E), 예: 9/22(화))
     */
    fun todayDateWithDayOfWeekString(date: LocalDate = LocalDate.now()): String =
        date.format(DATE_WITH_DAY_FORMAT)

    /**
     * 내일 날짜 문자열 (YYYYMMDD)
     */
    fun tomorrowString(): String = LocalDate.now().plusDays(1).format(DATE_FORMAT)

    /**
     * 모레 날짜 문자열 (YYYYMMDD)
     */
    fun dayAfterTomorrowString(): String = LocalDate.now().plusDays(2).format(DATE_FORMAT)
}
