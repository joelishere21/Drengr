package com.drengr.launcher.integrations.govee

import com.drengr.launcher.core.datastore.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Govee lights and plugs through Govee's cloud API. Needs the user's personal API key
 * (requested in the Govee Home app); nothing is sent to Govee until one is saved.
 */
@Singleton
class GoveeRepository @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var cachedDevices: List<GoveeDevice>? = null

    val hasKey: Flow<Boolean> = settings.goveeApiKey.map { !it.isNullOrBlank() }.distinctUntilChanged()

    /** Synchronous view of [hasKey], for Command Center action availability. */
    @Volatile var configured: Boolean = false
        private set

    init {
        scope.launch { hasKey.collect { configured = it } }
    }

    /** Checks [key] against Govee before saving it, so a typo is caught right away. */
    suspend fun saveKey(key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) throw GoveeException("Paste your Govee API key first.")
        val devices = withContext(Dispatchers.IO) { GoveeApi.parseDevices(request(GoveeApi.DEVICES, clean)) }
        settings.setGoveeApiKey(clean)
        cachedDevices = devices
    }

    suspend fun removeKey() {
        settings.setGoveeApiKey(null)
        cachedDevices = null
    }

    /** The account's devices. Cached for the session; [refresh] asks Govee again. */
    suspend fun devices(refresh: Boolean = false): List<GoveeDevice> {
        if (!refresh) cachedDevices?.let { return it }
        return withContext(Dispatchers.IO) {
            GoveeApi.parseDevices(request(GoveeApi.DEVICES, key())).also { cachedDevices = it }
        }
    }

    suspend fun state(device: GoveeDevice): GoveeState = withContext(Dispatchers.IO) {
        GoveeApi.parseState(request(GoveeApi.STATE, key(), GoveeApi.stateBody(device, requestId())))
    }

    suspend fun control(device: GoveeDevice, command: GoveeCommand) = withContext(Dispatchers.IO) {
        GoveeApi.checkControl(request(GoveeApi.CONTROL, key(), GoveeApi.controlBody(device, command, requestId())))
    }

    /** Turns every switchable device off. Runs in the background; [onDone] gets a summary. */
    fun allOff(onDone: (String) -> Unit = {}) {
        scope.launch {
            val message = try {
                val targets = devices().filter { it.canPower }
                val failed = targets.map { device ->
                    async { runCatching { control(device, GoveeCommand.Power(false)) }.isFailure }
                }.awaitAll().count { it }
                when {
                    targets.isEmpty() -> "No Govee devices to turn off"
                    failed == 0 -> "Turned off ${targets.size} Govee device" + if (targets.size == 1) "" else "s"
                    else -> "$failed of ${targets.size} Govee devices didn't respond"
                }
            } catch (e: GoveeException) {
                e.message ?: "Govee is unavailable"
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    private suspend fun key(): String =
        settings.goveeApiKey.first()?.takeIf { it.isNotBlank() }
            ?: throw GoveeException("Add your Govee API key in Settings → Smart home.")

    private fun requestId() = UUID.randomUUID().toString()

    /** GET without [body], POST with it. Returns the body of a 200 response. */
    private fun request(url: String, key: String, body: String? = null): String {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Govee-API-Key", key)
                setRequestProperty("User-Agent", "Drengr-Launcher")
                if (body != null) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
        } catch (e: IOException) {
            throw GoveeException("Can't reach Govee. Check your connection.")
        }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            }
            val detail = runCatching {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.let { org.json.JSONObject(it) }
                    ?.let { it.optString("message").ifBlank { it.optString("msg") } }
            }.getOrNull()
            throw GoveeException(GoveeApi.errorFor(code, detail) ?: "Govee error $code")
        } catch (e: IOException) {
            throw GoveeException("Can't reach Govee. Check your connection.")
        } finally {
            connection.disconnect()
        }
    }
}
