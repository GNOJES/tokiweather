package com.toki.weather.data.model

fun Int?.asTemperature(): String = this?.let { "$it°" } ?: "—"

fun formatTemperatureRange(min: Int?, max: Int?, separator: String = " / "): String =
    if (separator == "~") "${min?.toString() ?: "—"}~${max.asTemperature()}"
    else "${min.asTemperature()}$separator${max.asTemperature()}"
