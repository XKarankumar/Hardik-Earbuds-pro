# Device Compatibility Matrix

## Android OS Support
- **Minimum**: Android 8.0 (API 26)
- **Target / Compile**: Android 16 (API 36)
- Tested across API 26–36 behavior differences:
  - Android 8–11: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` for BLE scan.
  - Android 9+ / 10+: `BiometricPrompt` (`BIOMETRIC_STRONG | BIOMETRIC_WEAK`), Android Keystore AES-256-GCM, and PBKDF2-HMAC-SHA256 Security Center.
  - Android 12+: `BLUETOOTH_SCAN` (`neverForLocation`) & `BLUETOOTH_CONNECT`.
  - Android 12L / 13+: `AudioManager.getSpatializer()` & `Spatializer.OnHeadTrackerAvailableListener`.
  - Android 13+: `POST_NOTIFICATIONS` runtime permission.

## Earbuds Brand Profiles

| Brand / Model | Connection & A2DP/HFP | BLE GATT Inspection | Battery Telemetry | Fast Pair Detection | Proprietary ANC / Touch Handoff |
|---|---|---|---|---|---|
| **OnePlus Buds 3 Pro / Nord Buds 3 Pro** | Full | Full | Standard GATT `0x180F` / HFP AT / Broadcast | Yes (`0xFE2C`) | **HeyMelody** |
| **Samsung Galaxy Buds** | Full | Full | Standard GATT / Broadcast | Model dependent | **Galaxy Wearable** |
| **Sony WF/WH Series** | Full | Full | Standard GATT / Broadcast | Yes | **Sony Sound Connect** |
| **JBL Headphones** | Full | Full | Standard GATT / Broadcast | Yes | **JBL Headphones** |
| **boAt Airdopes / Nirvana** | Full | Full | HFP / Broadcast where exposed | Model dependent | **boAt Hearables** |
| **Realme Buds** | Full | Full | Standard GATT / Broadcast | Yes | **realme Link** |
| **Nothing Ear / CMF Buds** | Full | Full | Standard GATT / Broadcast | Yes | **Nothing X** |
| **Generic Bluetooth Audio** | Full | Full | Standard GATT / Broadcast | If advertised | N/A |
