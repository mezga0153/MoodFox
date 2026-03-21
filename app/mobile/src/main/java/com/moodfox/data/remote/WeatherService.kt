package com.moodfox.data.remote

import com.moodfox.data.local.db.WeatherSnapshot
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class GeoCity(
    val name: String,
    val country: String,
    val admin1: String,
    val latitude: Double,
    val longitude: Double,
)

@Singleton
class WeatherService @Inject constructor(private val client: HttpClient) {

    // Uses Open-Meteo API — no key required.
    // Returns null silently on any error; weather is always best-effort.

    suspend fun fetchCurrent(lat: Double, lon: Double): WeatherSnapshot? = try {
        val resp: OpenMeteoResponse = client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter("current", "temperature_2m,precipitation,weathercode,relativehumidity_2m")
            parameter("timezone", "auto")
        }.body()
        val cur = resp.current
        WeatherSnapshot(
            timestamp    = Instant.now().toEpochMilli(),
            city         = "%.2f,%.2f".format(lat, lon),
            temperatureC = cur.temperature2m,
            condition    = weatherCodeToCondition(cur.weathercode),
            isRaining    = cur.precipitation > 0f,
            humidity     = cur.relativehumidity2m.toFloat(),
        )
    } catch (_: Exception) { null }

    suspend fun searchCities(query: String): List<GeoCity> = try {
        @Serializable
        data class GeoResult(
            val name: String,
            val country: String? = null,
            val admin1: String? = null,
            val latitude: Double,
            val longitude: Double,
        )
        @Serializable
        data class GeoResponse(val results: List<GeoResult> = emptyList())

        val geo: GeoResponse = client.get("https://geocoding-api.open-meteo.com/v1/search") {
            parameter("name", query)
            parameter("count", 5)
            parameter("language", "en")
            parameter("format", "json")
        }.body()
        geo.results.map { GeoCity(it.name, it.country ?: "", it.admin1 ?: "", it.latitude, it.longitude) }
    } catch (_: Exception) { emptyList() }

    suspend fun fetchByCity(city: String): WeatherSnapshot? {
        val result = searchCities(city).firstOrNull() ?: return null
        return fetchCurrent(result.latitude, result.longitude)?.copy(city = result.name)
    }

    private fun weatherCodeToCondition(code: Int): String = when (code) {
        0 -> "Clear"
        in 1..3 -> "Partly cloudy"
        in 45..48 -> "Fog"
        in 51..67 -> "Drizzle/Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: android.content.Context): android.location.Location? {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                as android.location.LocationManager
        return listOf(
            "fused",
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.GPS_PROVIDER,
        ).firstNotNullOfOrNull { provider ->
            try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null }
        }
    }

    @Serializable
    private data class OpenMeteoResponse(val current: CurrentWeather)

    @Serializable
    private data class CurrentWeather(
        val temperature_2m: Float,
        val precipitation: Float,
        val weathercode: Int,
        val relativehumidity_2m: Int,
    ) {
        val temperature2m get() = temperature_2m
        val relativehumidity2m get() = relativehumidity_2m
    }
}
