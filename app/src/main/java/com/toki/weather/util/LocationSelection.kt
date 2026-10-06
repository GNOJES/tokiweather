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

enum class AddressReason { SELECTED, OUTSIDE_200M, INVALID_COORDINATES, NO_DONG, NEARBY_UNUSED }
data class AddressCandidateDiagnostic(val region: String?, val distanceMeters: Int?, val reason: AddressReason)
data class AddressSelection(val name: String, val candidates: List<AddressCandidateDiagnostic> = emptyList())
data class AddressDiagnostics(val at: Long, val selection: AddressSelection)

data class LocationFix(val elapsedNanos: Long, val accuracyMeters: Float)

object LocationSelection {
    // 근처 주소일 뿐 경계 판정은 아니다. 멀리 떨어진 주소는 동 이름의 근거로 쓰지 않는다.
    private const val MAX_ADDRESS_DISTANCE_METERS = 200.0
    private const val MAX_FIX_AGE_NANOS = 120_000_000_000L
    private const val QUICK_FIX_AGE_NANOS = 30_000_000_000L
    private const val COMPARABLE_FIX_WINDOW_NANOS = 15_000_000_000L

    fun addressName(latitude: Double, longitude: Double, candidates: List<AddressCandidate>): String =
        selectAddress(latitude, longitude, candidates).name

    /** Persist only region tokens, rounded distance and fixed reasons, never full addresses. */
    fun selectAddress(latitude: Double, longitude: Double, candidates: List<AddressCandidate>): AddressSelection {
        val items = candidates.take(5)
        fun dong(candidate: AddressCandidate): String? =
            listOf(candidate.subLocality, candidate.thoroughfare).firstOrNull { it != null && isDongName(it) }
                ?: candidate.addressLine.orEmpty().split(Regex("[\\s,()]+" )).lastOrNull(::isDongName)
                ?: candidate.featureName?.takeIf(::isDongName)
        fun district(candidate: AddressCandidate): String? = listOf(candidate.subLocality, candidate.locality)
            .firstOrNull { it != null && it.matches(Regex("^[가-힣]+(?:구|군|시)$")) }
        val distances = items.map { candidate ->
            val lat = candidate.latitude
            val lon = candidate.longitude
            if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0) null
            else distanceMeters(latitude, longitude, lat, lon)
        }
        val selected = items.indices.filter { distances[it]?.let { d -> d <= MAX_ADDRESS_DISTANCE_METERS } == true }
            .sortedBy { distances[it] }.firstOrNull { dong(items[it]) != null }
        val name = selected?.let { dong(items[it]) } ?: items.firstNotNullOfOrNull(::district) ?: "현재 위치"
        return AddressSelection(name, items.mapIndexed { index, candidate ->
            val distance = distances[index]
            AddressCandidateDiagnostic(dong(candidate) ?: district(candidate), distance?.roundToInt(), when {
                distance == null -> AddressReason.INVALID_COORDINATES
                distance > MAX_ADDRESS_DISTANCE_METERS -> AddressReason.OUTSIDE_200M
                index == selected -> AddressReason.SELECTED
                dong(candidate) == null -> AddressReason.NO_DONG
                else -> AddressReason.NEARBY_UNUSED
            })
        })
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

    /** 바로 쓸 수 있는 최근 위치가 있으면 새 GPS 획득을 기다리지 않는다. */
    fun quickFixIndex(fixes: List<LocationFix>, nowNanos: Long): Int? =
        fixIndex(fixes, nowNanos)?.takeIf { index ->
            nowNanos - fixes[index].elapsedNanos <= QUICK_FIX_AGE_NANOS &&
                fixes[index].accuracyMeters <= 100f
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
