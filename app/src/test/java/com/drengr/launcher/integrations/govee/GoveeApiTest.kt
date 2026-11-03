package com.drengr.launcher.integrations.govee

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoveeApiTest {
    private val devicesJson = """
        {"code":200,"message":"success","data":[
          {"sku":"H6008","device":"AA:BB:CC:DD:EE:FF:00:11","deviceName":"Desk lamp","type":"devices.types.light",
           "capabilities":[
             {"type":"devices.capabilities.on_off","instance":"powerSwitch","parameters":{"dataType":"ENUM"}},
             {"type":"devices.capabilities.range","instance":"brightness","parameters":{"dataType":"INTEGER","range":{"min":1,"max":100,"precision":1}}},
             {"type":"devices.capabilities.color_setting","instance":"colorRgb","parameters":{"dataType":"INTEGER"}},
             {"type":"devices.capabilities.color_setting","instance":"colorTemperatureK","parameters":{"dataType":"INTEGER","range":{"min":2700,"max":6500,"precision":1}}}
           ]},
          {"sku":"H5080","device":"11:22","deviceName":"","type":"devices.types.socket",
           "capabilities":[{"type":"devices.capabilities.on_off","instance":"powerSwitch"}]}
        ]}
    """.trimIndent()

    @Test fun parsesDevicesAndCapabilities() {
        val (lamp, plug) = GoveeApi.parseDevices(devicesJson)
        assertEquals("Desk lamp", lamp.name)
        assertEquals(GoveeDeviceType.Light, lamp.type)
        assertTrue(lamp.canPower && lamp.canColor)
        assertEquals(1..100, lamp.brightness)
        assertEquals(2700..6500, lamp.colorTemperature)

        assertEquals("H5080", plug.name) // blank name falls back to the model
        assertEquals(GoveeDeviceType.Plug, plug.type)
        assertNull(plug.brightness)
        assertFalse(plug.canColor)
    }

    @Test fun parsesState() {
        val state = GoveeApi.parseState(
            """
            {"requestId":"1","msg":"success","code":200,"payload":{"sku":"H6008","device":"AA","capabilities":[
              {"type":"devices.capabilities.online","instance":"online","state":{"value":true}},
              {"type":"devices.capabilities.on_off","instance":"powerSwitch","state":{"value":1}},
              {"type":"devices.capabilities.range","instance":"brightness","state":{"value":64}},
              {"type":"devices.capabilities.color_setting","instance":"colorRgb","state":{"value":16711680}},
              {"type":"devices.capabilities.color_setting","instance":"colorTemperatureK","state":{"value":0}}
            ]}}
            """.trimIndent(),
        )
        assertEquals(GoveeState(online = true, on = true, brightness = 64, color = 0xFF0000, colorTemperatureK = null), state)
    }

    @Test fun buildsControlBody() {
        val lamp = GoveeApi.parseDevices(devicesJson).first()
        val body = JSONObject(GoveeApi.controlBody(lamp, GoveeCommand.Power(false), "req-1"))
        assertEquals("req-1", body.getString("requestId"))
        val payload = body.getJSONObject("payload")
        assertEquals("H6008", payload.getString("sku"))
        assertEquals("AA:BB:CC:DD:EE:FF:00:11", payload.getString("device"))
        val capability = payload.getJSONObject("capability")
        assertEquals("devices.capabilities.on_off", capability.getString("type"))
        assertEquals("powerSwitch", capability.getString("instance"))
        assertEquals(0, capability.getInt("value"))

        val color = JSONObject(GoveeApi.controlBody(lamp, GoveeCommand.Color(0x7F579BFF), "r"))
            .getJSONObject("payload").getJSONObject("capability")
        assertEquals(0x579BFF, color.getInt("value")) // alpha stripped
    }

    @Test fun errorCodesBecomeReadableMessages() {
        val e = assertThrows(GoveeException::class.java) {
            GoveeApi.parseDevices("""{"code":401,"message":"Invalid API Key"}""")
        }
        assertTrue(e.message!!.contains("API key"))
        assertTrue(GoveeApi.errorFor(429, null)!!.contains("limit"))
        assertNull(GoveeApi.errorFor(200, "success"))
    }
}
