# Releasing Drengr

Drengr is set up for **Play App Signing**: Google Play holds the key that signs the app on users'
phones, and you sign each upload with your own **upload key**. If the upload key is ever lost or
leaked, Play support can reset it. The app's real signing key never leaves Google.

## 1. Create the upload key (once)

Run this outside the repo. Choose the passwords yourself, and back up the `.jks` file and its
passwords somewhere safe, such as a password manager:

```bash
mkdir -p ~/.android-keys
keytool -genkeypair -v -keystore ~/.android-keys/drengr-upload.jks \
  -alias drengr-upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Drengr upload key"
```

Then create `keystore.properties` in the project root. It is git-ignored; never commit it:

```properties
storeFile=/home/<you>/.android-keys/drengr-upload.jks
storePassword=<store password>
keyAlias=drengr-upload
keyPassword=<key password>
```

On CI, set `DRENGR_UPLOAD_STORE_FILE`, `DRENGR_UPLOAD_STORE_PASSWORD`, `DRENGR_UPLOAD_KEY_ALIAS` and
`DRENGR_UPLOAD_KEY_PASSWORD` instead. Without the key, release builds are produced unsigned.

## 2. Build

1. Bump `versionCode` (Play needs a higher one for every upload) and `versionName` in
   `app/build.gradle.kts`.
2. Build and test:

```bash
export JAVA_HOME=~/.local/share/mise/installs/java/temurin-21.0.13+9.0.LTS
export ANDROID_HOME=~/Android/Sdk
./gradlew testDebugUnitTest lintDebug bundleRelease
# → app/build/outputs/bundle/release/app-release.aab (upload this to Play)
```

Release builds are shrunk with R8 (the APK is about 1.6 MB) and include the libraries' startup
profiles. Before uploading, install a release build on a phone and check every page, Command
Center, Settings, weather and the note editor:

```bash
./gradlew assembleRelease
# Signed with the upload key → app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

A release build signed with your upload key can't install over a debug build, which uses a
different key. Uninstall first, but export your settings beforehand (Settings → Backup & restore).

## 3. Play Console checklist

- **App signing:** when setting up the first release, let Google create and manage the app signing
  key (the recommended option), then upload the `.aab` signed with your upload key.
- **Target API level:** Play requires apps to target a recent Android version, and the minimum rises
  every August. Drengr targets API 35. Check the requirement shown in Play Console before uploading;
  if it asks for 36, install SDK platform 36 and raise `compileSdk` and `targetSdk`, then retest.
- **Privacy policy:** required, because Drengr can ask for location, calendar and contacts access.
  Use the public URL of [PRIVACY.md](PRIVACY.md), e.g.
  `https://github.com/joelishere21/Drengr/blob/main/PRIVACY.md`. The repository must be public for
  that link to work; otherwise host the page elsewhere.
- **Data safety form.** These answers follow PRIVACY.md. Review them against Play's current
  definitions; this is not legal advice.
  - Data is encrypted in transit: yes (HTTPS only).
  - Users can ask for deletion: there is no account. All data is on the device and is removed on
    uninstall.
  - **Location → Approximate location:** collected (sent to Open-Meteo, rounded) for App
    functionality. Optional: only with current-location weather. Not used for ads or tracking.
  - **Govee:** the user's own API key and device commands go to Govee, at the user's request, to
    control their devices (App functionality, optional).
  - **Not collected** (stays on the device): contacts, calendar, notes and tasks, files, app
    list and usage counts, notification counts, listening history.
- **Permissions:** none of Drengr's permissions are on Play's restricted list. It doesn't use
  `QUERY_ALL_PACKAGES`, SMS, call log, accessibility, all-files access or background location.
- **Content rating:** fill in the questionnaire. Drengr has no user-generated content shared with others.
- **Store listing:** screenshots (phone, and tablet if you list tablet support), a 512×512 icon and
  a 1024×500 feature graphic. Before publishing, check that the name "Drengr" is free to use as a
  trademark and store name.
