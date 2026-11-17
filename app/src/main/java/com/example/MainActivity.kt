package com.example

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.PermissionManager
import com.example.security.BiometricAuthHelper
import com.example.ui.components.ConnectionPopupSheet
import com.example.ui.components.ObsidianBackdrop
import com.example.ui.navigation.BudsRoute
import com.example.ui.screens.BatteryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.SecurityCenterScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashOverlay
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.HardikBudsProTheme
import com.example.ui.viewmodel.BudsProViewModel
import com.example.ui.viewmodel.MainUiActionEvent
import kotlinx.coroutines.launch

private data class NavTab(
    val route: BudsRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : FragmentActivity() {

    private val viewModel: BudsProViewModel by viewModels()
    private var securityDeepLinkTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra("open_popup", false) == true) {
            viewModel.showConnectionPopup()
        }
        if (intent?.getBooleanExtra("open_security", false) == true) {
            securityDeepLinkTrigger++
        }

        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()

            HardikBudsProTheme(themeMode = prefs.themeMode) {
                BudsProAppShell(
                    activity = this,
                    viewModel = viewModel,
                    securityDeepLinkTrigger = securityDeepLinkTrigger
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAllHardwareStates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_popup", false)) {
            viewModel.showConnectionPopup()
        }
        if (intent.getBooleanExtra("open_security", false)) {
            securityDeepLinkTrigger++
        }
    }
}

@Composable
private fun BudsProAppShell(
    activity: FragmentActivity,
    viewModel: BudsProViewModel,
    securityDeepLinkTrigger: Int
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    var showSplash by rememberSaveable { mutableStateOf(true) }

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val btState by viewModel.bluetoothState.collectAsStateWithLifecycle()
    val perms by viewModel.permissionSnapshot.collectAsStateWithLifecycle()
    val eqState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val spatialState by viewModel.spatialState.collectAsStateWithLifecycle()
    val isLocatorPlaying by viewModel.isLocatorPlaying.collectAsStateWithLifecycle()
    val locatorChannel by viewModel.locatorChannel.collectAsStateWithLifecycle()
    val deviceHistory by viewModel.deviceHistory.collectAsStateWithLifecycle()
    val deviceProfiles by viewModel.deviceProfiles.collectAsStateWithLifecycle()
    val batteryLogs by viewModel.batteryLogs.collectAsStateWithLifecycle()
    val popupVisible by viewModel.isConnectionPopupVisible.collectAsStateWithLifecycle()

    val vaultState by viewModel.securityManager.vaultState.collectAsStateWithLifecycle()
    val securityUi by viewModel.securityManager.uiState.collectAsStateWithLifecycle()
    val trustedDevices by viewModel.securityManager.trustedDevices.collectAsStateWithLifecycle()
    val securityEvents by viewModel.securityManager.securityEvents.collectAsStateWithLifecycle()

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshAllHardwareStates()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshAllHardwareStates()
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshAllHardwareStates()
        if (PermissionManager.snapshot(activity).overlayGranted) {
            viewModel.setOverlayPopup(true)
        }
    }

    val companionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshAllHardwareStates()
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addAudioFiles(uris)
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            viewModel.setCustomAlertSoundUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiActions.collect { action ->
            when (action) {
                is MainUiActionEvent.LaunchCompanionIntentSender -> {
                    companionLauncher.launch(IntentSenderRequest.Builder(action.intentSender).build())
                }
                is MainUiActionEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(action.message)
                }
            }
        }
    }

    val compactTabs = remember {
        listOf(
            NavTab(BudsRoute.Dashboard, "Hub", Icons.Rounded.Headphones, Icons.Outlined.Headphones, "nav_hub"),
            NavTab(BudsRoute.Devices, "Devices", Icons.Rounded.Bluetooth, Icons.Outlined.Bluetooth, "nav_devices"),
            NavTab(BudsRoute.Studio, "Studio", Icons.Rounded.Equalizer, Icons.Outlined.Equalizer, "nav_studio"),
            NavTab(BudsRoute.Security, "Security", Icons.Rounded.Security, Icons.Outlined.Security, "nav_security"),
            NavTab(BudsRoute.Settings, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val railTabs = remember {
        listOf(
            NavTab(BudsRoute.Dashboard, "Hub", Icons.Rounded.Headphones, Icons.Outlined.Headphones, "nav_hub"),
            NavTab(BudsRoute.Devices, "Devices", Icons.Rounded.Bluetooth, Icons.Outlined.Bluetooth, "nav_devices"),
            NavTab(BudsRoute.Studio, "Studio", Icons.Rounded.Equalizer, Icons.Outlined.Equalizer, "nav_studio"),
            NavTab(BudsRoute.Battery, "Battery", Icons.Rounded.BatteryStd, Icons.Outlined.BatteryStd, "nav_battery"),
            NavTab(BudsRoute.Security, "Security", Icons.Rounded.Security, Icons.Outlined.Security, "nav_security"),
            NavTab(BudsRoute.Settings, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    fun navigateToTab(route: BudsRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(securityDeepLinkTrigger) {
        if (securityDeepLinkTrigger > 0) {
            navigateToTab(BudsRoute.Security)
        }
    }

    ObsidianBackdrop {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            compactTabs.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                        ) {
                            Spacer(modifier = Modifier.weight(0.1f))
                            railTabs.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = BudsRoute.Dashboard
                        ) {
                            composable<BudsRoute.Dashboard> {
                                DashboardScreen(
                                    bluetoothState = btState,
                                    permissions = perms,
                                    equalizerState = eqState,
                                    spatialState = spatialState,
                                    playerState = playerState,
                                    isLocatorPlaying = isLocatorPlaying,
                                    locatorChannel = locatorChannel,
                                    onRequestPermissions = {
                                        btPermissionLauncher.launch(PermissionManager.requiredBluetoothPermissions())
                                    },
                                    onShowPopup = viewModel::showConnectionPopup,
                                    onPreviewExternalPopup = viewModel::previewExternalPopup,
                                    onFastPair = { viewModel.launchFastPairCompanionChooser(true) },
                                    onOpenBluetoothSettings = viewModel::openSystemBluetoothSettings,
                                    onOpenCompanionApp = viewModel::launchBrandCompanionApp,
                                    onToggleFindEarbuds = viewModel::toggleFindEarbuds,
                                    onNavigateToDevices = { navigateToTab(BudsRoute.Devices) },
                                    onNavigateToStudio = { navigateToTab(BudsRoute.Studio) },
                                    onNavigateToBattery = { navigateToTab(BudsRoute.Battery) },
                                    vaultState = vaultState,
                                    securityUi = securityUi,
                                    onNavigateToSecurity = { navigateToTab(BudsRoute.Security) }
                                )
                            }

                            composable<BudsRoute.Devices> {
                                DevicesScreen(
                                    bluetoothState = btState,
                                    permissions = perms,
                                    deviceHistory = deviceHistory,
                                    deviceProfiles = deviceProfiles,
                                    onRequestPermissions = {
                                        btPermissionLauncher.launch(PermissionManager.requiredBluetoothPermissions())
                                    },
                                    onStartScan = viewModel::startBleScan,
                                    onStopScan = viewModel::stopBleScan,
                                    onLaunchFastPair = { viewModel.launchFastPairCompanionChooser(true) },
                                    onOpenBluetoothSettings = viewModel::openSystemBluetoothSettings,
                                    onBondDevice = viewModel::bondScannedDevice,
                                    onInspectGatt = viewModel::inspectGatt,
                                    onCloseGatt = viewModel::closeGatt,
                                    onToggleFavorite = viewModel::toggleFavoriteDevice,
                                    onSaveProfile = viewModel::saveDeviceProfile,
                                    onDeleteProfile = viewModel::deleteDeviceProfile
                                )
                            }

                            composable<BudsRoute.Studio> {
                                StudioScreen(
                                    bluetoothState = btState,
                                    equalizerState = eqState,
                                    playerState = playerState,
                                    spatialState = spatialState,
                                    onToggleEq = viewModel::setEqEnabled,
                                    onSelectPreset = viewModel::selectEqPreset,
                                    onUpdateEqBand = viewModel::updateEqBand,
                                    onSaveCustomPreset = viewModel::saveCustomEqPreset,
                                    onPickAudioFiles = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                    onPlayTrackAt = viewModel::playTrackAt,
                                    onTogglePlayPause = viewModel::togglePlayPause,
                                    onPrevious = viewModel::previousTrack,
                                    onNext = viewModel::nextTrack,
                                    onSeekTo = viewModel::seekTo,
                                    onVolumeChange = viewModel::setVolumeFraction,
                                    onRefreshSpatial = viewModel::refreshSpatialAudio,
                                    onOpenSoundSettings = viewModel::openSystemSoundSettings,
                                    onOpenCompanionApp = viewModel::launchBrandCompanionApp
                                )
                            }

                            composable<BudsRoute.Battery> {
                                BatteryScreen(
                                    bluetoothState = btState,
                                    batteryLogs = batteryLogs,
                                    preferences = prefs,
                                    onRefreshBattery = {
                                        btState.activeDevice?.address?.let(viewModel::inspectGatt)
                                            ?: viewModel.refreshAllHardwareStates()
                                    },
                                    onToggleLowBatteryAlert = viewModel::setLowBatteryAlerts,
                                    onThresholdChange = viewModel::setLowBatteryThreshold
                                )
                            }

                            composable<BudsRoute.Security> {
                                val sec = viewModel.securityManager
                                SecurityCenterScreen(
                                    vaultState = vaultState,
                                    securityUi = securityUi,
                                    bluetoothState = btState,
                                    trustedDevices = trustedDevices,
                                    securityEvents = securityEvents,
                                    onRegisterOwner = { name, pin, confirmPin, pinLen ->
                                        sec.registerOwner(
                                            displayName = name,
                                            pin = pin,
                                            confirmPin = confirmPin,
                                            pinLength = pinLen,
                                            initialPrimaryDevice = btState.activeDevice
                                                ?: btState.pairedDevices.firstOrNull()
                                        )
                                    },
                                    onUnlockWithPin = sec::unlockWithPin,
                                    onTriggerBiometricUnlock = {
                                        BiometricAuthHelper.authenticate(
                                            activity = activity,
                                            title = "Unlock HARDIK BUDS PRO Security",
                                            subtitle = "Authenticate with Fingerprint or Biometric",
                                            onSucceeded = { sec.markBiometricAuthenticated() },
                                            onErrorOrCancelled = { msg ->
                                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        )
                                    },
                                    onLockNow = sec::lockSecurityCenter,
                                    onSetLockSensitive = sec::setLockSensitiveSettings,
                                    onSetBiometricEnabled = sec::setBiometricEnabled,
                                    onSetUnauthorizedAlerts = sec::setUnauthorizedAlertsEnabled,
                                    onChangePin = sec::changePin,
                                    onResetPinWithRecovery = sec::resetPinWithRecoveryKey,
                                    onRotateRecoveryKey = sec::regenerateRecoveryKeyWithVerification,
                                    onAddTrustedDevice = { dev, pin, bio ->
                                        sec.addTrustedDeviceWithVerification(
                                            address = dev.address,
                                            deviceName = dev.name,
                                            brandName = dev.brand.displayName,
                                            isPrimaryBuds = dev.brand.displayName.contains("OnePlus", ignoreCase = true),
                                            pinInput = pin,
                                            biometricVerified = bio
                                        )
                                    },
                                    onRemoveTrustedDevice = { td, pin, bio ->
                                        sec.removeTrustedDeviceWithVerification(
                                            address = td.address,
                                            deviceName = td.deviceName,
                                            pinInput = pin,
                                            biometricVerified = bio
                                        )
                                    },
                                    onTransferOwnership = sec::transferOwnershipWithVerification,
                                    onToggleLostMode = { enable, note, pin, bio ->
                                        val active = btState.activeDevice
                                        val histLast = deviceHistory.firstOrNull()
                                        val targetName = active?.name ?: histLast?.name ?: "OnePlus Buds 3 Pro"
                                        val targetAddr = active?.address ?: histLast?.address
                                        val lastSeen = active?.batteryTelemetry?.lastUpdatedMillis
                                            ?: histLast?.lastConnectedAt
                                            ?: System.currentTimeMillis()
                                        val batterySummary = active?.batteryTelemetry?.let { b ->
                                            buildList {
                                                b.leftPercent?.let { add("L $it%") }
                                                b.rightPercent?.let { add("R $it%") }
                                                b.casePercent?.let { add("Case $it%") }
                                                if (isEmpty()) b.overallPercent?.let { add("$it%") }
                                            }.joinToString(" · ").ifBlank { null }
                                        }
                                        sec.setLostMode(
                                            enable = enable,
                                            targetName = targetName,
                                            targetAddress = targetAddr,
                                            lastSeenAt = lastSeen,
                                            batterySummary = batterySummary,
                                            note = note,
                                            pinInput = pin,
                                            biometricVerified = bio
                                        )
                                    },
                                    onTriggerFindChime = viewModel::toggleFindEarbuds,
                                    onDismissUnauthorizedBanner = sec::dismissLatestUnauthorizedAlert,
                                    onClearDisplayedKeys = sec::clearDisplayedKeys,
                                    onClearFeedback = sec::clearFeedback,
                                    onClearSecurityHistory = sec::clearSecurityHistory,
                                    onRequestBiometricForApproval = { onResult ->
                                        BiometricAuthHelper.authenticate(
                                            activity = activity,
                                            title = "Approve Sensitive Action",
                                            subtitle = "Verify Owner identity via BiometricPrompt",
                                            onSucceeded = { onResult(true) },
                                            onErrorOrCancelled = { msg ->
                                                onResult(false)
                                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        )
                                    }
                                )
                            }

                            composable<BudsRoute.Settings> {
                                SettingsScreen(
                                    context = activity,
                                    preferences = prefs,
                                    permissions = perms,
                                    onSetThemeMode = viewModel::setThemeMode,
                                    onSetInAppPopup = viewModel::setInAppPopup,
                                    onSetConnectionNotifications = viewModel::setConnectionNotifications,
                                    onSetOverlayPopup = viewModel::setOverlayPopup,
                                    onSetAutoReconnectHelper = viewModel::setAutoReconnectHelper,
                                    onPickCustomAlertSound = {
                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Connection Alert Sound")
                                        }
                                        ringtonePickerLauncher.launch(intent)
                                    },
                                    onClearCustomAlertSound = { viewModel.setCustomAlertSoundUri(null) },
                                    onSetLocalOnlyPrivacy = viewModel::setLocalOnlyPrivacy,
                                    onClearLocalLogs = viewModel::clearAllLocalLogs,
                                    onRequestBluetoothPermissions = {
                                        btPermissionLauncher.launch(PermissionManager.requiredBluetoothPermissions())
                                    },
                                    onRequestNotificationPermission = {
                                        val req = PermissionManager.requiredNotificationPermissions()
                                        if (req.isNotEmpty()) notificationPermissionLauncher.launch(req)
                                    },
                                    onRequestOverlayPermission = {
                                        overlaySettingsLauncher.launch(PermissionManager.buildOverlayPermissionIntent(activity))
                                    },
                                    vaultState = vaultState,
                                    isSecurityUnlocked = securityUi.isSecurityCenterUnlocked,
                                    onNavigateToSecurity = { navigateToTab(BudsRoute.Security) }
                                )
                            }
                        }
                    }
                }
            }

            ConnectionPopupSheet(
                visible = popupVisible,
                bluetoothState = btState,
                onDismiss = viewModel::dismissConnectionPopup,
                onConnectOrSettings = {
                    viewModel.reconnectOrOpenBluetoothSettings(btState.activeDevice?.address)
                },
                onFastPair = { viewModel.launchFastPairCompanionChooser(true) },
                onOpenCompanionApp = viewModel::launchBrandCompanionApp,
                onInspectGatt = { address -> viewModel.inspectGatt(address) }
            )

            if (showSplash) {
                SplashOverlay(onFinished = { showSplash = false })
            }
        }
    }
}
