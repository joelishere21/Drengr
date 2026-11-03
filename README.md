# Drengr — Android Launcher

> A smarter home for Android. Native Kotlin + Jetpack Compose, Omarchy-inspired, blue accent (`#579BFF`).

## Status: Alpha 0.1 (Milestones 1, 2 and most of 4)

| Alpha 0.1 criterion | State |
|---|---|
| Installable APK, selectable as the default Home app | ✅ HOME intent filter + RoleManager prompt in Settings |
| Five swipeable pages with a page indicator | ✅ Home · Applications · Productivity · System · Media |
| Working Home screen and persistent dock | ✅ Clock, date, weather, next alarm, agenda, quick note, favorites over your wallpaper; dock with 4 apps + Apps + Command Center (reorder from an app's long-press menu or Settings → Dock); Productivity page lists the next 7 days of events by day |
| App discovery, icons, search, favorites, launching | ✅ LauncherApps (incl. work profile), fuzzy search, categories, grid/list, long-press menu, app shortcuts, rename and hide apps |
| Blue theme applied consistently | ✅ Central design tokens; 6 accents selectable |
| Command Center opens/closes, launches apps, runs actions | ✅ Overlay (dock button or swipe up on Home), ~30 built-in actions |
| Navigation and preferences persist across restarts | ✅ DataStore (last page, favorites, dock, page order, theme, recents, note) |
| Test on the S25 Ultra and one more device profile | ⏳ Needs you: see "Install on your phone" |

Not done yet (later milestones): Android widget hosting, tablet layouts, configurable gesture zones.

**Wallpaper:** the system wallpaper shows behind every page. Settings → Appearance has "Change wallpaper" and a dimming slider (0–100 %, default 40 %; 100 % gives the original solid background). Scrolling wallpapers follow the pager.

**Agenda (Home):** the next 3 events from every calendar on the phone (Google, Samsung, Exchange…), read
locally via `CalendarContract`, updated live when calendars change. Calendar access is requested the first
time the Agenda card is tapped. Tap an event to open it, or the header to open the calendar app.

**Weather (Home):** current conditions, high/low and rain chance from [Open-Meteo](https://open-meteo.com)
(free, no account or API key). Location is either approximate device location (asked for when you tap
"Show weather") or a city searched in **Settings → Weather**, where units and on/off also live.
Coordinates are rounded to ~1 km before being sent; results are cached for 30 minutes.

## Gestures

- **Swipe left/right:** change page (no wraparound)
- **Home page, swipe down:** notification shade (can be turned off in Settings)
- **Home page, swipe up:** Command Center
- **Home page, long-press on empty space:** Settings (page editing)
- **Long-press an app:** shortcuts, favorite, dock (and move left/right when it's in the dock), rename, hide, app info, uninstall. Hidden apps leave the app list and suggestions but still turn up in Command Center search; unhide them in Settings → Hidden apps. A renamed app is still found by its original name.
- **Home button / Back:** close the overlay, then return to the Home page

## Build

Requires JDK 17–21 (not JDK 27 — AGP 8.7 doesn't support it) and the Android SDK (platform 35).

```bash
export JAVA_HOME=~/.local/share/mise/installs/java/temurin-21.0.13+9.0.LTS
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android lint
```

`local.properties` (git-ignored) points at the SDK.

## Install on your phone (Galaxy S25 Ultra)

1. Turn on **Settings → About phone → Software information → tap Build number 7×** to enable Developer options.
2. Turn on **Developer options → USB debugging** and connect over USB (or use Wireless debugging).
3. `~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. Open Drengr, long-press Home → **Settings → Default Home app**, or use the Command Center action
   "Choose default Home app". To switch back: **Settings → Apps → Choose default apps → Home app**.

## Code layout

```
app/src/main/java/com/drengr/launcher/
├── LauncherApplication.kt           Hilt entry point
├── core/
│   ├── designsystem/                colors, typography, shapes, spacing, shared components
│   ├── datastore/                   SettingsRepository (DataStore + JSON export/import)
│   ├── domain/                      models: PageId, AppInfo, UserSettings…
│   └── common/                      FuzzyMatcher
├── integrations/                    Android API wrappers
│   ├── applications/                AppRepository (LauncherApps, icons, shortcuts)
│   ├── battery/  connectivity/  system/
│   ├── media/                       AudioController, MediaSessionRepository (now playing)
│   ├── notifications/               DrengrNotificationListener, dot counts
├── plugins/                         ActionRegistry, LauncherAction, FlashlightController
├── launcher/                        LauncherActivity, LauncherScreen (pager), ViewModel, Dock, PageIndicator, AppItem
└── feature/
    ├── home/  applications/  productivity/  system/  media/
    ├── commandcenter/               overlay + ranking ViewModel
    └── settings/                    SettingsActivity
```

**Principles (from the spec):** screens consume ViewModels and never write preferences directly. Android
APIs sit behind repositories. Command Center actions are trusted, built-in `LauncherAction`s registered in
`ActionRegistry`, each labeled **Direct** (the launcher does it itself) or **Settings/App** (hands off to
Android). Runtime permissions (calendar, approximate location) are requested only when the user taps the feature
that needs them; if Android stops showing the prompt, the next tap opens App info instead. Notification access
(Settings → Notifications & media, or the Media page) is optional: it powers notification dots and the Media
page's track, artwork, progress and seeking. Drengr keeps only per-app counts, never notification content.

## Adding a Command Center action

Register a `LauncherAction` in `ActionRegistry.builtIns()`:

```kotlin
LauncherAction("id", "Label", "Description", ActionCategory.System, ActionKind.OpensSettings,
    Icons.Outlined.Something, keywords = listOf("alias")) { ctx -> ctx.launch(Intent(...)) }
```

It then shows up in Command Center search, and on the System or Productivity page if its category matches.
