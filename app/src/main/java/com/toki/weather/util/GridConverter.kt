package com.toki.weather.util

import kotlin.math.*

/**
 * 위경도 ↔ 기상청 격자 좌표 변환 유틸리티
 * 기상청 격자 좌표계 (Lambert Conformal Conic Projection)
 * 참고: 기상청 제공 변환 공식 기반
 */
object GridConverter {

    private const val RE = 6371.00877     // 지구 반경(km)
    private const val GRID = 5.0          // 격자 간격(km)
    private const val SLAT1 = 30.0        // 투영 위도1(degree)
    private const val SLAT2 = 60.0        // 투영 위도2(degree)
    private const val OLON = 126.0        // 기준점 경도(degree)
    private const val OLAT = 38.0         // 기준점 위도(degree)
    private const val XO = 43.0           // 기준점 X좌표(GRID)
    private const val YO = 136.0          // 기준점 Y좌표(GRID)

    data class GridXY(val nx: Int, val ny: Int)
    data class LatLon(val lat: Double, val lon: Double)

    /**
     * 위경도 → 격자 좌표 변환
     */
    fun toGrid(lat: Double, lon: Double): GridXY {
        val degRad = PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * degRad
        val slat2 = SLAT2 * degRad
        val olon = OLON * degRad
        val olat = OLAT * degRad

        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        var ra = tan(PI * 0.25 + lat * degRad * 0.5)
        ra = re * sf / ra.pow(sn)
        var theta = lon * degRad - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val x = (ra * sin(theta) + XO + 0.5).toInt()
        val y = (ro - ra * cos(theta) + YO + 0.5).toInt()
        return GridXY(x, y)
    }
}
