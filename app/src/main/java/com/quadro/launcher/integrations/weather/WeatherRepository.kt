package com.quadro.launcher.integrations.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.text.util.LocalePreferences
import com.quadro.launcher.core.domain.Place
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

class WeatherException(message: String) : Exception(message)

/**
 * Current conditions from Open-Meteo (free, no account or API key). The location is either a
 * place the user picked or the device's approximate location (ACCESS_COARSE_LOCATION, asked for
 * only when the user sets up weather). Coordinates are rounded to ~1 km before being sent.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var cache: Pair<String, Weather>? = null

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Follows the Android 14+ regional preference, otherwise the locale's convention. */
    fun systemPrefersFahrenheit(): Boolean =
        LocalePreferences.getTemperatureUnit() == LocalePreferences.TemperatureUnit.FAHRENHEIT

    /** Cached weather for this place/unit if it is younger than [maxAgeMs]. */
    fun cached(place: Place?, fahrenheit: Boolean, maxAgeMs: Long = Long.MAX_VALUE): Weather? {
        val (key, weather) = cache ?: return null
        val fresh = System.currentTimeMillis() - weather.fetchedAt < maxAgeMs
        return weather.takeIf { key == cacheKey(place, fahrenheit) && fresh }
    }

    /** Fetches weather for [place], or for the device location when [place] is null. */
    suspend fun fetch(place: Place?, fahrenheit: Boolean): Weather = withContext(Dispatchers.IO) {
        val target = place ?: run {
            val location = currentLocation()
                ?: throw WeatherException("Location unavailable. Turn on Location or pick a city in Settings.")
            Place(placeName(location) ?: "", location.latitude, location.longitude)
        }
        val json = get(OpenMeteo.forecastUrl(target.latitude, target.longitude, fahrenheit))
        val name = place?.name?.substringBefore(',') ?: target.name.ifBlank { null }
        OpenMeteo.parseForecast(json, fahrenheit, name, System.currentTimeMillis())
            .also { cache = cacheKey(place, fahrenheit) to it }
    }

    suspend fun searchPlaces(query: String): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else OpenMeteo.parsePlaces(get(OpenMeteo.geocodeUrl(query)))
    }

    private fun cacheKey(place: Place?, fahrenheit: Boolean) = "${place?.name ?: "@device"}|$fahrenheit"

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Drengr-Launcher")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw WeatherException("Weather service error (${connection.responseCode})")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw WeatherException("No connection to the weather service")
        } finally {
            connection.disconnect()
        }
    }

    // ---- Location -----------------------------------------------------------------------------

    @SuppressLint("MissingPermission") // Checked by hasLocationPermission().
    private suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) throw WeatherException("Location access is off")
        val manager = context.getSystemService(LocationManager::class.java)
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            throw WeatherException("Location is turned off. Turn it on or pick a city in Settings.")
        }
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.allProviders.contains(it) }.getOrDefault(false) }

        // A fix from the last 30 minutes is good enough for weather and costs no battery.
        val recent = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (recent != null && System.currentTimeMillis() - recent.time < TimeUnit.MINUTES.toMillis(30)) {
            return recent
        }

        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER && manager.isProviderEnabled(it) }
        val fresh = provider?.let { p ->
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        manager, p, signal, ContextCompat.getMainExecutor(context),
                    ) { location -> if (cont.isActive) cont.resume(location) }
                }
            }
        }
        return fresh ?: recent
    }

    private suspend fun placeName(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        location.latitude, location.longitude, 1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
        }
        return address?.run { locality ?: subAdminArea ?: adminArea }
    }
}
