package com.toki.weather.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.toki.weather.BuildConfig
import kotlinx.coroutines.CancellationException
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
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        // 캐시 GPS는 현재 요청보다 오래되면 정확도 수치가 작아도 우선하지 않는다.
        val cachedGps = if (hasFineLocationPermission(context)) {
            try { locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) }
            catch (_: Exception) { null }
        } else null
        val priority = if (hasFineLocationPermission(context)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val freshLocation: Location? = try {
            withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine { cont ->
                    val cts = CancellationTokenSource()
                    cont.invokeOnCancellation { cts.cancel() }
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(priority)
                        .setMaxUpdateAgeMillis(5_000L)
                        .setDurationMillis(9_000L)
                        .build()
                    fusedLocationClient.getCurrentLocation(request, cts.token)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }

        val lastLocation: Location? = if (freshLocation == null) {
            try {
                withTimeoutOrNull(2_000L) {
                    suspendCancellableCoroutine { cont ->
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
        } else null
        val candidates = listOfNotNull(freshLocation, cachedGps, lastLocation)
            .filter { it.hasAccuracy() }
        val selectedIndex = LocationSelection.fixIndex(
            candidates.map { LocationFix(it.elapsedRealtimeNanos, it.accuracy) },
            SystemClock.elapsedRealtimeNanos()
        )
        val location = selectedIndex?.let { candidates[it] }

        if (location == null) {
            Log.w(TAG, "Location is null, fallback to default")
            return getDefaultLocationInfo()
        }

        Log.d(TAG, "Acquired coordinates: lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}m, provider=${location.provider}")

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

    /** 먼 지번 주소를 현재 동으로 오인하지 않도록 거리와 주소 필드를 함께 검사한다. */
    private suspend fun getAdminAreaName(context: Context, lat: Double, lon: Double): String {
        return try {
            withTimeoutOrNull(4_000L) {
                suspendCancellableCoroutine { cont ->
                    Geocoder(context, Locale.KOREAN).getFromLocation(lat, lon, 5,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val candidates = addresses.map { address ->
                                    AddressCandidate(
                                        latitude = if (address.hasLatitude()) address.latitude else null,
                                        longitude = if (address.hasLongitude()) address.longitude else null,
                                        thoroughfare = address.thoroughfare,
                                        featureName = address.featureName,
                                        addressLine = address.getAddressLine(0),
                                        subLocality = address.subLocality,
                                        locality = address.locality
                                    )
                                }
                                val name = LocationSelection.addressName(lat, lon, candidates)
                                if (cont.isActive) cont.resume(name)
                            }
                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume("현재 위치")
                            }
                        })
                }
            } ?: "현재 위치"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
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
