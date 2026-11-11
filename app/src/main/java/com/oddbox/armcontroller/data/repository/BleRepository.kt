package com.oddbox.armcontroller.data.repository

import com.oddbox.armcontroller.data.model.BleConnectionState
import com.oddbox.armcontroller.data.model.BleDeviceItem
import com.oddbox.armcontroller.data.model.BleLogEntry
import com.oddbox.armcontroller.domain.model.ArmTelemetry
import kotlinx.coroutines.flow.StateFlow

interface BleRepository {
    val connectionState: StateFlow<BleConnectionState>
    val connectedDeviceName: StateFlow<String?>
    val connectedDeviceAddress: StateFlow<String?>
    val scannedDevices: StateFlow<List<BleDeviceItem>>
    val isScanning: StateFlow<Boolean>
    val telemetryFlow: StateFlow<ArmTelemetry>
    val logsFlow: StateFlow<List<BleLogEntry>>

    fun startScan(): Boolean
    fun stopScan()
    fun connect(address: String): Boolean
    fun disconnect()
    suspend fun sendCommand(command: String, isEmergency: Boolean = false): Boolean
    fun clearLogs()
    fun release()
}

