package com.oddbox.armcontroller.presentation.viewmodel

import com.oddbox.armcontroller.data.model.BleConnectionState
import com.oddbox.armcontroller.data.model.BleDeviceItem
import com.oddbox.armcontroller.data.model.BleLogEntry
import com.oddbox.armcontroller.domain.model.ArmDirection
import com.oddbox.armcontroller.domain.model.ArmSpeed
import com.oddbox.armcontroller.domain.model.ArmTelemetry
import com.oddbox.armcontroller.domain.model.MagnetState

data class ArmControlUiState(
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val scannedDevices: List<BleDeviceItem> = emptyList(),
    val isScanning: Boolean = false,
    val telemetry: ArmTelemetry = ArmTelemetry.UNINITIALIZED,
    val selectedSpeed: ArmSpeed = ArmSpeed.MEDIUM,
    val activeDirection: ArmDirection? = null,
    val isEmergencyStopped: Boolean = false,
    val isMagnetSafetyUnlocked: Boolean = false,
    val logs: List<BleLogEntry> = emptyList(),
    val heartbeatIntervalMs: Long = 200L,
    val showScanDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showLogDialog: Boolean = false,
    val statusBanner: String? = null
) {
    val isConnected: Boolean
        get() = connectionState == BleConnectionState.CONNECTED

    val isControlsEnabled: Boolean
        get() = isConnected && !isEmergencyStopped

    val isMagnetActive: Boolean
        get() = telemetry.magnetState == MagnetState.ON
}

