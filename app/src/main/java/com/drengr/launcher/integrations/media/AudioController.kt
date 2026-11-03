package com.drengr.launcher.integrations.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class AudioState(
    val volume: Int = 0,
    val maxVolume: Int = 1,
    val isMusicActive: Boolean = false,
    val ringerMode: String = "Normal",
    val outputLabel: String = "Phone speaker",
    val outputKind: OutputKind = OutputKind.Speaker,
) {
    val volumeFraction: Float get() = volume.toFloat() / maxVolume.coerceAtLeast(1)
}

enum class OutputKind { Speaker, Wired, Bluetooth, Usb, Other }

/**
 * Media controls that work without notification access: media key dispatch goes to the
 * active media session, and volume/output info comes from [AudioManager].
 * Track metadata/artwork (MediaSessionManager) needs a notification listener — Milestone 5.
 */
@Singleton
class AudioController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun state(): AudioState {
        val (label, kind) = currentOutput()
        return AudioState(
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            isMusicActive = audio.isMusicActive,
            ringerMode = when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "Silent"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                else -> "Normal"
            },
            outputLabel = label,
            outputKind = kind,
        )
    }

    fun playPause() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun adjustVolume(raise: Boolean) {
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    private fun sendMediaKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun currentOutput(): Pair<String, OutputKind> {
        val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        fun find(vararg types: Int) = devices.firstOrNull { it.type in types }
        find(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
            ?.let { return (it.productName?.toString()?.ifBlank { null } ?: "Bluetooth audio") to OutputKind.Bluetooth }
        find(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)
            ?.let { return "Wired headphones" to OutputKind.Wired }
        find(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
            ?.let { return (it.productName?.toString()?.ifBlank { null } ?: "USB audio") to OutputKind.Usb }
        return "Phone speaker" to OutputKind.Speaker
    }
}
