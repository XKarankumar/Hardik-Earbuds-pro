package com.example.ui.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BudsProApplication
import com.example.audio.LocatorChannel
import com.example.audio.PlayerUiState
import com.example.audio.SpatialAudioStatus
import com.example.bluetooth.BluetoothDashboardState
import com.example.bluetooth.BluetoothUiEvent
import com.example.core.CompanionAppLaunchResult
import com.example.core.CompanionAppLauncher
import com.example.core.PermissionManager
import com.example.core.PermissionStatusSnapshot
import com.example.data.local.BatteryHistoryEntity
import com.example.data.local.DeviceHistoryEntity
import com.example.data.local.DeviceProfileEntity
import com.example.data.preferences.AppPreferencesState
import com.example.devices.EarbudsBrand
import com.example.equalizer.EqPreset
import com.example.equalizer.EqualizerCatalog
import com.example.equalizer.EqualizerController
import com.example.equalizer.EqualizerUiState
import com.example.notifications.BudsNotificationManager
import com.example.notifications.ConnectionOverlayService
import com.example.security.SecurityCenterManager
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiActionEvent {
    data class LaunchCompanionIntentSender(val intentSender: IntentSender) : MainUiActionEvent
    data class ShowSnackbar(val message: String) : MainUiActionEvent
}

class BudsProViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BudsProApplication
    private val repository = app.repository
    private val prefsRepo = app.preferencesRepository
    private val btManager = app.bluetoothManager
    private val playerManager = app.mediaPlayerManager
    private val spatialMonitor = app.spatialAudioMonitor
    private val findEngine = app.findEarbudsEngine

    val securityManager = SecurityCenterManager(
        context = app,
        securityPrefs = app.securityPreferencesRepository,
        repository = repository,
        scope = viewModelScope
    )

    val preferences: StateFlow<AppPreferencesState> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesState())

    val bluetoothState: StateFlow<BluetoothDashboardState> = btManager.state
    val playerState: StateFlow<PlayerUiState> = playerManager.state
    val equalizerState: StateFlow<EqualizerUiState> = EqualizerController.state
    val spatialState: StateFlow<SpatialAudioStatus> = spatialMonitor.status
    val isLocatorPlaying: StateFlow<Boolean> = findEngine.isPlaying
    val locatorChannel: StateFlow<LocatorChannel> = findEngine.activeChannel

    val deviceHistory: StateFlow<List<DeviceHistoryEntity>> = repository.deviceHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deviceProfiles: StateFlow<List<DeviceProfileEntity>> = repository.deviceProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val batteryLogs: StateFlow<List<BatteryHistoryEntity>> = repository.batteryLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissionSnapshot = MutableStateFlow(PermissionManager.snapshot(app))
    val permissionSnapshot: StateFlow<PermissionStatusSnapshot> = _permissionSnapshot.asStateFlow()

    private val _isConnectionPopupVisible = MutableStateFlow(false)
    val isConnectionPopupVisible: StateFlow<Boolean> = _isConnectionPopupVisible.asStateFlow()

    private val _uiActions = MutableSharedFlow<MainUiActionEvent>(extraBufferCapacity = 16)
    val uiActions: SharedFlow<MainUiActionEvent> = _uiActions.asSharedFlow()

    private var lastLowBatteryAlertTimestamp = 0L

    init {
        btManager.start()
        playerManager.connect()
        spatialMonitor.start()

        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                val presetGains = resolvePresetGains(prefs.selectedEqPreset, prefs.customBandGainsDb)
                EqualizerController.applySettings(
                    enabled = prefs.eqEnabled,
                    presetName = prefs.selectedEqPreset,
                    gainsDb = presetGains
                )
                val soundUri = prefs.customAlertSoundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                BudsNotificationManager.ensureChannels(app, soundUri)
            }
        }

        viewModelScope.launch {
            repository.customEqPresets.collect { entities ->
                val custom = entities.mapNotNull { entity ->
                    val vals = entity.gainsDbCsv.split(",").mapNotNull { it.toFloatOrNull() }
                    if (vals.size == 10) EqPreset(entity.name, vals, isBuiltIn = false) else null
                }
                EqualizerController.updateCustomPresets(custom)
            }
        }

        viewModelScope.launch {
            btManager.events.collect { event ->
                when (event) {
                    is BluetoothUiEvent.DeviceConnected -> {
                        val dev = event.device
                        securityManager.evaluateConnectedDeviceSecurity(dev)
                        repository.recordDeviceConnection(
                            address = dev.address,
                            name = dev.name,
                            brand = dev.brand,
                            firmwareVersion = dev.firmwareRevision,
                            supportsFastPair = dev.supportsFastPair
                        )
                        if (dev.batteryTelemetry.hasAnyBatteryValue) {
                            repository.logBatterySnapshot(dev.address, dev.name, dev.batteryTelemetry)
                        }
                        val prefs = preferences.value
                        if (prefs.showInAppConnectionPopup) {
                            _isConnectionPopupVisible.value = true
                        }
                        val batterySummary = buildBatteryText(dev.batteryTelemetry)
                        val shownOverlay = ConnectionOverlayService.showOrFallback(
                            context = app,
                            allowOverlay = prefs.overlayPopupEnabled,
                            deviceName = dev.name,
                            statusText = "Connected",
                            batterySummary = batterySummary
                        )
                        if (prefs.connectionNotificationsEnabled && !shownOverlay) {
                            BudsNotificationManager.showConnectionNotification(
                                context = app,
                                deviceName = dev.name,
                                connected = true,
                                battery = dev.batteryTelemetry
                            )
                        }
                    }

                    is BluetoothUiEvent.DeviceDisconnected -> {
                        val prefs = preferences.value
                        if (prefs.connectionNotificationsEnabled) {
                            BudsNotificationManager.showConnectionNotification(
                                context = app,
                                deviceName = event.deviceName,
                                connected = false,
                                battery = null
                            )
                        }
                    }

                    is BluetoothUiEvent.LowBatteryDetected -> {
                        val prefs = preferences.value
                        val now = System.currentTimeMillis()
                        if (prefs.lowBatteryAlertsEnabled && now - lastLowBatteryAlertTimestamp > 600_000L) {
                            lastLowBatteryAlertTimestamp = now
                            BudsNotificationManager.showLowBatteryNotification(
                                context = app,
                                deviceName = event.device.name,
                                percent = event.percent
                            )
                        }
                    }

                    is BluetoothUiEvent.FastPairChooserReady -> {
                        _uiActions.tryEmit(MainUiActionEvent.LaunchCompanionIntentSender(event.intentSender))
                    }

                    is BluetoothUiEvent.InfoMessage -> {
                        _uiActions.tryEmit(MainUiActionEvent.ShowSnackbar(event.message))
                    }
                }
            }
        }

        // Observe battery telemetry changes on active device to persist history & trigger low-battery alerts
        viewModelScope.launch {
            var lastLoggedMillis = 0L
            btManager.state.collect { st ->
                val active = st.activeDevice
                if (active != null && active.batteryTelemetry.hasAnyBatteryValue) {
                    val ts = active.batteryTelemetry.lastUpdatedMillis
                    if (ts != lastLoggedMillis) {
                        lastLoggedMillis = ts
                        repository.logBatterySnapshot(active.address, active.name, active.batteryTelemetry)
                        val lowest = active.batteryTelemetry.lowestAvailablePercent
                        val prefs = preferences.value
                        if (lowest != null && lowest <= prefs.lowBatteryThreshold && prefs.lowBatteryAlertsEnabled) {
                            val now = System.currentTimeMillis()
                            if (now - lastLowBatteryAlertTimestamp > 600_000L) {
                                lastLowBatteryAlertTimestamp = now
                                BudsNotificationManager.showLowBatteryNotification(app, active.name, lowest)
                            }
                        }
                    }
                }
            }
        }
    }

    fun refreshAllHardwareStates() {
        _permissionSnapshot.value = PermissionManager.snapshot(app)
        btManager.refreshPairedDevices()
        spatialMonitor.refresh()
        playerManager.refreshVolume()
        securityManager.refreshCapabilities()
    }

    fun showConnectionPopup() {
        _isConnectionPopupVisible.value = true
    }

    fun dismissConnectionPopup() {
        _isConnectionPopupVisible.value = false
    }

    fun startBleScan() {
        _permissionSnapshot.value = PermissionManager.snapshot(app)
        btManager.startBleScan()
    }

    fun stopBleScan() = btManager.stopBleScan()

    fun launchFastPairCompanionChooser(filterOnePlusOrFastPair: Boolean = true) {
        btManager.launchCompanionDeviceAssociation(filterOnePlusOrFastPair)
    }

    fun bondScannedDevice(address: String) {
        btManager.initiateBond(address)
    }

    fun inspectGatt(address: String) {
        btManager.inspectGatt(address, silent = false)
    }

    fun closeGatt() {
        btManager.closeGatt()
    }

    fun reconnectOrOpenBluetoothSettings(address: String?) {
        if (address != null) {
            btManager.attemptReconnectOrOpenSettings(address)
        } else {
            PermissionManager.openBluetoothSettings(app)
        }
    }

    fun openSystemBluetoothSettings() {
        PermissionManager.openBluetoothSettings(app)
    }

    fun launchBrandCompanionApp(appName: String, candidatePackages: List<String>) {
        val result: CompanionAppLaunchResult = CompanionAppLauncher.launchOrOpenStore(
            context = app,
            appDisplayName = appName,
            candidatePackages = candidatePackages
        )
        _uiActions.tryEmit(MainUiActionEvent.ShowSnackbar(result.message))
    }

    fun previewExternalPopup() {
        val active = bluetoothState.value.activeDevice
        val name = active?.name ?: "OnePlus Nord Buds 3 Pro"
        val status = if (active != null) "Connected" else "Ready to Connect"
        val batteryText = active?.batteryTelemetry?.let(::buildBatteryText)
            ?: "Connect earbuds in Bluetooth Settings for live telemetry"

        val prefs = preferences.value
        val shownOverlay = ConnectionOverlayService.showOrFallback(
            context = app,
            allowOverlay = prefs.overlayPopupEnabled,
            deviceName = name,
            statusText = status,
            batterySummary = batteryText
        )
        if (!shownOverlay) {
            BudsNotificationManager.showConnectionNotification(
                context = app,
                deviceName = name,
                connected = active != null,
                battery = active?.batteryTelemetry
            )
            _uiActions.tryEmit(
                MainUiActionEvent.ShowSnackbar("Sent Connection Card Notification (overlay unavailable or disabled).")
            )
        } else {
            _uiActions.tryEmit(MainUiActionEvent.ShowSnackbar("Displayed system overlay connection card."))
        }
    }

    // Equalizer controls
    fun setEqEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = preferences.value
            prefsRepo.setEqState(enabled, current.selectedEqPreset, current.customBandGainsDb)
        }
    }

    fun selectEqPreset(presetName: String) {
        viewModelScope.launch {
            val customFromDb = equalizerState.value.customPresets.firstOrNull { it.name == presetName }
            val gains = customFromDb?.gainsDb ?: resolvePresetGains(presetName, preferences.value.customBandGainsDb)
            prefsRepo.setEqState(preferences.value.eqEnabled, presetName, gains)
        }
    }

    fun updateEqBand(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..9) return
        viewModelScope.launch {
            val currentGains = equalizerState.value.bandGainsDb.toMutableList()
            if (currentGains.size != 10) return@launch
            currentGains[bandIndex] = gainDb.coerceIn(-12f, 12f)
            prefsRepo.setEqState(preferences.value.eqEnabled, "Custom", currentGains)
        }
    }

    fun saveCustomEqPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val gains = equalizerState.value.bandGainsDb
            repository.saveCustomEqPreset(trimmed, gains)
            prefsRepo.setEqState(preferences.value.eqEnabled, trimmed, gains)
            _uiActions.tryEmit(MainUiActionEvent.ShowSnackbar("Saved EQ preset \"$trimmed\""))
        }
    }

    fun deleteCustomEqPreset(name: String) {
        viewModelScope.launch {
            repository.deleteCustomEqPreset(name)
            if (preferences.value.selectedEqPreset == name) {
                prefsRepo.setEqState(preferences.value.eqEnabled, "Normal", List(10) { 0f })
            }
        }
    }

    // Media3 Player controls
    fun addAudioFiles(uris: List<Uri>) = playerManager.addLocalAudioUris(uris)
    fun playTrackAt(index: Int) = playerManager.playTrackAt(index)
    fun togglePlayPause() = playerManager.togglePlayPause()
    fun nextTrack() = playerManager.nextTrack()
    fun previousTrack() = playerManager.previousTrack()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun setVolumeFraction(fraction: Float) = playerManager.setVolumeFraction(fraction)

    // Spatial Audio
    fun refreshSpatialAudio() = spatialMonitor.refresh()
    fun openSystemSoundSettings() = spatialMonitor.openSystemSoundSettings()

    // Find Earbuds
    fun toggleFindEarbuds(channel: LocatorChannel) {
        if (findEngine.isPlaying.value && findEngine.activeChannel.value == channel) {
            findEngine.stop()
        } else {
            findEngine.startLocatorTone(channel)
        }
    }

    fun stopFindEarbuds() = findEngine.stop()

    // Device History & Profiles
    fun toggleFavoriteDevice(address: String, favorite: Boolean) {
        viewModelScope.launch { repository.toggleFavoriteDevice(address, favorite) }
    }

    fun saveDeviceProfile(
        profileName: String,
        brand: EarbudsBrand,
        deviceAddress: String?,
        eqPreset: String,
        spatialPreferred: Boolean,
        notes: String
    ) {
        if (profileName.isBlank()) return
        viewModelScope.launch {
            repository.saveProfile(
                DeviceProfileEntity(
                    profileName = profileName.trim(),
                    brandName = brand.displayName,
                    deviceAddress = deviceAddress,
                    preferredEqPreset = eqPreset,
                    spatialAudioPreferred = spatialPreferred,
                    notes = notes.trim()
                )
            )
            _uiActions.tryEmit(MainUiActionEvent.ShowSnackbar("Saved device profile \"$profileName\""))
        }
    }

    fun deleteDeviceProfile(id: Int) {
        viewModelScope.launch { repository.deleteProfile(id) }
    }

    // Settings & Privacy
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setInAppPopup(enabled: Boolean) = viewModelScope.launch { prefsRepo.setInAppPopup(enabled) }
    fun setConnectionNotifications(enabled: Boolean) = viewModelScope.launch { prefsRepo.setConnectionNotifications(enabled) }
    fun setOverlayPopup(enabled: Boolean) = viewModelScope.launch { prefsRepo.setOverlayPopup(enabled) }
    fun setLowBatteryAlerts(enabled: Boolean) = viewModelScope.launch { prefsRepo.setLowBatteryAlerts(enabled) }
    fun setLowBatteryThreshold(percent: Int) = viewModelScope.launch { prefsRepo.setLowBatteryThreshold(percent) }
    fun setAutoReconnectHelper(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoReconnectHelper(enabled) }
    fun setCustomAlertSoundUri(uri: Uri?) = viewModelScope.launch {
        prefsRepo.setCustomAlertSoundUri(uri?.toString())
        BudsNotificationManager.ensureChannels(app, uri)
    }
    fun setLocalOnlyPrivacy(enabled: Boolean) = viewModelScope.launch { prefsRepo.setLocalOnlyPrivacy(enabled) }
    fun clearAllLocalLogs() = viewModelScope.launch {
        repository.clearAllLocalLogs()
        _uiActions.tryEmit(MainUiActionEvent.ShowSnackbar("Cleared local device & battery history."))
    }

    private fun resolvePresetGains(presetName: String, customFallback: List<Float>): List<Float> {
        if (presetName == "Custom") return customFallback
        return EqualizerCatalog.builtInPresets.firstOrNull { it.name == presetName }?.gainsDb ?: customFallback
    }

    private fun buildBatteryText(telemetry: com.example.devices.BatteryTelemetry): String {
        if (!telemetry.hasAnyBatteryValue) return "Battery telemetry not exposed by device"
        return buildList {
            telemetry.leftPercent?.let { add("Left $it%") }
            telemetry.rightPercent?.let { add("Right $it%") }
            telemetry.casePercent?.let { add("Case $it%") }
            if (telemetry.leftPercent == null && telemetry.rightPercent == null) {
                telemetry.overallPercent?.let { add("Battery $it%") }
            }
        }.joinToString(" · ")
    }

    override fun onCleared() {
        findEngine.stop()
        spatialMonitor.stop()
        playerManager.disconnect()
        btManager.stop()
        super.onCleared()
    }
}
