package com.toki.weather.data.repository

import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

/** 관측 기온·습도·풍속으로 추정한 체감온도. 관측값이 부족하면 표시하지 않는다. */
fun apparentTemperatureCelsius(temperature: Double?, humidity: Int?, windSpeed: Double?): Int? {
    if (temperature == null || !temperature.isFinite() || humidity !in 0..100 ||
        windSpeed == null || !windSpeed.isFinite() || windSpeed < 0
    ) return null
    val vaporPressure = humidity!! / 100.0 * 6.105 * exp(17.27 * temperature / (237.7 + temperature))
    return (temperature + 0.33 * vaporPressure - 0.70 * windSpeed - 4.0).roundToInt()
}

private data class RainfallRange(val minimum: Double, val maximum: Double) {
    val exact: Boolean get() = minimum == maximum
}

private fun parseRainfall(raw: String?): RainfallRange? {
    val value = raw?.trim()?.replace(" ", "") ?: return null
    if (value == "강수없음") return RainfallRange(0.0, 0.0)
    val lessThan = Regex("([0-9]+(?:\\.[0-9]+)?)mm미만").matchEntire(value)
    if (lessThan != null) return RainfallRange(0.0, lessThan.groupValues[1].toDouble())
    val range = Regex("([0-9]+(?:\\.[0-9]+)?)~([0-9]+(?:\\.[0-9]+)?)mm").matchEntire(value)
    if (range != null) return RainfallRange(range.groupValues[1].toDouble(), range.groupValues[2].toDouble())
    val exact = Regex("([0-9]+(?:\\.[0-9]+)?)(?:mm)?").matchEntire(value)
    return exact?.groupValues?.get(1)?.toDoubleOrNull()?.let { RainfallRange(it, it) }
}

private fun rainfallNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private fun formatRainfall(range: RainfallRange?): String = when {
    range == null -> "—"
    range.maximum == 0.0 -> "-"
    range.exact -> "${rainfallNumber(range.minimum)}mm"
    range.minimum == 0.0 -> "<${rainfallNumber(range.maximum)}mm"
    else -> "${rainfallNumber(range.minimum)}~${rainfallNumber(range.maximum)}mm"
}

fun formatHourlyRainfall(raw: String?): String = formatRainfall(parseRainfall(raw))

/** 1시간 강수량을 합산한다. 누락된 시간이 있으면 임의로 0mm를 채우지 않는다. */
fun summarizeRainfall(values: List<String?>): String {
    val ranges = values.map { parseRainfall(it) }
    if (ranges.isEmpty() || ranges.any { it == null }) return "—"
    return formatRainfall(RainfallRange(ranges.sumOf { it!!.minimum }, ranges.sumOf { it!!.maximum }))
}
