package com.oddbox.armcontroller.domain.controller

import com.oddbox.armcontroller.data.ble.BleConstants
import com.oddbox.armcontroller.domain.model.ArmCommand
import com.oddbox.armcontroller.domain.model.ArmDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MovementHeartbeatController(
    private val scope: CoroutineScope,
    private val sendCommandAction: suspend (String) -> Boolean,
    var heartbeatIntervalMs: Long = BleConstants.HEARTBEAT_INTERVAL_MS
) {
    private val _activeDirection = MutableStateFlow<ArmDirection?>(null)
    val activeDirection: StateFlow<ArmDirection?> = _activeDirection.asStateFlow()

    private var heartbeatJob: Job? = null
    private val controllerMutex = Mutex()

    /**
     * Triggered immediately when user touches down on a direction button.
     */
    fun startMovement(direction: ArmDirection) {
        scope.launch {
            controllerMutex.withLock {
                // If a conflicting direction is already active, stop it first
                val current = _activeDirection.value
                if (current != null && current != direction) {
                    heartbeatJob?.cancel()
                    sendCommandAction(ArmCommand.STOP)
                }

                _activeDirection.value = direction
                heartbeatJob?.cancel()

                heartbeatJob = scope.launch(Dispatchers.Default) {
                    try {
                        // 1. Initial burst command
                        sendCommandAction(direction.command)

                        // 2. Periodic heartbeat while held (before 600ms firmware watchdog)
                        while (isActive) {
                            delay(heartbeatIntervalMs)
                            if (isActive) {
                                sendCommandAction(direction.command)
                            }
                        }
                    } finally {
                        // Guarantee STOP is sent if coroutine was cancelled
                        // (unless another direction superseded it within the lock)
                    }
                }
            }
        }
    }

    /**
     * Triggered immediately when finger releases or gesture cancels.
     */
    fun stopMovement(direction: ArmDirection? = null) {
        scope.launch {
            controllerMutex.withLock {
                val current = _activeDirection.value
                // If direction specified, only stop if it matches the active one
                if (direction == null || current == direction) {
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    _activeDirection.value = null
                    // Immediately send STOP command
                    sendCommandAction(ArmCommand.STOP)
                }
            }
        }
    }

    /**
     * Force stop all movement (e.g. app backgrounding, screen navigation, or disconnect).
     */
    fun forceStop() {
        scope.launch {
            controllerMutex.withLock {
                heartbeatJob?.cancel()
                heartbeatJob = null
                _activeDirection.value = null
                sendCommandAction(ArmCommand.STOP)
            }
        }
    }
}

