package com.toki.weather.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.toki.weather.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class LocationInfo(
    val nx: Int,
    val ny: Int,
    val locationName: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

object LocationHelper {

    private const val TAG = "LocationHelper"

    /**
     * 위치 권한 보유 여부 확인 (정밀 또는 대략)
     */
    fun hasLocationPermission(context: Context): Boolean {
        return hasFineLocationPermission(context) || hasCoarseLocationPermission(context)
    }

    /**
     * 정밀 위치(GPS) 권한 확인
     */
    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 대략적 위치 권한 확인
     */
    fun hasCoarseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * GPS 현재 위치를 조회하여 기상청 격자 좌표 및 지역 이름 반환
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationInfo(context: Context): LocationInfo {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted. Using default coordinates.")
            return getDefaultLocationInfo()
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        // 고정밀 위치(GPS) 권한이 있는 경우 PRIORITY_HIGH_ACCURACY로 위성 GNSS 신호 수신
        // (영등포동7가 vs 영등포동8가 등 인접 법정동 간 50m 오차 방지)
        val priority = if (hasFineLocationPermission(context)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val location: Location? = try {
            val cts = CancellationTokenSource()
            withTimeoutOrNull(7000L) {
                suspendCancellableCoroutine { cont ->
                    fusedLocationClient.getCurrentLocation(
                        priority,
                        cts.token
                    ).addOnSuccessListener { loc ->
                        if (loc != null) {
                            cont.resume(loc)
                        } else {
                            // getCurrentLocation 실패 시 lastLocation 시도
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { lastLoc -> cont.resume(lastLoc) }
                                .addOnFailureListener { cont.resume(null) }
                        }
                    }.addOnFailureListener {
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { lastLoc -> cont.resume(lastLoc) }
                            .addOnFailureListener { cont.resume(null) }
                    }

                    cont.invokeOnCancellation {
                        cts.cancel()
                    }
                }
            } ?: run {
                Log.w(TAG, "getCurrentLocation timed out after 7s, falling back to lastLocation")
                suspendCancellableCoroutine { cont ->
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLoc -> cont.resume(lastLoc) }
                        .addOnFailureListener { cont.resume(null) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching location", e)
            null
        }

        if (location == null) {
            Log.w(TAG, "Location is null, fallback to default")
            return getDefaultLocationInfo()
        }

        Log.d(TAG, "Acquired coordinates: lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}m")

        // 위경도 -> 기상청 격자 변환
        val grid = GridConverter.toGrid(location.latitude, location.longitude)
        // 위경도 -> 행정구역명 추출 (동/가/읍/면 단위)
        val name = getAdminAreaName(context, location.latitude, location.longitude)

        return LocationInfo(
            nx = grid.nx,
            ny = grid.ny,
            locationName = name,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    /**
     * 역지오코딩을 통해 "영등포동7가", "역삼동" 등 가장 정확한 동/가/읍/면 단위 지역명 추출
     */
    private suspend fun getAdminAreaName(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.KOREAN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1) { addresses ->
                        val address = addresses.firstOrNull()
                        val resultName = if (address != null) {
                            extractDongName(address)
                        } else {
                            "현재 위치"
                        }
                        cont.resume(resultName)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val address = addresses?.firstOrNull()
                if (address != null) {
                    extractDongName(address)
                } else {
                    "현재 위치"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder failed", e)
            "현재 위치"
        }
    }

    /**
     * Geocoder 주소 객체에서 도로명이 아닌 법정동/행정동('영등포동7가', '역삼동', '을지로3가' 등)을 우선 추출
     */
    private fun extractDongName(address: Address): String {
        // 1. thoroughfare가 명확한 동/가/읍/면인 경우
        val thoroughfare = address.thoroughfare
        if (!thoroughfare.isNullOrBlank() && isDongName(thoroughfare)) {
            return thoroughfare
        }

        // 2. featureName이 동/가/읍/면인 경우
        val featureName = address.featureName
        if (!featureName.isNullOrBlank() && isDongName(featureName)) {
            return featureName
        }

        // 3. 전체 주소 문자열에서 동/가/읍/면 단위 토큰 검색 (세부 단위일수록 뒤쪽에 위치)
        val fullAddress = address.getAddressLine(0) ?: ""
        if (fullAddress.isNotBlank()) {
            val tokens = fullAddress.split(Regex("[\\s,()]+"))
            for (token in tokens.reversed()) {
                val clean = token.trim()
                if (isDongName(clean)) {
                    return clean
                }
            }
        }

        // 4. 동/가/읍/면을 찾지 못한 경우 thoroughfare (도로명 포함) 사용
        if (!thoroughfare.isNullOrBlank()) {
            return thoroughfare
        }

        // 5. 구/군(subLocality) 사용 (예: 영등포구, 강남구)
        if (!address.subLocality.isNullOrBlank()) {
            return address.subLocality
        }

        // 6. 시(locality) 사용 (예: 서울특별시)
        if (!address.locality.isNullOrBlank()) {
            return address.locality
        }

        return "현재 위치"
    }

    private fun isDongName(token: String): Boolean {
        if (token.isBlank()) return false
        // 시, 군, 구, 도, 로, 길 로 끝나는 것은 동 이름이 아님
        if (token.endsWith("구") || token.endsWith("시") || token.endsWith("군") ||
            token.endsWith("도") || token.endsWith("로") || token.endsWith("길")) {
            return false
        }
        // 동, 읍, 면, 또는 [숫자]가 (예: 영등포동, 역삼1동, 영등포동7가, 종로3가, 읍, 면)
        return token.matches(Regex("^[가-힣0-9]+(?:동[0-9]*가?|[0-9]+가|동|읍|면)$"))
    }

    private fun getDefaultLocationInfo(): LocationInfo {
        return LocationInfo(
            nx = BuildConfig.DEFAULT_NX,
            ny = BuildConfig.DEFAULT_NY,
            locationName = "설정 위치",
            latitude = 37.5665,
            longitude = 126.9780
        )
    }
}
