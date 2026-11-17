# HARDIK BUDS PRO — ULTIMATE PRO V2.0

**Copyright © 2026 Hardik Kumar. All Rights Reserved.**

A luxury Obsidian Black + Platinum Silver universal wireless earbuds companion app for Android (8.0+ / API 26 to API 36), built with Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer, Room, DataStore, and real Android Bluetooth / BLE GATT APIs.

---

## Key Features

1. **Luxury Liquid Glass Interface**
   - Obsidian Black + Platinum Silver Dark Theme and Brushed Aluminum Light Theme.
   - Custom bundled Google Fonts (`Outfit`, `Plus Jakarta Sans`, `JetBrains Mono`), adaptive launcher icon, animated launch splash, and responsive `NavigationBar` / `NavigationRail` layouts.

2. **Animated Connection Popup & System Alerts**
   - In-app spring-animated Liquid Glass popup sheet displaying real connection status (`Connecting`, `Connected`, `Disconnected`), Left/Right/Case battery gauges (only when exposed by hardware), and companion controls.
   - High-priority Android notification fallback and opt-in `SYSTEM_ALERT_WINDOW` floating overlay card.

3. **Google Fast Pair & Companion Device Pairing**
   - Uses official Android `CompanionDeviceManager` (`AssociationRequest` with `0xFE2C` Fast Pair BLE filter and Classic Bluetooth filter) + Android system Bluetooth Settings.

4. **Dedicated OnePlus Nord Buds 3 Pro Integration + Multi-Brand Architecture**
   - Prioritized profile for **OnePlus Nord Buds 3 Pro** plus modular integrations for **Samsung, Sony, JBL, boAt, Realme, Nothing**, and **Generic Bluetooth Audio**.
   - Directly launches **HeyMelody** (`com.heymelody.android` / `com.oplus.melody`) or the respective OEM companion app for proprietary firmware-authenticated ANC/Touch controls.

5. **Bluetooth Device Manager & Live BLE GATT Inspector**
   - Real `BluetoothLeScanner` discovery, paired device enumeration, bonding, and live `BluetoothGatt` service/characteristic inspection (`0x180F` Battery Service, `0x180A` Device Information, `0xFE2C` Fast Pair, LE Audio UUIDs).

6. **10-Band Biquad DSP Equalizer & Media3 Audio Player**
   - Genuine 10-band cascaded Biquad peaking IIR `AudioProcessor` (`60 Hz, 120 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz, 12 kHz, 16 kHz`) integrated directly into Jetpack **Media3 ExoPlayer** (`MediaSessionService`).
   - All 10 presets (`Normal`, `Bass Boost`, `Deep Bass`, `Rock`, `Pop`, `Classical`, `Jazz`, `Vocal`, `Treble Boost`, `Custom`) plus custom Room-persisted presets and native `AudioEffect.Equalizer` HAL diagnostics.
   - Local audio file playback via zero-permission Storage Access Framework (`OpenMultipleDocuments`), background `MediaSessionService`, media notification, AudioFocus, and system stream volume control.

7. **Android Spatializer, Battery Dashboard & Find Earbuds**
   - Real `AudioManager.getSpatializer()` detection (availability, enabled state, head-tracking hardware, and 5.1 multichannel spatialization eligibility).
   - Battery trend chart and log history via Room + configurable low-battery notification threshold.
   - Stereo-panned `AudioTrack` Find Earbuds acoustic chime (Left / Right / Both) with ear-safety notice.

---

## Setup & Build Instructions

1. Open the repository in **Android Studio** (Ladybug / Meerkat or newer with JDK 17/21 and Android SDK 36).
2. Let Gradle sync (`compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`).
3. Build Debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
4. Run Unit & Robolectric Tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
5. Build Release APK (requires keystore environment variables `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`):
   ```bash
   gradle :app:assembleRelease
   ```

---

## Documentation Index

- `docs/ARCHITECTURE.md` — Clean layered package structure
- `docs/COMPATIBILITY.md` — Supported Android versions & earbud brand matrix
- `docs/PERMISSIONS.md` — Least-privilege permission breakdown
- `docs/HARDWARE_LIMITATIONS.md` — Transparent OEM protocol & Android SDK constraints
- `docs/TESTING_REPORT.md` — Automated JVM & Robolectric test results
