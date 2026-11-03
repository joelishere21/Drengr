package com.quadro.launcher.plugins

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Torch control via CameraManager.setTorchMode — needs no CAMERA permission. */
@Singleton
class FlashlightController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)

    private val cameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    val isAvailable: Boolean get() = cameraId != null

    init {
        if (cameraId != null) {
            cameraManager.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        if (id == cameraId) _isOn.value = enabled
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    fun toggle(): Boolean {
        val id = cameraId ?: return false
        cameraManager.setTorchMode(id, !_isOn.value)
        return true
    }
}
