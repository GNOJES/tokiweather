package com.toki.weather.worker

import kotlinx.coroutines.CancellationException

/** 조회 실패와 표시 실패를 구분한다. 캐시 재표시는 날씨 조회 성공을 뜻하지 않는다. */
data class BackgroundRefreshOutcome(
    val fetchFailure: Throwable?,
    val widgetFailures: List<Throwable>
) {
    val succeeded: Boolean get() = fetchFailure == null && widgetFailures.isEmpty()
}

suspend fun <T> backgroundRefreshAndUpdate(
    fetch: suspend () -> Result<T>,
    updateWidgets: List<suspend () -> Unit>
): BackgroundRefreshOutcome {
    val fetchFailure = try {
        fetch().exceptionOrNull()?.also { if (it is CancellationException) throw it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }
    // 실패 시에도 이전 캐시를 다시 그려 현재 시각의 일출/일몰 판정을 반영한다.
    // 한 종류의 표시 오류 때문에 다른 종류까지 갱신하지 못하는 것을 방지한다.
    val widgetFailures = mutableListOf<Throwable>()
    for (update in updateWidgets) {
        try {
            update()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            widgetFailures += e
        }
    }
    return BackgroundRefreshOutcome(fetchFailure, widgetFailures)
}
