# Permissions Documentation

HARDIK BUDS PRO follows Google Play Developer Program least-privilege guidelines:

| Permission | API Scope | Purpose |
|---|---|---|
| `BLUETOOTH_CONNECT` | Android 12+ (API 31+) | Read paired devices, connection state, and connect BLE GATT |
| `BLUETOOTH_SCAN` (`neverForLocation`) | Android 12+ (API 31+) | Discover nearby BLE earbuds and Fast Pair (`0xFE2C`) beacons |
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` | Android 8–11 (`maxSdkVersion=30`) | Required by Android <= 11 for BLE scanning |
| `POST_NOTIFICATIONS` | Android 13+ (API 33+) | Connection/disconnection alerts, low battery alerts, media controls |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | All | Background Media3 ExoPlayer audio playback |
| `MODIFY_AUDIO_SETTINGS` | All | Media stream volume & AudioEffect inspection |
| `SYSTEM_ALERT_WINDOW` | Optional (Opt-In) | Optional external connection card overlay; falls back to notifications if ungranted |
| `USE_BIOMETRIC` | Android 9+ (Normal permission) | Fingerprint / biometric owner verification in `SecurityCenterScreen` via Jetpack `BiometricPrompt` |

**Zero-Permission Media Selection**: Local audio files are picked via Android's Storage Access Framework (`ActivityResultContracts.OpenMultipleDocuments`), requiring **zero** `READ_EXTERNAL_STORAGE` or `READ_MEDIA_AUDIO` permissions.
