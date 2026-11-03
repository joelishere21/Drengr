package com.drengr.launcher.integrations.govee

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

enum class GoveeDeviceType { Light, Plug, Other }

/** A device on the user's Govee account and what it can do. */
@Immutable
data class GoveeDevice(
    val sku: String,
    val id: String,
    val name: String,
    val type: GoveeDeviceType,
    val canPower: Boolean,
    val brightness: IntRange?,
    val canColor: Boolean,
    val colorTemperature: IntRange?,
)

/** Last known state. null fields are unknown (not reported, or not fetched yet). */
@Immutable
data class GoveeState(
    val online: Boolean? = null,
    val on: Boolean? = null,
    val brightness: Int? = null,
    val color: Int? = null,
    val colorTemperatureK: Int? = null,
)

/** One command for [GoveeApi.controlBody]. */
sealed interface GoveeCommand {
    data class Power(val on: Boolean) : GoveeCommand
    data class Brightness(val percent: Int) : GoveeCommand
    data class Color(val rgb: Int) : GoveeCommand
    data class ColorTemperature(val kelvin: Int) : GoveeCommand
}

class GoveeException(message: String) : Exception(message)

/** Govee Developer Platform (OpenAPI v1): request bodies and response parsing. */
internal object GoveeApi {
    const val BASE = "https://openapi.api.govee.com/router/api/v1"
    const val DEVICES = "$BASE/user/devices"
    const val STATE = "$BASE/device/state"
    const val CONTROL = "$BASE/device/control"

    private const val POWER = "devices.capabilities.on_off"
    private const val RANGE = "devices.capabilities.range"
    private const val COLOR = "devices.capabilities.color_setting"
    private const val ONLINE = "devices.capabilities.online"

    fun parseDevices(json: String): List<GoveeDevice> {
        val root = JSONObject(json)
        checkCode(root.optInt("code", 200), root.optString("message"))
        val data = root.optJSONArray("data") ?: return emptyList()
        return data.objects().map { d ->
            val caps = d.optJSONArray("capabilities")?.objects().orEmpty()
            fun cap(type: String, instance: String) =
                caps.firstOrNull { it.optString("type") == type && it.optString("instance") == instance }
            fun range(c: JSONObject?): IntRange? {
                val r = c?.optJSONObject("parameters")?.optJSONObject("range") ?: return null
                return r.optInt("min", 0)..r.optInt("max", 100)
            }
            GoveeDevice(
                sku = d.getString("sku"),
                id = d.getString("device"),
                name = d.optString("deviceName").trim().ifEmpty { d.getString("sku") },
                type = when (d.optString("type")) {
                    "devices.types.light" -> GoveeDeviceType.Light
                    "devices.types.socket" -> GoveeDeviceType.Plug
                    else -> GoveeDeviceType.Other
                },
                canPower = cap(POWER, "powerSwitch") != null,
                brightness = cap(RANGE, "brightness")?.let { range(it) ?: 1..100 },
                canColor = cap(COLOR, "colorRgb") != null,
                colorTemperature = range(cap(COLOR, "colorTemperatureK")),
            )
        }
    }

    fun parseState(json: String): GoveeState {
        val root = JSONObject(json)
        checkCode(root.optInt("code", 200), root.optString("msg", root.optString("message")))
        val caps = root.optJSONObject("payload")?.optJSONArray("capabilities")?.objects().orEmpty()
        fun value(type: String, instance: String): Any? = caps
            .firstOrNull { it.optString("type") == type && it.optString("instance") == instance }
            ?.optJSONObject("state")?.opt("value")
            ?.takeIf { it != JSONObject.NULL && it != "" }
        fun int(v: Any?): Int? = (v as? Number)?.toInt() ?: (v as? String)?.toIntOrNull()
        return GoveeState(
            online = value(ONLINE, "online")?.let { it == true || it == "true" },
            on = int(value(POWER, "powerSwitch"))?.let { it == 1 },
            brightness = int(value(RANGE, "brightness")),
            color = int(value(COLOR, "colorRgb")),
            colorTemperatureK = int(value(COLOR, "colorTemperatureK"))?.takeIf { it > 0 },
        )
    }

    /** Checks the envelope of a control response; throws on failure. */
    fun checkControl(json: String) {
        val root = JSONObject(json)
        checkCode(root.optInt("code", 200), root.optString("msg", root.optString("message")))
    }

    fun stateBody(device: GoveeDevice, requestId: String): String = JSONObject()
        .put("requestId", requestId)
        .put("payload", JSONObject().put("sku", device.sku).put("device", device.id))
        .toString()

    fun controlBody(device: GoveeDevice, command: GoveeCommand, requestId: String): String {
        val (type, instance, value) = when (command) {
            is GoveeCommand.Power -> Triple(POWER, "powerSwitch", if (command.on) 1 else 0)
            is GoveeCommand.Brightness -> Triple(RANGE, "brightness", command.percent)
            is GoveeCommand.Color -> Triple(COLOR, "colorRgb", command.rgb and 0xFFFFFF)
            is GoveeCommand.ColorTemperature -> Triple(COLOR, "colorTemperatureK", command.kelvin)
        }
        return JSONObject()
            .put("requestId", requestId)
            .put(
                "payload",
                JSONObject()
                    .put("sku", device.sku)
                    .put("device", device.id)
                    .put("capability", JSONObject().put("type", type).put("instance", instance).put("value", value)),
            )
            .toString()
    }

    /** Maps Govee's error codes (which also arrive in the body) to messages a person can act on. */
    fun errorFor(code: Int, message: String?): String? = when (code) {
        200 -> null
        401, 403 -> "Govee didn't accept that API key. Check it and try again."
        429 -> "Govee's request limit was reached. Try again in a little while."
        400 -> "Govee rejected the request" + (message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
        else -> "Govee error $code" + (message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
    }

    private fun checkCode(code: Int, message: String?) {
        errorFor(code, message)?.let { throw GoveeException(it) }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
}
