# Architecture — HARDIK BUDS PRO

## Package Structure (`app/src/main/java/com/example/`)

- `core/` — `AppConstants`, `PermissionManager`, `CompanionAppLauncher`
- `bluetooth/` — `BudsBluetoothManager`, `BluetoothModels` (`BluetoothLeScanner`, `BluetoothGatt`, `BluetoothA2dp`/`BluetoothHeadset` proxies, `CompanionDeviceManager`, system broadcast receivers)
- `devices/` — `EarbudsModels` (`BatteryTelemetry`, `AncCapabilityState`, `TouchGestureConfig`, `EarbudsCapabilities`)
- `integrations/` — `BrandIntegrations` (`EarbudsIntegrationRegistry`, `OnePlusNordBuds3ProIntegration`, `StandardBrandIntegration`)
- `equalizer/` — `EqualizerEngine` (`TenBandBiquadAudioProcessor`, `EqualizerCatalog`, `EqualizerController`)
- `audio/` — `BudsPlaybackService` (`MediaSessionService`), `BudsMediaPlayerManager` (`MediaController`), `SpatialAudioMonitor`, `FindEarbudsEngine`
- `notifications/` — `BudsNotificationManager`, `ConnectionOverlayService`
- `data/` — `BudsProDatabase` (Room Entities & DAOs), `BudsRepository`, `AppPreferencesRepository` (DataStore)
- `ui/` — `theme/` (`Color`, `Type`, `Theme`), `components/` (`LiquidGlassComponents`, `ConnectionPopupSheet`), `navigation/` (`BudsRoutes`), `screens/` (`SplashOverlay`, `DashboardScreen`, `DevicesScreen`, `StudioScreen`, `StudioComponents`, `BatteryScreen`, `SettingsScreen`), `viewmodel/` (`BudsProViewModel`)
