package com.drengr.launcher.integrations.system

import android.annotation.SuppressLint
import android.content.Context

/**
 * Pulls down the notification shade, as a swipe from the status bar would.
 *
 * Android has no public API for this. Launchers call the hidden
 * `StatusBarManager.expandNotificationsPanel()`, which needs the normal (install-time)
 * EXPAND_STATUS_BAR permission. Returns false if the device blocks it.
 */
@SuppressLint("WrongConstant")
fun Context.expandNotificationShade(): Boolean = runCatching {
    val statusBar = getSystemService("statusbar") ?: return false
    statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
    true
}.getOrDefault(false)
