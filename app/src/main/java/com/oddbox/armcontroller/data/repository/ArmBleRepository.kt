package com.oddbox.armcontroller.data.repository

import android.bluetooth.BluetoothManager
import android.content.Context
import com.oddbox.armcontroller.data.ble.BleManager
import com.oddbox.armcontroller.data.ble.BleScanner
import com.oddbox.armcontroller.data.model.BleConnectionState
import com.oddbox.armcontroller.data.model.BleDeviceItem
import com.oddbox.armcontroller.data.model.BleLogEntry
import com.oddbox.armcontroller.domain.model.ArmTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArmBleRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : BleRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    private val bleManager = BleManager(context, bluetoothAdapter, scope)
    private val bleScanner = BleScanner(context, bluetoothAdapter, scope) { msg, isErr ->
        bleManager.log(
            if (isErr) com.oddbox.armcontroller.data.model.BleLogType.ERROR
            else com.oddbox.armcontroller.data.model.BleLogType.CONNECTION,
            msg
        )
    }

    private val _telemetryFlow = MutableStateFlow(ArmTelemetry.UNINITIALIZED)
    override val telemetryFlow: StateFlow<ArmTelemetry> = _telemetryFlow.asStateFlow()

    override val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    override val connectedDeviceName: StateFlow<String?> = bleManager.connectedDeviceName
    override val connectedDeviceAddress: StateFlow<String?> = bleManager.connectedDeviceAddress
    override val scannedDevices: StateFlow<List<BleDeviceItem>> = bleScanner.scannedDevices
    override val isScanning: StateFlow<Boolean> = bleScanner.isScanning
    override val logsFlow: StateFlow<List<BleLogEntry>> = bleManager.logsFlow

    init {
        // Collect telemetry notifications
        scope.launch {
            bleManager.rawStatusFlow.collect { raw ->
                val parsed = ArmTelemetry.parse(raw)
                if (parsed != null) {
                    _telemetryFlow.value = parsed
                }
            }
        }

        // Reset telemetry on disconnect
        scope.launch {
            bleManager.connectionState.collect { state ->
                if (state == BleConnectionState.DISCONNECTED) {
                    _telemetryFlow.value = ArmTelemetry.UNINITIALIZED
                }
            }
        }
    }

    override fun startScan(): Boolean {
        return bleScanner.startScan()
    }

    override fun stopScan() {
        bleScanner.stopScan()
    }

    override fun connect(address: String): Boolean {
        stopScan()
        return bleManager.connect(address)
    }

    override fun disconnect() {
        bleManager.disconnect()
    }

    override fun currentMovementEpoch(): Long {
        return bleManager.currentMovementEpoch()
    }

    override fun invalidateMovementQueue(reason: String): Long {
        return bleManager.invalidateMovementQueue(reason)
    }

    override fun queueStopImmediate(reason: String) {
        bleManager.queueStopImmediate(reason)
    }

    override suspend fun sendMovementCommand(command: String, epoch: Long): Boolean {
        return bleManager.sendMovementCommand(command, epoch)
    }

    override suspend fun sendCommand(command: String, isEmergency: Boolean): Boolean {
        return bleManager.sendCommand(command, isEmergency)
    }

    override fun clearLogs() {
        bleManager.clearLogs()
    }

    override fun release() {
        bleScanner.release()
        bleManager.release()
    }
}

