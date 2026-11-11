package com.oddbox.armcontroller.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oddbox.armcontroller.data.model.BleConnectionState
import com.oddbox.armcontroller.data.repository.ArmBleRepository
import com.oddbox.armcontroller.data.repository.BleRepository
import com.oddbox.armcontroller.domain.controller.ArmSafetyManager
import com.oddbox.armcontroller.domain.controller.MovementHeartbeatController
import com.oddbox.armcontroller.domain.model.ArmCommand
import com.oddbox.armcontroller.domain.model.ArmDirection
import com.oddbox.armcontroller.domain.model.ArmSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArmControlViewModel(
    application: Application,
    private val repository: BleRepository = ArmBleRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ArmControlUiState())
    val uiState: StateFlow<ArmControlUiState> = _uiState.asStateFlow()

    private val movementController = MovementHeartbeatController(
        scope = viewModelScope,
        sendMovementAction = { cmd, epoch ->
            repository.sendMovementCommand(cmd, epoch)
        },
        queueStopImmediateAction = { reason ->
            repository.queueStopImmediate(reason)
        },
        invalidateMovementQueueAction = {
            repository.invalidateMovementQueue("Movement superseded")
        },
        currentEpochProvider = {
            repository.currentMovementEpoch()
        },
        heartbeatIntervalMs = 200L
    )

    private val safetyManager = ArmSafetyManager(
        scope = viewModelScope,
        movementController = movementController,
        sendCommandAction = { cmd, isEstop ->
            repository.sendCommand(cmd, isEstop)
        },
        onLog = { _, _ -> }
    )

    init {
        // Collect connection state
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state == BleConnectionState.DISCONNECTED) {
                    safetyManager.onDisconnection()
                }
            }
        }

        // Collect connected device name
        viewModelScope.launch {
            repository.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(connectedDeviceName = name) }
            }
        }

        // Collect connected device address
        viewModelScope.launch {
            repository.connectedDeviceAddress.collect { addr ->
                _uiState.update { it.copy(connectedDeviceAddress = addr) }
            }
        }

        // Collect scanned devices
        viewModelScope.launch {
            repository.scannedDevices.collect { list ->
                _uiState.update { it.copy(scannedDevices = list) }
            }
        }

        // Collect scanning status
        viewModelScope.launch {
            repository.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }

        // Collect telemetry
        viewModelScope.launch {
            repository.telemetryFlow.collect { telem ->
                _uiState.update { it.copy(telemetry = telem) }
            }
        }

        // Collect log entries
        viewModelScope.launch {
            repository.logsFlow.collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }

        // Collect active movement direction
        viewModelScope.launch {
            movementController.activeDirection.collect { dir ->
                _uiState.update { it.copy(activeDirection = dir) }
            }
        }

        // Collect Emergency Stop state
        viewModelScope.launch {
            safetyManager.isEmergencyStopped.collect { isEstop ->
                _uiState.update { it.copy(isEmergencyStopped = isEstop) }
            }
        }

        // Collect Magnet Safety Interlock state
        viewModelScope.launch {
            safetyManager.isMagnetArmingUnlocked.collect { unlocked ->
                _uiState.update { it.copy(isMagnetSafetyUnlocked = unlocked) }
            }
        }
    }

    // --- BLE Connection & Scanning ---
    fun startScan() {
        val started = repository.startScan()
        if (started) {
            _uiState.update { it.copy(showScanDialog = true) }
        }
    }

    fun stopScan() {
        repository.stopScan()
    }

    fun connectDevice(address: String) {
        repository.connect(address)
        _uiState.update { it.copy(showScanDialog = false) }
    }

    fun disconnectDevice() {
        movementController.forceStop("User disconnect")
        viewModelScope.launch {
            // Brief window for prioritized STOP packet to hit the BLE radio before GATT closes.
            kotlinx.coroutines.delay(50L)
            safetyManager.onDisconnection()
            repository.disconnect()
        }
    }

    // --- Directional Press-and-Hold Movement ---
    fun onDirectionPress(direction: ArmDirection) {
        if (!uiState.value.isControlsEnabled) return
        movementController.startMovement(direction)
    }

    fun onDirectionRelease(direction: ArmDirection) {
        movementController.stopMovement(direction)
    }

    fun onMovementCancelled() {
        movementController.forceStop("Movement cancelled")
    }

    // --- Speed Selection ---
    fun setSpeed(speed: ArmSpeed) {
        _uiState.update { it.copy(selectedSpeed = speed) }
        viewModelScope.launch {
            repository.sendCommand(speed.command, false)
        }
    }

    // --- Electromagnet Control ---
    fun toggleMagnetSafety(unlocked: Boolean) {
        safetyManager.setMagnetArmingUnlocked(unlocked)
    }

    fun setMagnetState(turnOn: Boolean) {
        if (!uiState.value.isControlsEnabled) return
        if (turnOn && !uiState.value.isMagnetSafetyUnlocked) {
            _uiState.update { it.copy(statusBanner = "Unlock Safety Interlock to enable magnet") }
            return
        }

        val cmd = if (turnOn) ArmCommand.MAGNET_ON else ArmCommand.MAGNET_OFF
        viewModelScope.launch {
            repository.sendCommand(cmd, false)
        }
    }

    // --- Emergency Stop ---
    fun triggerEmergencyStop() {
        safetyManager.triggerEmergencyStop()
    }

    fun resetEmergencyStop() {
        safetyManager.resetEmergencyStop()
    }

    // --- Settings & Timings ---
    fun updateHeartbeatInterval(intervalMs: Long) {
        val clamped = intervalMs.coerceIn(100L, 500L)
        movementController.heartbeatIntervalMs = clamped
        _uiState.update { it.copy(heartbeatIntervalMs = clamped) }
    }

    // --- Dialogs & Panels ---
    fun setShowScanDialog(show: Boolean) {
        if (show) {
            movementController.forceStop("Scan dialog opened")
        } else {
            repository.stopScan()
        }
        _uiState.update { it.copy(showScanDialog = show) }
    }

    fun setShowSettingsDialog(show: Boolean) {
        if (show) {
            movementController.forceStop("Settings dialog opened")
        }
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun setShowLogDialog(show: Boolean) {
        _uiState.update { it.copy(showLogDialog = show) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    fun onAppBackgrounded() {
        movementController.forceStop("App backgrounded")
    }

    override fun onCleared() {
        super.onCleared()
        movementController.forceStop("ViewModel cleared")
        repository.release()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArmControlViewModel::class.java)) {
                return ArmControlViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
