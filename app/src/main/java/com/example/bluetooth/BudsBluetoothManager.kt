package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.core.AppConstants
import com.example.core.PermissionManager
import com.example.devices.BatterySource
import com.example.devices.BatteryTelemetry
import com.example.devices.ConnectionState
import com.example.integrations.EarbudsIntegrationRegistry
import com.example.integrations.OnePlusNordBuds3ProIntegration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface BluetoothUiEvent {
    data class DeviceConnected(val device: PairedAudioDevice) : BluetoothUiEvent
    data class DeviceDisconnected(val deviceName: String, val address: String) : BluetoothUiEvent
    data class LowBatteryDetected(val device: PairedAudioDevice, val percent: Int) : BluetoothUiEvent
    data class FastPairChooserReady(val intentSender: IntentSender) : BluetoothUiEvent
    data class InfoMessage(val message: String) : BluetoothUiEvent
}

@SuppressLint("MissingPermission")
class BudsBluetoothManager(
    private val context: Context
) {
    private val tag = "BudsBluetoothManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var a2dpProxy: BluetoothA2dp? = null
    private var headsetProxy: BluetoothHeadset? = null
    private var activeGatt: BluetoothGatt? = null
    private val gattReadQueue = ArrayDeque<BluetoothGattCharacteristic>()

    private val batteryCache = ConcurrentHashMap<String, BatteryTelemetry>()
    private val firmwareCache = ConcurrentHashMap<String, Triple<String?, String?, String?>>() // fw, model, mfg
    private val fastPairSeenAddresses = ConcurrentHashMap<String, Boolean>()

    private val _state = MutableStateFlow(
        BluetoothDashboardState(
            isBluetoothSupported = bluetoothAdapter != null,
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
        )
    )
    val state: StateFlow<BluetoothDashboardState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BluetoothUiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BluetoothUiEvent> = _events.asSharedFlow()

    private var receiversRegistered = false
    private var scanStopRunnable: Runnable? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy as? BluetoothA2dp
                BluetoothProfile.HEADSET -> headsetProxy = proxy as? BluetoothHeadset
            }
            refreshPairedDevices()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
            refreshPairedDevices()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val enabled = btState == BluetoothAdapter.STATE_ON
                    _state.update { it.copy(isBluetoothEnabled = enabled) }
                    if (enabled) refreshPairedDevices() else clearConnectedState()
                }

                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val device = extractBluetoothDevice(intent)
                    val stateExtra = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_CONNECTED
                    )
                    if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED ||
                        stateExtra == BluetoothProfile.STATE_CONNECTED
                    ) {
                        _state.update {
                            it.copy(
                                connectionState = ConnectionState.CONNECTED,
                                lastConnectionEventMessage = "Connected: ${safeDeviceName(device)}"
                            )
                        }
                        refreshPairedDevices()
                        device?.let { dev ->
                            val paired = buildPairedModel(dev)
                            _events.tryEmit(BluetoothUiEvent.DeviceConnected(paired))
                            // Auto-read standard GATT Battery/Device Info if BLE connectable
                            if (PermissionManager.snapshot(context).bluetoothConnectGranted) {
                                inspectGatt(dev.address, silent = true)
                            }
                        }
                    } else if (stateExtra == BluetoothProfile.STATE_DISCONNECTED) {
                        refreshPairedDevices()
                        device?.let {
                            _events.tryEmit(
                                BluetoothUiEvent.DeviceDisconnected(
                                    deviceName = safeDeviceName(it),
                                    address = it.address
                                )
                            )
                        }
                    } else if (stateExtra == BluetoothProfile.STATE_CONNECTING) {
                        _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = extractBluetoothDevice(intent)
                    refreshPairedDevices()
                    if (device != null) {
                        _events.tryEmit(
                            BluetoothUiEvent.DeviceDisconnected(
                                deviceName = safeDeviceName(device),
                                address = device.address
                            )
                        )
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshPairedDevices()
                }

                AppConstants.ACTION_BATTERY_LEVEL_CHANGED -> {
                    val device = extractBluetoothDevice(intent)
                    val level = intent.getIntExtra(AppConstants.EXTRA_BATTERY_LEVEL, -1)
                    if (device != null && level in 0..100) {
                        val existing = batteryCache[device.address] ?: BatteryTelemetry()
                        batteryCache[device.address] = existing.copy(
                            overallPercent = level,
                            source = if (existing.source == BatterySource.UNAVAILABLE) {
                                BatterySource.ANDROID_BATTERY_BROADCAST
                            } else existing.source,
                            lastUpdatedMillis = System.currentTimeMillis()
                        )
                        refreshPairedDevices()
                    }
                }

                BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> {
                    val device = extractBluetoothDevice(intent)
                    val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
                    val args = intent.extras?.get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) as? Array<*>
                    if (device != null && cmd != null && args != null) {
                        parseHfpBatteryAtCommand(device.address, cmd, args)
                    }
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(
                    isScanning = false,
                    scanStatusMessage = "BLE scan failed (code $errorCode). Ensure Bluetooth & Location services are active."
                )
            }
        }
    }

    fun start() {
        if (receiversRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
            addAction(AppConstants.ACTION_BATTERY_LEVEL_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(bluetoothReceiver, filter)
        }
        receiversRegistered = true

        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        refreshPairedDevices()
    }

    fun stop() {
        stopBleScan()
        closeGatt()
        if (receiversRegistered) {
            runCatching { context.unregisterReceiver(bluetoothReceiver) }
            receiversRegistered = false
        }
        a2dpProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        headsetProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
    }

    fun refreshPairedDevices() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.update {
                it.copy(isBluetoothSupported = false, isBluetoothEnabled = false, pairedDevices = emptyList())
            }
            return
        }
        val snapshot = PermissionManager.snapshot(context)
        if (!snapshot.bluetoothConnectGranted) {
            _state.update {
                it.copy(
                    isBluetoothSupported = true,
                    isBluetoothEnabled = adapter.isEnabled,
                    pairedDevices = emptyList(),
                    activeDevice = null,
                    connectionState = ConnectionState.DISCONNECTED
                )
            }
            return
        }

        val bonded = runCatching { adapter.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList())
        val models = bonded
            .map { buildPairedModel(it) }
            .sortedWith(
                compareByDescending<PairedAudioDevice> { it.isConnected }
                    .thenByDescending { it.isAudioClass }
                    .thenBy { it.name.lowercase() }
            )

        val active = models.firstOrNull { it.isConnected && it.isAudioClass }
            ?: models.firstOrNull { it.isConnected }

        val activeIntegration = active?.let {
            EarbudsIntegrationRegistry.resolve(it.name, it.supportsFastPair)
        } ?: OnePlusNordBuds3ProIntegration

        val hasBatteryGatt = active?.batteryTelemetry?.source == BatterySource.BLE_GATT_BATTERY_SERVICE
        val capabilities = activeIntegration.describeCapabilities(
            deviceName = active?.name,
            hasBatteryGatt = hasBatteryGatt,
            hasFastPair = active?.supportsFastPair == true
        )
        val ancState = activeIntegration.buildAncState(
            deviceName = active?.name,
            isConnected = active != null,
            discoveredCustomControlUuid = false
        )
        val touchConfigs = activeIntegration.buildTouchConfigs(
            isConnected = active != null,
            discoveredCustomControlUuid = false
        )

        _state.update {
            it.copy(
                isBluetoothSupported = true,
                isBluetoothEnabled = adapter.isEnabled,
                connectionState = if (active != null) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
                activeDevice = active,
                activeCapabilities = capabilities,
                ancState = ancState,
                touchConfigs = touchConfigs,
                pairedDevices = models
            )
        }
    }

    fun startBleScan(durationMillis: Long = 12_000L) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update {
                it.copy(
                    isScanning = false,
                    scanStatusMessage = "Turn on Bluetooth to scan for nearby earbuds and Fast Pair beacons."
                )
            }
            return
        }
        val perms = PermissionManager.snapshot(context)
        if (!perms.hasBluetoothCorePermissions) {
            _state.update {
                it.copy(
                    isScanning = false,
                    scanStatusMessage = "Bluetooth Scan / Location permission is required before scanning."
                )
            }
            return
        }
        val scanner = bleScanner
        if (scanner == null) {
            _state.update {
                it.copy(
                    isScanning = false,
                    scanStatusMessage = "BLE scanner is unavailable on this hardware."
                )
            }
            return
        }

        stopBleScan()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        runCatching {
            scanner.startScan(null, settings, bleScanCallback)
            _state.update {
                it.copy(
                    isScanning = true,
                    scanStatusMessage = "Scanning for BLE audio & Google Fast Pair (0xFE2C) beacons..."
                )
            }
            val stopTask = Runnable { stopBleScan() }
            scanStopRunnable = stopTask
            mainHandler.postDelayed(stopTask, durationMillis)
        }.onFailure { error ->
            _state.update {
                it.copy(
                    isScanning = false,
                    scanStatusMessage = "Unable to start BLE scan: ${error.localizedMessage ?: "Permission or hardware error"}"
                )
            }
        }
    }

    fun stopBleScan() {
        scanStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scanStopRunnable = null
        val perms = PermissionManager.snapshot(context)
        if (_state.value.isScanning && perms.hasBluetoothCorePermissions && bluetoothAdapter?.isEnabled == true) {
            runCatching { bleScanner?.stopScan(bleScanCallback) }
        }
        _state.update {
            it.copy(
                isScanning = false,
                scanStatusMessage = if (it.scannedBleDevices.isEmpty() && it.scanStatusMessage?.contains("failed") != true) {
                    "Scan complete."
                } else it.scanStatusMessage
            )
        }
    }

    fun launchCompanionDeviceAssociation(filterOnePlusOrFastPair: Boolean = false) {
        val manager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
        if (manager == null) {
            _events.tryEmit(
                BluetoothUiEvent.InfoMessage(
                    "CompanionDeviceManager is unavailable on this device. Opening Bluetooth settings."
                )
            )
            PermissionManager.openBluetoothSettings(context)
            return
        }

        val builder = AssociationRequest.Builder().setSingleDevice(false)
        if (filterOnePlusOrFastPair) {
            val fastPairFilter = BluetoothLeDeviceFilter.Builder()
                .setScanFilter(
                    android.bluetooth.le.ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(AppConstants.UUID_FAST_PAIR_SERVICE))
                        .build()
                )
                .build()
            val classicFilter = BluetoothDeviceFilter.Builder().build()
            builder.addDeviceFilter(fastPairFilter)
            builder.addDeviceFilter(classicFilter)
        } else {
            builder.addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            builder.addDeviceFilter(BluetoothLeDeviceFilter.Builder().build())
        }

        val request = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.associate(
                request,
                context.mainExecutor,
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        _events.tryEmit(BluetoothUiEvent.FastPairChooserReady(intentSender))
                    }

                    override fun onFailure(error: CharSequence?) {
                        _events.tryEmit(
                            BluetoothUiEvent.InfoMessage(
                                "Nearby device pairing prompt closed (${error ?: "No device chosen"})."
                            )
                        )
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            manager.associate(
                request,
                object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        _events.tryEmit(BluetoothUiEvent.FastPairChooserReady(chooserLauncher))
                    }

                    override fun onFailure(error: CharSequence?) {
                        _events.tryEmit(
                            BluetoothUiEvent.InfoMessage(
                                "Nearby device pairing prompt closed (${error ?: "No device chosen"})."
                            )
                        )
                    }
                },
                mainHandler
            )
        }
    }

    fun initiateBond(address: String) {
        val adapter = bluetoothAdapter ?: return
        if (!PermissionManager.snapshot(context).bluetoothConnectGranted) return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            val started = runCatching { device.createBond() }.getOrDefault(false)
            _events.tryEmit(
                BluetoothUiEvent.InfoMessage(
                    if (started) "Pairing request sent to ${safeDeviceName(device)}..."
                    else "Android could not start direct pairing. Opening Bluetooth Settings."
                )
            )
            if (!started) PermissionManager.openBluetoothSettings(context)
        } else {
            inspectGatt(address, silent = false)
        }
    }

    fun attemptReconnectOrOpenSettings(address: String) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            PermissionManager.openBluetoothSettings(context)
            return
        }
        if (!PermissionManager.snapshot(context).bluetoothConnectGranted) {
            _events.tryEmit(BluetoothUiEvent.InfoMessage("Grant Bluetooth Connect permission first."))
            return
        }
        _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }
        // Inspect BLE GATT for battery/device info, and guide user to system Bluetooth settings for Classic A2DP audio reconnection
        // because Android restricts programmatic BluetoothA2dp.connect() to privileged system apps.
        inspectGatt(address, silent = false)
        _events.tryEmit(
            BluetoothUiEvent.InfoMessage(
                "BLE telemetry refresh initiated. Note: Android reserves Classic A2DP audio switching for System Bluetooth Settings."
            )
        )
        PermissionManager.openBluetoothSettings(context)
    }

    fun inspectGatt(address: String, silent: Boolean = false) {
        val adapter = bluetoothAdapter ?: return
        if (!PermissionManager.snapshot(context).bluetoothConnectGranted) {
            if (!silent) {
                _state.update {
                    it.copy(
                        gattInspection = it.gattInspection.copy(
                            statusMessage = "Bluetooth Connect permission is required for GATT inspection."
                        )
                    )
                }
            }
            return
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        closeGatt()

        val name = safeDeviceName(device)
        if (!silent) {
            _state.update {
                it.copy(
                    gattInspection = GattInspectionState(
                        targetAddress = address,
                        targetName = name,
                        isConnectingOrDiscovering = true,
                        isConnected = false,
                        services = emptyList(),
                        statusMessage = "Connecting BLE GATT to $name ($address)..."
                    )
                )
            }
        }

        activeGatt = device.connectGatt(
            context,
            false,
            createGattCallback(address, name, silent),
            BluetoothDevice.TRANSPORT_AUTO
        )
    }

    fun closeGatt() {
        gattReadQueue.clear()
        runCatching {
            activeGatt?.disconnect()
            activeGatt?.close()
        }
        activeGatt = null
    }

    private fun createGattCallback(
        address: String,
        name: String,
        silent: Boolean
    ): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!silent) {
                        _state.update {
                            it.copy(
                                gattInspection = it.gattInspection.copy(
                                    isConnected = true,
                                    isConnectingOrDiscovering = true,
                                    statusMessage = "Connected to GATT server. Discovering services..."
                                )
                            )
                        }
                    }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!silent) {
                        _state.update {
                            it.copy(
                                gattInspection = it.gattInspection.copy(
                                    isConnected = false,
                                    isConnectingOrDiscovering = false,
                                    statusMessage = "GATT disconnected (status $status). Earbuds may expose Classic A2DP only when case is closed or in standby."
                                )
                            )
                        }
                    }
                    runCatching { gatt.close() }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (!silent) {
                        _state.update {
                            it.copy(
                                gattInspection = it.gattInspection.copy(
                                    isConnectingOrDiscovering = false,
                                    statusMessage = "Service discovery failed (status $status)."
                                )
                            )
                        }
                    }
                    return
                }

                val services = gatt.services.orEmpty()
                val mappedServices = services.map { service ->
                    GattServiceInfo(
                        uuid = service.uuid.toString(),
                        label = AppConstants.resolveSigName(service.uuid),
                        isPrimary = service.type == android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY,
                        characteristics = service.characteristics.orEmpty().map { ch ->
                            val props = ch.properties
                            val propLabels = buildList {
                                if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                                if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                                if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
                                if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                                if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
                            }.joinToString(" · ")
                            GattCharacteristicInfo(
                                uuid = ch.uuid.toString(),
                                label = AppConstants.resolveSigName(ch.uuid),
                                propertiesSummary = propLabels.ifEmpty { "NONE" },
                                isReadable = props and BluetoothGattCharacteristic.PROPERTY_READ != 0
                            )
                        }
                    )
                }

                val hasFastPair = services.any { it.uuid == AppConstants.UUID_FAST_PAIR_SERVICE }
                if (hasFastPair) fastPairSeenAddresses[address] = true

                // Queue prioritized standard SIG reads (Battery 0x2A19 and Device Info 0x180A)
                gattReadQueue.clear()
                services.forEach { svc ->
                    svc.characteristics.orEmpty().forEach { ch ->
                        val readable = ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                        if (readable && (
                                svc.uuid == AppConstants.UUID_BATTERY_SERVICE ||
                                    svc.uuid == AppConstants.UUID_DEVICE_INFO_SERVICE ||
                                    !silent
                                )
                        ) {
                            if (gattReadQueue.size < 20) {
                                gattReadQueue.addLast(ch)
                            }
                        }
                    }
                }

                if (!silent) {
                    _state.update {
                        it.copy(
                            gattInspection = it.gattInspection.copy(
                                targetAddress = address,
                                targetName = name,
                                isConnectingOrDiscovering = gattReadQueue.isNotEmpty(),
                                isConnected = true,
                                services = mappedServices,
                                statusMessage = "Discovered ${mappedServices.size} GATT service(s)."
                            )
                        )
                    }
                }

                readNextQueuedCharacteristic(gatt)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    handleCharacteristicValue(gatt, characteristic, characteristic.value, status)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                handleCharacteristicValue(gatt, characteristic, value, status)
            }
        }
    }

    private fun readNextQueuedCharacteristic(gatt: BluetoothGatt) {
        val next = gattReadQueue.removeFirstOrNull()
        if (next == null) {
            _state.update {
                it.copy(
                    gattInspection = it.gattInspection.copy(isConnectingOrDiscovering = false)
                )
            }
            return
        }
        val started = runCatching { gatt.readCharacteristic(next) }.getOrDefault(false)
        if (!started) {
            readNextQueuedCharacteristic(gatt)
        }
    }

    private fun handleCharacteristicValue(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        bytes: ByteArray?,
        status: Int
    ) {
        val address = gatt.device.address
        if (status == BluetoothGatt.GATT_SUCCESS && bytes != null && bytes.isNotEmpty()) {
            val uuid = characteristic.uuid
            val hex = bytes.joinToString(" ") { "%02X".format(it) }
            val decoded = decodeCharacteristicValue(uuid, bytes)

            when (uuid) {
                AppConstants.UUID_BATTERY_LEVEL -> {
                    val pct = (bytes[0].toInt() and 0xFF).coerceIn(0, 100)
                    val prev = batteryCache[address] ?: BatteryTelemetry()
                    batteryCache[address] = prev.copy(
                        overallPercent = pct,
                        source = BatterySource.BLE_GATT_BATTERY_SERVICE,
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                    refreshPairedDevices()
                }
                AppConstants.UUID_FIRMWARE_REVISION -> {
                    val existing = firmwareCache[address] ?: Triple(null, null, null)
                    firmwareCache[address] = Triple(decoded, existing.second, existing.third)
                    refreshPairedDevices()
                }
                AppConstants.UUID_MODEL_NUMBER -> {
                    val existing = firmwareCache[address] ?: Triple(null, null, null)
                    firmwareCache[address] = Triple(existing.first, decoded, existing.third)
                    refreshPairedDevices()
                }
                AppConstants.UUID_MANUFACTURER_NAME -> {
                    val existing = firmwareCache[address] ?: Triple(null, null, null)
                    firmwareCache[address] = Triple(existing.first, existing.second, decoded)
                    refreshPairedDevices()
                }
            }

            _state.update { current ->
                val updatedServices = current.gattInspection.services.map { svc ->
                    svc.copy(
                        characteristics = svc.characteristics.map { ch ->
                            if (ch.uuid.equals(uuid.toString(), ignoreCase = true)) {
                                ch.copy(decodedValue = decoded, rawHex = hex)
                            } else ch
                        }
                    )
                }
                current.copy(
                    gattInspection = current.gattInspection.copy(services = updatedServices)
                )
            }
        }
        readNextQueuedCharacteristic(gatt)
    }

    private fun decodeCharacteristicValue(uuid: UUID, bytes: ByteArray): String {
        return when (uuid) {
            AppConstants.UUID_BATTERY_LEVEL -> "${bytes[0].toInt() and 0xFF}%"
            AppConstants.UUID_MANUFACTURER_NAME,
            AppConstants.UUID_MODEL_NUMBER,
            AppConstants.UUID_FIRMWARE_REVISION,
            AppConstants.UUID_HARDWARE_REVISION,
            AppConstants.UUID_SOFTWARE_REVISION -> bytes.decodeToString().trim('\u0000', ' ')
            else -> {
                val printable = bytes.all { it in 32..126 }
                if (printable) bytes.decodeToString() else "${bytes.size} bytes"
            }
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val deviceName = safeDeviceName(device, record?.deviceName)
        val serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
        val fastPairServiceData = record?.getServiceData(ParcelUuid(AppConstants.UUID_FAST_PAIR_SERVICE))
        val isFastPair = fastPairServiceData != null ||
            serviceUuids.any { it.equals(AppConstants.UUID_FAST_PAIR_SERVICE.toString(), ignoreCase = true) }

        if (isFastPair) {
            fastPairSeenAddresses[device.address] = true
        }

        val fastPairModelHex = fastPairServiceData
            ?.takeIf { it.size >= 3 }
            ?.take(3)
            ?.joinToString("") { "%02X".format(it) }

        val integration = EarbudsIntegrationRegistry.resolve(deviceName, isFastPair)
        val entry = ScannedBleDevice(
            address = device.address,
            name = deviceName,
            rssi = result.rssi,
            isFastPairAdvertised = isFastPair,
            fastPairModelIdHex = fastPairModelHex,
            serviceUuids = serviceUuids,
            brandHint = integration.brand,
            lastSeenMillis = System.currentTimeMillis()
        )

        _state.update { current ->
            val filtered = current.scannedBleDevices.filterNot { it.address == entry.address }
            val updated = (filtered + entry)
                .sortedWith(
                    compareByDescending<ScannedBleDevice> { it.isFastPairAdvertised }
                        .thenByDescending { it.rssi }
                )
                .take(40)
            current.copy(scannedBleDevices = updated)
        }
    }

    private fun parseHfpBatteryAtCommand(address: String, cmd: String, args: Array<*>) {
        // Standard Apple/Bluetooth HFP AT+IPHONEACCEV=count,key1,val1,... (key 1 = battery 0..9 -> (val+1)*10%)
        runCatching {
            if (cmd.contains("IPHONEACCEV", ignoreCase = true) && args.size >= 3) {
                val nums = args.mapNotNull { it?.toString()?.toIntOrNull() }
                var idx = 1
                while (idx + 1 < nums.size) {
                    val key = nums[idx]
                    val value = nums[idx + 1]
                    if (key == 1 && value in 0..9) {
                        val pct = (value + 1) * 10
                        val prev = batteryCache[address] ?: BatteryTelemetry()
                        batteryCache[address] = prev.copy(
                            overallPercent = pct,
                            source = BatterySource.HFP_VENDOR_AT_REPORT,
                            lastUpdatedMillis = System.currentTimeMillis()
                        )
                        refreshPairedDevices()
                    }
                    idx += 2
                }
            }
        }.onFailure {
            Log.d(tag, "Unable to parse HFP AT event: ${it.message}")
        }
    }

    private fun buildPairedModel(device: BluetoothDevice): PairedAudioDevice {
        val name = safeDeviceName(device)
        val isA2dp = a2dpProxy?.connectedDevices?.any { it.address == device.address } == true
        val isHeadset = headsetProxy?.connectedDevices?.any { it.address == device.address } == true
        val isConnected = isA2dp || isHeadset || isBluetoothDeviceConnectedViaReflectionSafeCheck(device)

        val majorClass = device.bluetoothClass?.majorDeviceClass
        val isAudioClass = majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            majorClass == BluetoothClass.Device.Major.WEARABLE ||
            isA2dp || isHeadset

        val fwInfo = firmwareCache[device.address]
        val fastPair = fastPairSeenAddresses[device.address] == true
        val integration = EarbudsIntegrationRegistry.resolve(name, fastPair)
        val battery = batteryCache[device.address] ?: BatteryTelemetry()

        return PairedAudioDevice(
            address = device.address,
            name = name,
            brand = integration.brand,
            isConnected = isConnected,
            isA2dpConnected = isA2dp,
            isHeadsetConnected = isHeadset,
            isAudioClass = isAudioClass,
            batteryTelemetry = battery,
            firmwareRevision = fwInfo?.first,
            modelNumber = fwInfo?.second,
            manufacturerName = fwInfo?.third,
            supportsFastPair = fastPair
        )
    }

    private fun isBluetoothDeviceConnectedViaReflectionSafeCheck(device: BluetoothDevice): Boolean {
        val connectedDevices = buildList {
            a2dpProxy?.connectedDevices?.let(::addAll)
            headsetProxy?.connectedDevices?.let(::addAll)
        }
        return connectedDevices.any { it.address == device.address }
    }

    private fun clearConnectedState() {
        _state.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                activeDevice = null,
                pairedDevices = emptyList()
            )
        }
    }

    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun safeDeviceName(device: BluetoothDevice?, fallbackName: String? = null): String {
        val name = runCatching { device?.name }.getOrNull()
        return when {
            !name.isNullOrBlank() -> name
            !fallbackName.isNullOrBlank() -> fallbackName
            else -> device?.address ?: "Unknown Bluetooth Device"
        }
    }
}
