package com.toki.weather.data.repository

import com.toki.weather.data.remote.AirQualityApiService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant

class AirQualityRepositoryTest {
    private val now = Instant.parse("2026-09-23T06:30:00Z").toEpochMilli()
    private val stations = """[{"stationName":"먼 곳","dmX":"35.1","dmY":"129.0"},{"stationName":"가까운 곳","dmX":"37.57","dmY":"126.98"}]"""
    private val reading = """[{"dataTime":"2026-09-23 15:00","pm10Value":"32","pm25Value":"12","pm10Flag":null,"pm25Flag":null}]"""

    private fun repository(stationItems: String = stations, measurements: String = reading, code: String = "00"): AirQualityRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("test+/=", request.url.queryParameter("serviceKey"))
            assertEquals("json", request.url.queryParameter("returnType"))
            val isStations = request.url.encodedPath.endsWith("getMsrstnList")
            if (!isStations) {
                assertEquals("가까운 곳", request.url.queryParameter("stationName"))
                assertEquals("1.3", request.url.queryParameter("ver"))
            }
            val items = if (isStations) stationItems else measurements
            val body = """{"response":{"header":{"resultCode":"$code"},"body":{"totalCount":2,"items":$items}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/B552584/")
            .client(client).addConverterFactory(GsonConverterFactory.create()).build()
            .create(AirQualityApiService::class.java)
        return AirQualityRepository(api, "test+/=", { now })
    }

    @Test fun nearestStationAndDecodedKeyProduceOfficialReadings() = runBlocking {
        assertEquals(32 to 12, repository().fetch(37.5665, 126.9780))
    }

    @Test fun missingOrFlaggedMeasurementsAreNotZero() = runBlocking {
        val measurements = """[{"dataTime":"2026-09-23 15:00","pm10Value":"-","pm25Value":"12","pm25Flag":"점검및교정"}]"""
        assertEquals(-1 to -1, repository(measurements = measurements).fetch(37.5665, 126.9780))
    }

    @Test fun validZeroIsPreservedAndEachPollutantIsIndependent() = runBlocking {
        val measurements = """[{"dataTime":"2026-09-23 15:00","pm10Value":"0","pm25Value":"-"}]"""
        assertEquals(0 to -1, repository(measurements = measurements).fetch(37.5665, 126.9780))
    }

    @Test fun staleMeasurementIsNotPresentedAsCurrent() = runBlocking {
        assertEquals(-1 to -1, repository(measurements = reading.replace("15:00", "10:00")).fetch(37.5665, 126.9780))
    }

    @Test fun invalidStationCoordinatesAreSkipped() = runBlocking {
        val items = """[{"stationName":"잘못된 곳","dmX":"NaN","dmY":"126.98"},{"stationName":"가까운 곳","dmX":"37.57","dmY":"126.98"}]"""
        assertEquals(32 to 12, repository(stationItems = items).fetch(37.5665, 126.9780))
    }

    @Test fun noNearbyStationReturnsMissing() = runBlocking {
        assertEquals(-1 to -1, repository().fetch(0.0, 0.0))
    }

    @Test fun apiErrorDoesNotUseItsPayload() = runBlocking {
        assertEquals(-1 to -1, repository(code = "30").fetch(37.5665, 126.9780))
    }
    @Test fun stationPaginationFindsNearestAndReusesDirectory() = runBlocking {
        var stationRequests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val items = if (request.url.encodedPath.endsWith("getMsrstnList")) {
                stationRequests++
                when (request.url.queryParameter("pageNo")) {
                    "1" -> """[{"stationName":"먼 곳","dmX":"35.1","dmY":"129.0"}]"""
                    "2" -> """[{"stationName":"가까운 곳","dmX":"37.57","dmY":"126.98"}]"""
                    else -> error("Unexpected page")
                }
            } else {
                assertEquals("가까운 곳", request.url.queryParameter("stationName"))
                reading
            }
            val body = """{"response":{"header":{"resultCode":"00"},"body":{"totalCount":2,"items":$items}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/B552584/")
            .client(client).addConverterFactory(GsonConverterFactory.create()).build()
            .create(AirQualityApiService::class.java)
        val repository = AirQualityRepository(api, "test", { now })
        assertEquals(32 to 12, repository.fetch(37.5665, 126.9780))
        assertEquals(32 to 12, repository.fetch(37.57, 126.98))
        assertEquals(2, stationRequests)
    }

    @Test fun networkFailureReturnsMissingInsteadOfBreakingWeatherUpdate() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline") }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/B552584/")
            .client(client).addConverterFactory(GsonConverterFactory.create()).build()
            .create(AirQualityApiService::class.java)
        assertEquals(-1 to -1, AirQualityRepository(api, "test", { now }).fetch(37.57, 126.98))
    }

    @Test fun futureMeasurementIsRejected() = runBlocking {
        assertEquals(-1 to -1, repository(measurements = reading.replace("15:00", "16:00")).fetch(37.57, 126.98))
    }

    @Test fun transientStationNetworkFailureRetriesAndReturnsReading() = runBlocking {
        var stationAttempts = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val isStations = request.url.encodedPath.endsWith("getMsrstnList")
            if (isStations && ++stationAttempts == 1) throw java.io.IOException("temporary timeout")
            val items = if (isStations) stations else reading
            val body = """{"response":{"header":{"resultCode":"00"},"body":{"totalCount":2,"items":$items}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/B552584/")
            .client(client).addConverterFactory(GsonConverterFactory.create()).build()
            .create(AirQualityApiService::class.java)

        assertEquals(32 to 12, AirQualityRepository(api, "test", { now }).fetch(37.5665, 126.9780))
        assertEquals(2, stationAttempts)
    }

}
