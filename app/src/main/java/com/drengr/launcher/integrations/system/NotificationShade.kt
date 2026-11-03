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
fun Context.expandNotificationShade(): Boolean = callStatusBar("expandNotificationsPanel")

/** Opens the full Quick Settings panel, the same way. Returns false if the device blocks it. */
fun Context.expandQuickSettings(): Boolean = callStatusBar("expandSettingsPanel")

@SuppressLint("WrongConstant")
private fun Context.callStatusBar(method: String): Boolean = runCatching {
    val statusBar = getSystemService("statusbar") ?: return false
    statusBar.javaClass.getMethod(method).invoke(statusBar)
    true
}.getOrDefault(false)
