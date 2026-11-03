package com.drengr.launcher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.domain.Place
import com.drengr.launcher.integrations.calendar.CalendarEvent
import com.drengr.launcher.integrations.calendar.CalendarRepository
import com.drengr.launcher.integrations.weather.Weather
import com.drengr.launcher.integrations.weather.WeatherException
import com.drengr.launcher.integrations.weather.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface AgendaUi {
    data object Loading : AgendaUi
    data object NeedsPermission : AgendaUi
    data class Ready(val events: List<CalendarEvent>) : AgendaUi
}

sealed interface WeatherUi {
    data object Hidden : WeatherUi
    data object NeedsLocation : WeatherUi
    data object Loading : WeatherUi
    data class Ready(val weather: Weather) : WeatherUi
    data class Error(val message: String) : WeatherUi
}

private data class WeatherConfig(val enabled: Boolean, val place: Place?, val fahrenheit: Boolean)

private val WEATHER_MAX_AGE = TimeUnit.MINUTES.toMillis(30)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val weatherRepository: WeatherRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val calendarGranted = MutableStateFlow(calendar.hasPermission())

    val agenda: StateFlow<AgendaUi> = calendarGranted
        .flatMapLatest { granted ->
            if (granted) calendar.upcoming().map { AgendaUi.Ready(it) } else flowOf(AgendaUi.NeedsPermission)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUi.Loading)

    private val _weather = MutableStateFlow<WeatherUi>(WeatherUi.Loading)
    val weather: StateFlow<WeatherUi> = _weather.asStateFlow()

    private var weatherConfig: WeatherConfig? = null
    private var weatherJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings
                .map { s ->
                    WeatherConfig(
                        enabled = s.weatherEnabled,
                        place = s.weatherPlace,
                        fahrenheit = s.useFahrenheit ?: weatherRepository.systemPrefersFahrenheit(),
                    )
                }
                .distinctUntilChanged()
                .collect { config ->
                    weatherConfig = config
                    weatherJob?.cancel() // a fetch for the old place/unit is no longer wanted
                    refreshWeather()
                }
        }
    }

    fun calendarIntent(event: CalendarEvent) = calendar.viewIntent(event)

    /** Re-checks permissions (they can change in system settings) and refreshes stale data. */
    fun onResume() {
        calendarGranted.value = calendar.hasPermission()
        refreshWeather()
    }

    fun onCalendarPermissionResult() {
        calendarGranted.value = calendar.hasPermission()
    }

    /** Fetches weather unless a fresh result is cached. [force] always fetches. */
    fun refreshWeather(force: Boolean = false) {
        val config = weatherConfig ?: return
        if (!config.enabled) {
            _weather.value = WeatherUi.Hidden
            return
        }
        if (config.place == null && !weatherRepository.hasLocationPermission()) {
            _weather.value = WeatherUi.NeedsLocation
            return
        }
        val cached = weatherRepository.cached(config.place, config.fahrenheit)
        if (cached != null) {
            _weather.value = WeatherUi.Ready(cached)
            if (!force && System.currentTimeMillis() - cached.fetchedAt < WEATHER_MAX_AGE) return
        } else {
            _weather.value = WeatherUi.Loading
        }
        if (weatherJob?.isActive == true) return
        weatherJob = viewModelScope.launch {
            try {
                _weather.value = WeatherUi.Ready(weatherRepository.fetch(config.place, config.fahrenheit))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep showing older data rather than replacing it with an error.
                val message = (e as? WeatherException)?.message ?: "Weather unavailable"
                if (cached == null) _weather.value = WeatherUi.Error(message)
            }
        }
    }
}
