# Drengr privacy policy

_Last updated: November 3, 2026_

Drengr is an Android home-screen app (launcher). It has no accounts, no ads, no analytics and no
tracking, and its developer runs no servers. Almost everything Drengr shows is read on your phone
and stays there. This page lists every exception and every permission, and why it's there.

## What leaves your phone

Only these, and only after you turn on the feature:

| Feature | Sent to | What is sent |
|---|---|---|
| Weather with **current location** | [Open-Meteo](https://open-meteo.com/en/terms) | Your approximate location, rounded to about 1 km, to get the forecast |
| Weather with a **chosen city** | Open-Meteo | The city name you type (to find it), then that city's coordinates |
| **Smart home** (Govee) | [Govee](https://us.govee.com/pages/privacy-policy) | Your own Govee API key, and the commands you give your devices |
| Tapping the **weather line** or **Search the web** | Google, or your browser's search engine | The search (e.g. "weather Chicago"), sent by the app that opens, not by Drengr |

Everything is sent over HTTPS. Drengr sends nothing else and keeps no copies on any server.

## What stays on your phone

- **Apps:** your installed apps, favorites, dock, hidden and renamed apps, and how often you open them (for suggestions).
- **Calendar** (if you allow calendar access): upcoming events, read when shown and never stored.
- **Contacts** (only if you turn on Settings → Command Center → Search contacts and allow access): looked up by name while you search, never copied or stored.
- **Notes and tasks** you write in Drengr.
- **Recent documents:** the files you pick yourself. Drengr can open only those files.
- **Notification access** (optional): Drengr keeps only a count of notifications per app, for notification dots, never their content. The same access lets the Media page show and control what's playing, and remember the last 10 tracks for "Recently played".
- **Settings:** theme, pages, gestures and so on.

## Backups

- **Settings exports** (Settings → Backup & restore) are files you create and keep. They include settings, notes and tasks. They never include your Govee API key, listening history or recent documents.
- **Android's own backup:** if it's on in your phone's settings, Android may copy Drengr's settings, notes and tasks to your Google account or to a new phone. Drengr leaves your Govee API key, listening history and recent documents out of these backups.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | Weather and Govee, only when you use them |
| Approximate location (asked when you first tap "Show weather") | Weather for where you are. Not needed if you pick a city |
| Calendar (asked when you first tap the Agenda) | Show your upcoming events |
| Contacts (asked when you turn on contact search) | Find people from Command Center |
| Notification access (turned on by you in Android settings) | Notification dots, media controls, Recently played |
| Do Not Disturb access (asked when you first use the toggle) | The "Toggle Do Not Disturb" action |
| Expand status bar | Opening notifications or quick settings with a gesture |
| Request delete packages | "Uninstall" in an app's menu (Android still asks you to confirm) |

You can withdraw any of these in Android's settings at any time. Drengr keeps working without them.

## Deleting your data

Uninstalling Drengr deletes everything it stored on your phone. You can also:
- remove the Govee key in Settings → Smart home;
- clear Recently played on the Media page and Command Center's Recent list in Settings → Command Center;
- delete notes, tasks and recent documents in Drengr.

## Children

Drengr is not directed at children and collects no personal information from anyone.

## Changes and contact

Changes to this policy will be posted here and noted in the app's release notes. For questions,
open an issue at https://github.com/joelishere21/Drengr/issues.
