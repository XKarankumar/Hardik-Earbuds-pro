package com.example.bluetooth

import com.example.devices.AncCapabilityState
import com.example.devices.BatteryTelemetry
import com.example.devices.ConnectionState
import com.example.devices.EarbudsBrand
import com.example.devices.EarbudsCapabilities
import com.example.devices.TouchGestureConfig

data class PairedAudioDevice(
    val address: String,
    val name: String,
    val brand: EarbudsBrand,
    val isConnected: Boolean,
    val isA2dpConnected: Boolean,
    val isHeadsetConnected: Boolean,
    val isAudioClass: Boolean,
    val batteryTelemetry: BatteryTelemetry,
    val firmwareRevision: String? = null,
    val modelNumber: String? = null,
    val manufacturerName: String? = null,
    val supportsFastPair: Boolean = false
)

data class ScannedBleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isFastPairAdvertised: Boolean,
    val fastPairModelIdHex: String?,
    val serviceUuids: List<String>,
    val brandHint: EarbudsBrand,
    val lastSeenMillis: Long
)

data class GattCharacteristicInfo(
    val uuid: String,
    val label: String,
    val propertiesSummary: String,
    val isReadable: Boolean,
    val decodedValue: String? = null,
    val rawHex: String? = null
)

data class GattServiceInfo(
    val uuid: String,
    val label: String,
    val isPrimary: Boolean,
    val characteristics: List<GattCharacteristicInfo>
)

data class GattInspectionState(
    val targetAddress: String? = null,
    val targetName: String? = null,
    val isConnectingOrDiscovering: Boolean = false,
    val isConnected: Boolean = false,
    val services: List<GattServiceInfo> = emptyList(),
    val statusMessage: String = "Select a device to inspect live BLE GATT services."
)

data class BluetoothDashboardState(
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val scanStatusMessage: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val activeDevice: PairedAudioDevice? = null,
    val activeCapabilities: EarbudsCapabilities? = null,
    val ancState: AncCapabilityState = AncCapabilityState(),
    val touchConfigs: List<TouchGestureConfig> = emptyList(),
    val pairedDevices: List<PairedAudioDevice> = emptyList(),
    val scannedBleDevices: List<ScannedBleDevice> = emptyList(),
    val gattInspection: GattInspectionState = GattInspectionState(),
    val lastConnectionEventMessage: String? = null
)
