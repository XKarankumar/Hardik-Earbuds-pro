# Architecture — HARDIK BUDS PRO

## Package Structure (`app/src/main/java/com/example/`)

- `core/` — `AppConstants`, `PermissionManager`, `CompanionAppLauncher`
- `bluetooth/` — `BudsBluetoothManager`, `BluetoothModels` (`BluetoothLeScanner`, `BluetoothGatt`, `BluetoothA2dp`/`BluetoothHeadset` proxies, `CompanionDeviceManager`, system broadcast receivers)
- `devices/` — `EarbudsModels` (`BatteryTelemetry`, `AncCapabilityState`, `TouchGestureConfig`, `EarbudsCapabilities`)
- `integrations/` — `BrandIntegrations` (`EarbudsIntegrationRegistry`, `OnePlusNordBuds3ProIntegration`, `StandardBrandIntegration`)
- `equalizer/` — `EqualizerEngine` (`TenBandBiquadAudioProcessor`, `EqualizerCatalog`, `EqualizerController`)
- `audio/` — `BudsPlaybackService` (`MediaSessionService`), `BudsMediaPlayerManager` (`MediaController`), `SpatialAudioMonitor`, `FindEarbudsEngine`
- `notifications/` — `BudsNotificationManager`, `ConnectionOverlayService` (Connection, Low Battery, Media, and Security Alert channels)
- `security/` — `SecurityCryptoManager` (Android Keystore AES-256-GCM + PBKDF2-HMAC-SHA256 + 24-char Base32 Recovery Keys), `BiometricAuthHelper` (`BiometricPrompt`), `SecurityCenterManager` (Owner lock, lockout escalation, No-Gmail PIN recovery & key rotation, Trusted Devices, Unauthorized Connection monitor, Lost Mode)
- `data/` — `BudsProDatabase` (Room v2 with `MIGRATION_1_2`, adding `trusted_devices` and `security_events`), `BudsRepository`, `AppPreferencesRepository` (DataStore), `SecurityPreferencesRepository` (DataStore security vault)
- `ui/` — `theme/` (`Color`, `Type`, `Theme`), `components/` (`LiquidGlassComponents`, `ConnectionPopupSheet`), `navigation/` (`BudsRoutes`), `screens/` (`SplashOverlay`, `DashboardScreen`, `DevicesScreen`, `StudioScreen`, `StudioComponents`, `BatteryScreen`, `SecurityCenterScreen`, `SecurityDialogs`, `SettingsScreen`), `viewmodel/` (`BudsProViewModel`)
