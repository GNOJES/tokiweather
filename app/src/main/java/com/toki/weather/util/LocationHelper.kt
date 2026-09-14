package com.toki.weather.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
     * 위치 권한 보유 여부 확인
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
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

        val location: Location? = try {
            // 1. 현재 위치 요청 (우선순위: 균형 전력 모드, 5초 타임아웃)
            val cts = CancellationTokenSource()
            suspendCancellableCoroutine { cont ->
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
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
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching location", e)
            null
        }

        if (location == null) {
            Log.w(TAG, "Location is null, fallback to default")
            return getDefaultLocationInfo()
        }

        // 위경도 -> 기상청 격자 변환
        val grid = GridConverter.toGrid(location.latitude, location.longitude)
        // 위경도 -> 행정구역명 추출 (동/구 단위)
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
     * 역지오코딩을 통해 "역삼동", "서초구" 같은 친근한 지역명 추출
     */
    private suspend fun getAdminAreaName(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.KOREAN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1) { addresses ->
                        val address = addresses.firstOrNull()
                        val resultName = when {
                            address == null -> "현재 위치"
                            !address.thoroughfare.isNullOrBlank() -> address.thoroughfare // 예: 역삼동
                            !address.subLocality.isNullOrBlank() -> address.subLocality   // 예: 강남구
                            !address.locality.isNullOrBlank() -> address.locality         // 예: 서울특별시
                            else -> "현재 위치"
                        }
                        cont.resume(resultName)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val address = addresses?.firstOrNull()
                when {
                    address == null -> "현재 위치"
                    !address.thoroughfare.isNullOrBlank() -> address.thoroughfare
                    !address.subLocality.isNullOrBlank() -> address.subLocality
                    !address.locality.isNullOrBlank() -> address.locality
                    else -> "현재 위치"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder failed", e)
            "현재 위치"
        }
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
