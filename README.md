# Kasaka — SakaSense mobile app

Smart soil intelligence for Filipino farms. Kasaka is the Android companion to the
[SakaSense](https://www.sakasense.com) soil device: pair over Bluetooth, read soil pH and N-P-K,
and get a soil health score, the best crops for the plot and a fertilizer prescription, all offline.

<p align="center"><img src="docs/play-store/icon-512.png" width="96" alt="SakaSense logo"></p>

## Features

| | |
|---|---|
| **Scan** | Pair with the SakaSense device over Bluetooth LE, measure, and save to a plot. Includes a simulated demo device and manual entry of values from the device LCD. |
| **Soil health score** | The same 0–100 index as the web dashboard, so a reading scores the same everywhere. |
| **SakaSense AI (on-phone)** | Follows the Kasaka Guidebook (`docs/guidebook/`): sorts each reading into the book's LOW / SLIGHTLY LOW / GOOD / HIGH boxes, gives the 1–7 code and the crop's Fix-it dose (per bed, for the whole plot, or per m² for containers), and ranks the best crops. Crops from earlier versions that the book has no chapter for (tomato, rice, potato, cassava, chili, lettuce, basil) still work, with the book's general amounts. |
| **Farms & plots** | Multiple farms, a farm layout map coloured by plot status, and per-plot history. |
| **Trends** | pH / N / P / K charts over 7, 14 or 30 days, with the target zone shaded. |
| **Alerts** | Deficiencies, offline devices and overdue check-ins. |
| **Guidebook** | The Kasaka Guidebook on the phone: crop chapters (growing, reading table, fertilizer plan, Fix-it codes) for kangkong, pechay, sweet potato and okra, plus the soil-care pages. Search by code 1–7. |
| **Reports** | Share a plot's readings as CSV (to Sheets, Excel, email or Messenger). |
| **Offline-first** | Everything is stored on the phone (Room database). |
| **Light & dark themes** | Brand colours and fonts from sakasense.com. Light mode is easier to read in sunlight. |

Demo account (same as the web dashboard): `maria@sakasense.demo` / `sakasense123`, or tap **Try the demo account**.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Navigation Compose (type-safe routes), ViewModel + StateFlow
- Room (offline database), DataStore (session & settings)
- Bluetooth LE GATT client (`device/`), kotlinx.serialization
- AGP 9.4, Kotlin 2.4, compileSdk 37, targetSdk 36, minSdk 26 (Android 8.0+)

```
app/src/main/java/com/sakasense/kasaka/
├── data/            Room entities & DAOs, repositories, demo data
├── device/          BLE protocol, BLE link, device manager (+ simulated device)
├── domain/          SoilAnalyzer (health index, crop ranking, fertilizer), crop profiles, Guidebook
└── ui/              Compose screens: onboarding, auth, home, plots, scan, guidebook, alerts, profile
```

## Getting started

1. Install **Android Studio** (2026.2 or newer).
2. `File → Open…` and pick this folder. Let Gradle sync. It downloads SDK 37 if missing.
3. Run the `app` configuration on a phone or emulator.

Command line:

```bash
./gradlew :app:assembleDebug        # debug APK  → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests (soil analyzer)
./gradlew :app:bundleRelease        # Play Store bundle → app/build/outputs/bundle/release/
```

The debug build installs as `com.sakasense.kasaka.debug`, so it can sit next to the Play Store version.

## Publishing to Google Play

1. **Create an upload key** once and keep it safe. If you lose it, you have to ask Google to reset it.
   ```bash
   keytool -genkeypair -v -keystore kasaka-upload.jks -alias kasaka -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **Create `keystore.properties`** in the project root. It is git-ignored, so never commit it.
   ```properties
   storeFile=kasaka-upload.jks
   storePassword=…
   keyAlias=kasaka
   keyPassword=…
   ```
3. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` for every upload.
4. `./gradlew :app:bundleRelease` and upload `app-release.aab` in the Play Console.
5. Store listing assets: `docs/play-store/icon-512.png` (app icon). You still need a 1024×500
   feature graphic and phone screenshots.
6. **Data safety form:** the app stores account details and soil readings **on the device only**.
   Nothing is sent to a server yet. It uses Bluetooth to connect to the SakaSense device.
   The Bluetooth scan permission is declared `neverForLocation`.
7. A privacy policy URL is required: `https://www.sakasense.com/privacy`.

> ⚠️ The application ID `com.sakasense.kasaka` is permanent once published. Change it before the first upload if you want a different one.

## Device protocol

The BLE contract (UUIDs, `CROP:` and `MEASURE` commands, JSON payload) is in
[`docs/DEVICE_PROTOCOL.md`](docs/DEVICE_PROTOCOL.md). The ESP32 firmware that implements it is in
[`kasaka_device/`](kasaka_device/) (`kasaka_device.ino`; `probe_scan/` finds the probe's registers).
Firmware 2.x judges readings with the same Kasaka Guidebook rules as the app. Without hardware, use
the **demo device** or **manual entry**.

## Roadmap

- Cloud sync and real accounts with the sakasense.com backend (`SessionRepository` and
  `SoilRepository` are the only classes that need to change)
- Push notifications for alerts
- Filipino and Waray translations (move UI strings into `strings.xml`)
- Wi-Fi provisioning of the device from the app
- Agronomist review of the Kasaka Guidebook; `domain/CropProfiles.kt` and `domain/Guidebook.kt` copy it, so change the book first

---

© 2026 SakaSense Venture Solutions · Sagkahan, Tacloban City
