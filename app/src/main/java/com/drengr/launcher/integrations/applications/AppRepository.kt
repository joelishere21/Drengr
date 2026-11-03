package com.drengr.launcher.integrations.applications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.drengr.launcher.core.domain.AppCategory
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.AppShortcut
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers launchable apps across user profiles (including work profiles) through
 * [LauncherApps], keeps the list fresh on package changes, and launches apps/shortcuts.
 */
@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val activityInfos = ConcurrentHashMap<String, LauncherActivityInfo>()
    private val iconCache = LruCache<String, ImageBitmap>(400)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var reloadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            scheduleReload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            scheduleReload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scheduleReload(debounceMs = 0)
    }

    /** Coalesces bursts of package events (e.g. app updates) into one reload. */
    private fun scheduleReload(debounceMs: Long = 400) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(debounceMs)
            reload()
        }
    }

    private fun reload() {
        val myPackage = context.packageName
        val mainUser = Process.myUserHandle()
        val result = mutableListOf<AppInfo>()
        val infos = HashMap<String, LauncherActivityInfo>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot list activities for profile $serial", e)
                continue
            }
            for (info in activities) {
                if (info.componentName.packageName == myPackage) continue
                val key = "${info.componentName.flattenToShortString()}#$serial"
                infos[key] = info
                result += AppInfo(
                    key = key,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    packageName = info.componentName.packageName,
                    componentName = info.componentName,
                    user = user,
                    category = categorize(info.applicationInfo),
                    isWorkProfile = user != mainUser,
                )
            }
        }
        activityInfos.keys.retainAll(infos.keys)
        activityInfos.putAll(infos)
        // Drop cached icons so updated apps show their new icon.
        iconCache.evictAll()
        _apps.value = result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        _loaded.value = true
    }

    fun cachedIcon(key: String): ImageBitmap? = iconCache.get(key)

    suspend fun loadIcon(key: String): ImageBitmap? {
        iconCache.get(key)?.let { return it }
        val info = activityInfos[key] ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                info.getBadgedIcon(0).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap()
            }.getOrNull()?.also { iconCache.put(key, it) }
        }
    }

    fun launch(app: AppInfo, sourceBounds: Rect? = null, options: Bundle? = null): Boolean = try {
        launcherApps.startMainActivity(app.componentName, app.user, sourceBounds, options)
        true
    } catch (e: Exception) {
        // SecurityException, ActivityNotFoundException, or the app was just removed.
        Log.w(TAG, "Failed to launch ${app.key}", e)
        false
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.componentName, app.user, null, null) }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openAppInfo(app)
        }
    }

    /** App shortcuts are only available while Drengr is the default Home app. */
    fun shortcuts(app: AppInfo): List<AppShortcut> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        val shortcuts: List<ShortcutInfo> = try {
            launcherApps.getShortcuts(query, app.user).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        return shortcuts
            .filter { it.isEnabled }
            .sortedBy { it.rank }
            .take(5)
            .map {
                AppShortcut(
                    id = it.id,
                    label = (it.shortLabel ?: it.longLabel ?: it.id).toString(),
                    packageName = it.`package`,
                    user = it.userHandle,
                )
            }
    }

    fun launchShortcut(shortcut: AppShortcut) {
        runCatching {
            launcherApps.startShortcut(shortcut.packageName, shortcut.id, null, null, shortcut.user)
        }.onFailure { Log.w(TAG, "Failed to start shortcut ${shortcut.id}", it) }
    }

    /** Sensible first-run dock: phone, messages, browser, camera — whichever resolve. */
    fun defaultDockKeys(apps: List<AppInfo>): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        val mainUser = Process.myUserHandle()
        return intents.mapNotNull { intent ->
            val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            apps.firstOrNull { it.packageName == pkg && it.user == mainUser }?.key
        }.distinct()
    }

    private fun categorize(info: ApplicationInfo): AppCategory {
        val pkg = info.packageName.lowercase()
        fun pkgHas(vararg words: String) = words.any { pkg.contains(it) }
        if (info.category == ApplicationInfo.CATEGORY_GAME) return AppCategory.Games
        @Suppress("DEPRECATION")
        if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) return AppCategory.Games
        return when {
            pkgHas("camera", "gallery", "photo", "lightroom", "snapseed") -> AppCategory.Photography
            info.category == ApplicationInfo.CATEGORY_IMAGE -> AppCategory.Photography
            pkgHas("bank", "wallet", "finance", "paypal", "venmo", "cashapp", "invest", "crypto", "money") ->
                AppCategory.Finance
            pkgHas("termux", "github", "gitlab", "jetbrains", "vscode", "ssh", "devtools", "terminal", "code") ->
                AppCategory.Development
            info.category == ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.Communication
            pkgHas("messag", "mail", "dialer", "phone", "contacts", "telegram", "whatsapp", "signal",
                "discord", "slack", "teams", "sms", "chat", "outlook") -> AppCategory.Communication
            info.category == ApplicationInfo.CATEGORY_AUDIO ||
                info.category == ApplicationInfo.CATEGORY_VIDEO -> AppCategory.Media
            pkgHas("music", "spotify", "podcast", "youtube", "netflix", "video", "player", "radio", "audible") ->
                AppCategory.Media
            info.category == ApplicationInfo.CATEGORY_PRODUCTIVITY ||
                info.category == ApplicationInfo.CATEGORY_NEWS -> AppCategory.Productivity
            pkgHas("calendar", "docs", "notes", "office", "keep", "todo", "task", "drive", "notion", "obsidian") ->
                AppCategory.Productivity
            else -> AppCategory.Utilities
        }
    }

    private companion object {
        const val TAG = "AppRepository"
        const val ICON_SIZE_PX = 192
    }
}
