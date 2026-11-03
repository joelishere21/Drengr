package com.drengr.launcher.integrations.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoTest {

    @Test fun parsesForecast() {
        val json = """
            {"current":{"time":"2026-11-02T14:30","temperature_2m":11.6,"weather_code":61,"is_day":1},
             "daily":{"time":["2026-11-02"],"temperature_2m_max":[13.4],"temperature_2m_min":[4.5],
                      "precipitation_probability_max":[70]}}
        """.trimIndent()
        val w = OpenMeteo.parseForecast(json, fahrenheit = false, place = "Oslo", now = 0)
        assertEquals(12, w.temperature)
        assertEquals(13, w.high)
        assertEquals(5, w.low)
        assertEquals(WeatherCondition.Rain, w.condition)
        assertEquals(70, w.precipitationChance)
        assertEquals("°C", w.unit)
        assertTrue(w.isDay)
    }

    @Test fun missingPrecipitationIsNull() {
        val json = """
            {"current":{"temperature_2m":-2.0,"weather_code":0,"is_day":0},
             "daily":{"temperature_2m_max":[1.0],"temperature_2m_min":[-5.0],"precipitation_probability_max":[null]}}
        """.trimIndent()
        val w = OpenMeteo.parseForecast(json, fahrenheit = true, place = null, now = 0)
        assertEquals(null, w.precipitationChance)
        assertFalse(w.isDay)
        assertEquals("°F", w.unit)
    }

    @Test fun parsesPlaces() {
        val json = """
            {"results":[
              {"name":"Bergen","latitude":60.39,"longitude":5.32,"admin1":"Vestland","country":"Norway"},
              {"name":"Bergen","latitude":52.81,"longitude":9.96,"country":"Germany"}]}
        """.trimIndent()
        val places = OpenMeteo.parsePlaces(json)
        assertEquals(listOf("Bergen, Vestland, Norway", "Bergen, Germany"), places.map { it.name })
        assertEquals(60.39, places.first().latitude, 0.0001)
    }

    @Test fun noResultsIsEmpty() = assertTrue(OpenMeteo.parsePlaces("{}").isEmpty())

    @Test fun coordinatesAreRounded() {
        val url = OpenMeteo.forecastUrl(59.913868, 10.752245, fahrenheit = true)
        assertTrue(url.contains("latitude=59.91&longitude=10.75"))
        assertTrue(url.endsWith("&temperature_unit=fahrenheit"))
    }

    @Test fun wmoCodes() {
        assertEquals(WeatherCondition.Clear, WeatherCondition.fromWmo(1))
        assertEquals(WeatherCondition.Thunderstorm, WeatherCondition.fromWmo(99))
        assertEquals(WeatherCondition.Snow, WeatherCondition.fromWmo(85))
        assertEquals(WeatherCondition.Unknown, WeatherCondition.fromWmo(42))
    }
}
