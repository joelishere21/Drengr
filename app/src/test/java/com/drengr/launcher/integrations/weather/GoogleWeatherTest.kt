package com.drengr.launcher.integrations.weather

import com.drengr.launcher.core.domain.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleWeatherTest {
    private val city = Place("Springfield, Illinois, United States", 39.8, -89.6)

    @Test fun configuredCityWins() =
        assertEquals("weather Springfield, Illinois, United States", GoogleWeather.query(city, "Elsewhere"))

    @Test fun deviceLocationUsesShownName() =
        assertEquals("weather Brooklyn", GoogleWeather.query(null, "Brooklyn"))

    @Test fun noNameLetsGoogleUseLocation() {
        assertEquals("weather", GoogleWeather.query(null, null))
        assertEquals("weather", GoogleWeather.query(null, " "))
    }
}
