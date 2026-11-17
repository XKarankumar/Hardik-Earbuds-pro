# Known Hardware & Android Platform Limitations

1. **OnePlus Nord Buds 3 Pro ANC, Transparency & Touch Controls**
   - OnePlus / Oplus firmware restricts ANC mode writes (49dB Adaptive ANC, Transparency, Wind Noise Reduction), Dual Connection toggling, and Touch Gesture remapping to authenticated HeyMelody / Oplus protocol sessions.
   - HARDIK BUDS PRO never simulates fake ANC confirmation or sends undocumented GATT/RFCOMM opcodes. It reports real hardware/protocol boundaries and launches **HeyMelody** directly when installed.

2. **Individual Left / Right / Charging Case Battery Percentages**
   - Android restricts `BluetoothDevice.METADATA_UNTETHERED_*` to privileged `@SystemApi` system apps (`BLUETOOTH_PRIVILEGED`).
   - Third-party apps read battery via public BLE GATT Battery Service (`0x180F` / `0x2A19`), HFP AT events (`+IPHONEACCEV`), and `ACTION_BATTERY_LEVEL_CHANGED` broadcasts. Unavailable fields remain `"--"` and are never invented.

3. **Case-Open Detection & Google Fast Pair System Popups**
   - Google Play Services handles native Fast Pair system half-sheets. Third-party apps cannot reliably intercept proprietary case-open events unless the earbuds broadcast documented public BLE advertisements. HARDIK BUDS PRO uses `CompanionDeviceManager` and BLE `0xFE2C` service detection.

4. **10-Band Equalizer Scope**
   - The genuine 10-band Biquad DSP (`60 Hz – 16 kHz`) processes audio inside HARDIK BUDS PRO's Media3 ExoPlayer pipeline. Non-privileged Android apps cannot intercept and modify third-party streaming apps system-wide.

5. **Spatial Audio**
   - Android `AudioManager.getSpatializer()` requires Android 12L/13+ and OEM Spatializer HAL support. Dolby Atmos certification is never claimed unless exposed by the device.
