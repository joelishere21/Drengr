package com.quadro.launcher.integrations.weather

import androidx.compose.runtime.Immutable
import com.quadro.launcher.core.domain.Place
import org.json.JSONObject
import kotlin.math.roundToInt

enum class WeatherCondition(val label: String) {
    Clear("Clear"),
    PartlyCloudy("Partly cloudy"),
    Cloudy("Cloudy"),
    Fog("Fog"),
    Drizzle("Drizzle"),
    Rain("Rain"),
    FreezingRain("Freezing rain"),
    Snow("Snow"),
    Thunderstorm("Thunderstorm"),
    Unknown("—"),
    ;

    companion object {
        /** Maps WMO weather interpretation codes, as returned by Open-Meteo. */
        fun fromWmo(code: Int): WeatherCondition = when (code) {
            0, 1 -> Clear
            2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            51, 53, 55 -> Drizzle
            56, 57, 66, 67 -> FreezingRain
            61, 63, 65, 80, 81, 82 -> Rain
            71, 73, 75, 77, 85, 86 -> Snow
            95, 96, 99 -> Thunderstorm
            else -> Unknown
        }
    }
}

@Immutable
data class Weather(
    val temperature: Int,
    val high: Int,
    val low: Int,
    val condition: WeatherCondition,
    val isDay: Boolean,
    /** Today's highest chance of precipitation, 0–100, if known. */
    val precipitationChance: Int?,
    val fahrenheit: Boolean,
    val place: String?,
    val fetchedAt: Long,
) {
    val unit: String get() = if (fahrenheit) "°F" else "°C"
}

internal object OpenMeteo {
    fun forecastUrl(latitude: Double, longitude: Double, fahrenheit: Boolean): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${latitude.round2()}&longitude=${longitude.round2()}" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=1" +
            if (fahrenheit) "&temperature_unit=fahrenheit" else ""

    fun geocodeUrl(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&name=" +
            java.net.URLEncoder.encode(query.trim(), "UTF-8")

    fun parseForecast(json: String, fahrenheit: Boolean, place: String?, now: Long): Weather {
        val o = JSONObject(json)
        val current = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val chance = daily.optJSONArray("precipitation_probability_max")
            ?.takeIf { it.length() > 0 && !it.isNull(0) }?.getInt(0)
        return Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            condition = WeatherCondition.fromWmo(current.getInt("weather_code")),
            isDay = current.optInt("is_day", 1) == 1,
            precipitationChance = chance,
            fahrenheit = fahrenheit,
            place = place,
            fetchedAt = now,
        )
    }

    fun parsePlaces(json: String): List<Place> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val name = listOf(r.optString("name"), r.optString("admin1"), r.optString("country"))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
            Place(name, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    /** ~1 km precision is plenty for a forecast and avoids sending an exact position. */
    private fun Double.round2(): String = "%.2f".format(java.util.Locale.US, this)
}
