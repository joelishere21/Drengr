package com.quadro.launcher.plugins

import android.app.NotificationManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Wifi
import com.quadro.launcher.core.domain.PageId
import com.quadro.launcher.feature.settings.SettingsActivity
import com.quadro.launcher.integrations.media.AudioController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of named actions for the Command Center. Built-in, trusted actions only:
 * nothing here loads external code. Future extensions should register through
 * permission-scoped intents/IPC rather than in-process plugins.
 */
@Singleton
class ActionRegistry @Inject constructor(
    private val flashlight: FlashlightController,
    private val audio: AudioController,
) {
    private val actions = LinkedHashMap<String, LauncherAction>()

    init {
        builtIns().forEach(::register)
    }

    fun register(action: LauncherAction) {
        actions[action.id] = action
    }

    operator fun get(id: String): LauncherAction? = actions[id]

    fun available(context: Context): List<LauncherAction> = actions.values.filter { it.isAvailable(context) }

    /** Opens a web search for free text typed into the Command Center. */
    fun webSearch(context: Context, query: String): ActionResult = LauncherAction(
        id = "web_search",
        label = "Search the web",
        description = query,
        category = ActionCategory.Productivity,
        kind = ActionKind.OpensApp,
        icon = Icons.Outlined.Public,
    ) { ctx ->
        try {
            ctx.launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
        } catch (e: android.content.ActivityNotFoundException) {
            ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query))))
        }
    }.run(context)

    private fun settingsAction(
        id: String,
        label: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        keywords: List<String>,
        intent: () -> Intent,
    ) = LauncherAction(id, label, description, ActionCategory.System, ActionKind.OpensSettings, icon, keywords) {
        it.launch(intent())
    }

    private fun builtIns(): List<LauncherAction> = listOf(
        // ---- System: direct controls --------------------------------------------------------
        LauncherAction(
            id = "flashlight",
            label = "Toggle flashlight",
            description = "Turn the torch on or off",
            category = ActionCategory.System,
            kind = ActionKind.Direct,
            icon = Icons.Outlined.FlashOn,
            keywords = listOf("torch", "light", "flash"),
            isAvailable = { flashlight.isAvailable },
        ) {
            if (flashlight.toggle()) ActionResult.Done else ActionResult.Message("No flashlight available")
        },
        LauncherAction(
            id = "dnd",
            label = "Toggle Do Not Disturb",
            description = "Switch between priority-only and all notifications",
            category = ActionCategory.System,
            kind = ActionKind.Direct,
            icon = Icons.Outlined.DoNotDisturbOn,
            keywords = listOf("dnd", "silence", "quiet", "focus", "mute"),
        ) { ctx ->
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) {
                val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                nm.setInterruptionFilter(
                    if (on) NotificationManager.INTERRUPTION_FILTER_ALL
                    else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                )
                ActionResult.Message(if (on) "Do Not Disturb off" else "Do Not Disturb on")
            } else {
                ctx.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                ActionResult.Message("Allow Drengr Do Not Disturb access to toggle it directly.")
            }
        },

        // ---- System: settings shortcuts -----------------------------------------------------
        settingsAction("wifi", "Wi-Fi", "Choose a Wi-Fi network", Icons.Outlined.Wifi,
            listOf("wireless", "network", "internet")) {
            if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
        },
        settingsAction("internet", "Internet & network", "Mobile data, hotspot and connectivity",
            Icons.Outlined.Public, listOf("mobile data", "cellular", "hotspot", "network")) {
            if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            else Intent(Settings.ACTION_WIRELESS_SETTINGS)
        },
        settingsAction("bluetooth", "Bluetooth", "Pair and manage devices", Icons.Outlined.Bluetooth,
            listOf("pair", "headphones", "devices")) { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) },
        settingsAction("airplane", "Airplane mode", "Flight mode settings", Icons.Outlined.AirplanemodeActive,
            listOf("flight", "plane")) { Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
        settingsAction("display", "Display", "Brightness, dark mode, screen timeout",
            Icons.Outlined.BrightnessMedium, listOf("brightness", "screen", "dark mode")) {
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        },
        settingsAction("volume", "Volume", "Media, call, ring and alarm volume",
            Icons.AutoMirrored.Outlined.VolumeUp, listOf("sound", "audio", "ringtone")) {
            if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_VOLUME) else Intent(Settings.ACTION_SOUND_SETTINGS)
        },
        settingsAction("sound", "Sound settings", "Ringtones, vibration and sound modes",
            Icons.AutoMirrored.Outlined.VolumeUp, listOf("ringtone", "vibrate")) { Intent(Settings.ACTION_SOUND_SETTINGS) },
        settingsAction("location", "Location", "Location services", Icons.Outlined.LocationOn,
            listOf("gps")) { Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
        settingsAction("battery", "Battery & power", "Battery usage and power saving", Icons.Outlined.BatteryFull,
            listOf("power", "saver", "charge")) { Intent(Intent.ACTION_POWER_USAGE_SUMMARY) },
        settingsAction("storage", "Storage", "Free up space", Icons.Outlined.Storage,
            listOf("space", "disk", "files")) { Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) },
        settingsAction("android_settings", "Android settings", "Open system settings", Icons.Outlined.Settings,
            listOf("system", "preferences")) { Intent(Settings.ACTION_SETTINGS) },

        // ---- Productivity -------------------------------------------------------------------
        LauncherAction("camera", "Open camera", "Take a photo", ActionCategory.Productivity,
            ActionKind.OpensApp, Icons.Outlined.PhotoCamera, listOf("photo", "picture", "selfie")) {
            it.launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        },
        LauncherAction("calendar", "Open calendar", "See your schedule", ActionCategory.Productivity,
            ActionKind.OpensApp, Icons.Outlined.CalendarToday, listOf("agenda", "schedule", "events")) {
            it.launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
        },
        LauncherAction("new_event", "New calendar event", "Create an appointment", ActionCategory.Productivity,
            ActionKind.OpensApp, Icons.Outlined.Event, listOf("appointment", "meeting", "schedule")) {
            it.launch(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI))
        },
        LauncherAction("alarms", "Alarms", "View or set alarms", ActionCategory.Productivity,
            ActionKind.OpensApp, Icons.Outlined.Alarm, listOf("clock", "wake")) {
            it.launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        },
        LauncherAction("timer", "Set a timer", "Start a countdown", ActionCategory.Productivity,
            ActionKind.OpensApp, Icons.Outlined.Timer, listOf("countdown", "stopwatch")) {
            it.launch(Intent(AlarmClock.ACTION_SET_TIMER))
        },
        LauncherAction("email", "Email", "Open your mail app", ActionCategory.Productivity,
            ActionKind.OpensApp, Icons.Outlined.Email, listOf("mail", "inbox")) {
            it.launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL))
        },
        LauncherAction("maps", "Maps", "Open your maps app", ActionCategory.Productivity,
            ActionKind.OpensApp, Icons.Outlined.Map, listOf("navigate", "directions")) {
            it.launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS))
        },
        LauncherAction("quick_note", "Write a quick note", "Jump to the note on the Productivity page",
            ActionCategory.Productivity, ActionKind.Navigation, Icons.Outlined.Edit, listOf("note", "memo", "jot")) {
            ActionResult.Navigate(PageId.Productivity)
        },

        // ---- Media --------------------------------------------------------------------------
        LauncherAction("media_play_pause", "Play / pause", "Control the active media session",
            ActionCategory.Media, ActionKind.Direct, Icons.Outlined.PlayArrow, listOf("music", "pause", "resume")) {
            audio.playPause(); ActionResult.Done
        },
        LauncherAction("media_next", "Next track", "Skip to the next track", ActionCategory.Media,
            ActionKind.Direct, Icons.Outlined.SkipNext, listOf("skip", "music")) {
            audio.next(); ActionResult.Done
        },
        LauncherAction("media_previous", "Previous track", "Go back a track", ActionCategory.Media,
            ActionKind.Direct, Icons.Outlined.SkipPrevious, listOf("back", "music")) {
            audio.previous(); ActionResult.Done
        },
        LauncherAction("media_page", "Media controls", "Open the Media page", ActionCategory.Media,
            ActionKind.Navigation, Icons.Outlined.MusicNote, listOf("music", "now playing")) {
            ActionResult.Navigate(PageId.Media)
        },

        // ---- Launcher -----------------------------------------------------------------------
        LauncherAction("quadro_settings", "Drengr settings", "Appearance, pages, backup",
            ActionCategory.Launcher, ActionKind.Navigation, Icons.Outlined.Tune,
            listOf("launcher", "theme", "accent", "customize", "preferences", "quadro")) {
            it.launch(Intent(it, SettingsActivity::class.java))
        },
        LauncherAction("default_home", "Choose default Home app", "Set Drengr as your launcher",
            ActionCategory.Launcher, ActionKind.OpensSettings, Icons.Outlined.Home,
            listOf("launcher", "default", "home screen")) {
            it.launch(Intent(Settings.ACTION_HOME_SETTINGS))
        },
        LauncherAction("wallpaper", "Change wallpaper", "Pick a wallpaper", ActionCategory.Launcher,
            ActionKind.OpensApp, Icons.Outlined.Wallpaper, listOf("background")) {
            it.launch(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"))
        },
        LauncherAction("all_apps", "All applications", "Open the Applications page", ActionCategory.Launcher,
            ActionKind.Navigation, Icons.Outlined.Apps, listOf("drawer", "apps")) {
            ActionResult.Navigate(PageId.Applications)
        },
        LauncherAction("system_page", "System status", "Open the System page", ActionCategory.Launcher,
            ActionKind.Navigation, Icons.Outlined.Settings, listOf("status", "device")) {
            ActionResult.Navigate(PageId.System)
        },
    )

    companion object {
        /** Shown in "Suggested" when the Command Center opens with no history. */
        val SUGGESTED_IDS = listOf("wifi", "flashlight", "camera", "quick_note", "calendar")
    }
}
