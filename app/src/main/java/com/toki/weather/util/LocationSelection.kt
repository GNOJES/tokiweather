package com.toki.weather.util

import kotlin.math.*

/** Android 객체와 분리해 실제 주소 응답 및 위치 시각으로 회귀 검증한다. */
data class AddressCandidate(
    val latitude: Double?,
    val longitude: Double?,
    val thoroughfare: String? = null,
    val featureName: String? = null,
    val addressLine: String? = null,
    val subLocality: String? = null,
    val locality: String? = null
)

data class LocationFix(val elapsedNanos: Long, val accuracyMeters: Float)

object LocationSelection {
    // 근처 주소일 뿐 경계 판정은 아니다. 멀리 떨어진 주소는 동 이름의 근거로 쓰지 않는다.
    private const val MAX_ADDRESS_DISTANCE_METERS = 200.0
    private const val MAX_FIX_AGE_NANOS = 120_000_000_000L
    private const val COMPARABLE_FIX_WINDOW_NANOS = 15_000_000_000L

    fun addressName(latitude: Double, longitude: Double, candidates: List<AddressCandidate>): String {
        val nearby = candidates.mapNotNull { candidate ->
            val lat = candidate.latitude ?: return@mapNotNull null
            val lon = candidate.longitude ?: return@mapNotNull null
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            val distance = distanceMeters(latitude, longitude, lat, lon)
            if (distance > MAX_ADDRESS_DISTANCE_METERS) null else candidate to distance
        }.sortedBy { it.second }
        for ((candidate, _) in nearby) {
            val structured = listOf(candidate.subLocality, candidate.thoroughfare)
                .firstOrNull { it != null && isDongName(it) }
            if (structured != null) return structured
            val token = candidate.addressLine.orEmpty().split(Regex("[\\s,()]+"))
                .lastOrNull(::isDongName)
            if (token != null) return token
            candidate.featureName?.takeIf(::isDongName)?.let { return it }
        }
        // 동 후보를 검증하지 못하면 구/시 단위로만 표시한다.
        return candidates.asSequence().flatMap { sequenceOf(it.subLocality, it.locality) }
            .filterNotNull().firstOrNull { it.matches(Regex("^[가-힣]+(?:구|군|시)$")) }
            ?: "현재 위치"
    }

    fun fixIndex(fixes: List<LocationFix>, nowNanos: Long): Int? {
        val eligible = fixes.indices.filter {
            val fix = fixes[it]
            fix.elapsedNanos > 0 && nowNanos - fix.elapsedNanos in 0..MAX_FIX_AGE_NANOS &&
                fix.accuracyMeters.isFinite() && fix.accuracyMeters > 0
        }
        val newest = eligible.maxOfOrNull { fixes[it].elapsedNanos } ?: return null
        return eligible.filter { newest - fixes[it].elapsedNanos <= COMPARABLE_FIX_WINDOW_NANOS }
            .minByOrNull { fixes[it].accuracyMeters }
    }

    private fun isDongName(value: String): Boolean =
        value.matches(Regex("^[가-힣][가-힣0-9]*(?:동[0-9]*가?|[0-9]+가|읍|면)$"))

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
