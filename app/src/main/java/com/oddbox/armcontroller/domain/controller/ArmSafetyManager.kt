package com.oddbox.armcontroller.domain.controller

import com.oddbox.armcontroller.domain.model.ArmCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArmSafetyManager(
    private val scope: CoroutineScope,
    private val movementController: MovementHeartbeatController,
    private val sendCommandAction: suspend (String, Boolean) -> Boolean,
    private val onLog: (String, Boolean) -> Unit
) {
    private val _isEmergencyStopped = MutableStateFlow(false)
    val isEmergencyStopped: StateFlow<Boolean> = _isEmergencyStopped.asStateFlow()

    private val _isMagnetArmingUnlocked = MutableStateFlow(false)
    val isMagnetArmingUnlocked: StateFlow<Boolean> = _isMagnetArmingUnlocked.asStateFlow()

    /**
     * Executes immediate Emergency Stop sequence:
     * 1. Stops any ongoing movement heartbeat
     * 2. Sets emergency stop lock flag
     * 3. Sends ESTOP command to ESP32
     * 4. Sends MAGNET_OFF command to ESP32
     * 5. Locks magnet safety switch
     */
    fun triggerEmergencyStop() {
        _isEmergencyStopped.value = true
        _isMagnetArmingUnlocked.value = false
        movementController.forceStop("Emergency stop")

        scope.launch {
            onLog("EMERGENCY STOP TRIGGERED! Shutting down movement & magnet.", true)
            sendCommandAction(ArmCommand.ESTOP, true)
            sendCommandAction(ArmCommand.MAGNET_OFF, true)
        }
    }

    /**
     * Resets emergency stop state. Requires deliberate user confirmation.
     */
    fun resetEmergencyStop() {
        _isEmergencyStopped.value = false
        onLog("Emergency stop reset by operator. System is ready.", false)
    }

    /**
     * Toggles safety arming switch for the electromagnet.
     */
    fun setMagnetArmingUnlocked(unlocked: Boolean) {
        _isMagnetArmingUnlocked.value = unlocked
        if (!unlocked) {
            // If operator locks safety switch, turn off magnet
            scope.launch {
                sendCommandAction(ArmCommand.MAGNET_OFF, false)
            }
        }
    }

    /**
     * Called when connection is lost to clean up safety states.
     */
    fun onDisconnection() {
        movementController.forceStop("BLE disconnected")
        _isMagnetArmingUnlocked.value = false
    }
}

