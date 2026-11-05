package com.toki.weather.ui.screen

import java.net.URI

/** A page-scoped request; Android permission is checked again for every new request. */
internal class RadarLocationPermission {
    private var pending: ((Boolean) -> Unit)? = null

    /** Returns true only when the trusted page needs an Android permission decision. */
    fun request(origin: String, pageUrl: String?, hasPermission: Boolean, respond: (Boolean) -> Unit): Boolean {
        cancel()
        if (!isWeatherPage(origin) || !isWeatherPage(pageUrl)) {
            respond(false)
            return false
        }
        if (hasPermission) {
            respond(true)
            return false
        }
        pending = respond
        return true
    }

    fun complete(granted: Boolean): Boolean {
        val response = pending ?: return false
        pending = null
        response(granted)
        return true
    }

    fun cancel() { complete(false) }

    private fun isWeatherPage(value: String?): Boolean = runCatching {
        val uri = URI(value ?: return false)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("weather.naver.com", ignoreCase = true) &&
            uri.port in listOf(-1, 443) && uri.rawUserInfo == null
    }.getOrDefault(false)
}
