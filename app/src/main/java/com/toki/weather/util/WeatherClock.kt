package com.toki.weather.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

/** Minute ticks while composed, plus an immediate app-resume tick after sleep/time changes. */
@Composable
fun rememberWeatherNow(lifecycle: Lifecycle? = null): State<LocalDateTime> =
    produceState(LocalDateTime.now(ZoneId.of("Asia/Seoul")), lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        }
        lifecycle?.addObserver(observer)
        try {
            while (true) {
                value = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        } finally { lifecycle?.removeObserver(observer) }
    }
