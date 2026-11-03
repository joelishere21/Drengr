package com.quadro.launcher.integrations.system

import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class StorageState(val totalBytes: Long = 0, val freeBytes: Long = 0) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

@Singleton
class StorageRepository @Inject constructor() {
    suspend fun read(): StorageState = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        StorageState(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 100) "%.0f GB".format(gb) else "%.1f GB".format(gb)
}
