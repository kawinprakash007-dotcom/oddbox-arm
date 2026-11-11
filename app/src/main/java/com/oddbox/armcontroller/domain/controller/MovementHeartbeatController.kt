package com.oddbox.armcontroller.domain.controller

import com.oddbox.armcontroller.data.ble.BleConstants
import com.oddbox.armcontroller.data.ble.BleDebugLog
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns press-and-hold directional heartbeat lifecycle.
 *
 * All start / stop / force-stop state transitions serialize under [stateLock]
 * synchronously on the caller thread, atomically advancing [generation] and the
 * queue epoch via [invalidateMovementQueueAction] + [queueStopImmediateAction].
 * This eliminates coroutine cancellation races where an in-flight heartbeat could
 * push a movement packet into the BLE queue after STOP.
 */
class MovementHeartbeatController(
    private val scope: CoroutineScope,
    private val sendMovementAction: suspend (command: String, epoch: Long) -> Boolean,
    private val queueStopImmediateAction: (reason: String) -> Unit,
    private val invalidateMovementQueueAction: () -> Long,
    private val currentEpochProvider: () -> Long,
    var heartbeatIntervalMs: Long = BleConstants.HEARTBEAT_INTERVAL_MS
) {
    private val _activeDirection = MutableStateFlow<ArmDirection?>(null)
    val activeDirection: StateFlow<ArmDirection?> = _activeDirection.asStateFlow()

    private val stateLock = Any()
    private val generation = AtomicLong(0L)
    private var heartbeatJob: Job? = null

    /**
     * Triggered synchronously on finger DOWN.
     * Immediately dispatches the initial command and repeats at [heartbeatIntervalMs]
     * (< 600ms ESP32 watchdog) until released, cancelled, disabled, or backgrounded.
     */
    fun startMovement(direction: ArmDirection) {
        synchronized(stateLock) {
            val previous = _activeDirection.value
            val myGen = generation.incrementAndGet()
            heartbeatJob?.cancel()
            heartbeatJob = null

            if (previous != null && previous != direction) {
                // Conflicting axis/direction pressed while another was held: halt old axis cleanly first.
                BleDebugLog.d("HEARTBEAT_CANCELLED")
                queueStopImmediateAction("Direction switch to ${direction.command}")
            }

            val myEpoch = invalidateMovementQueueAction()
            _activeDirection.value = direction

            heartbeatJob = scope.launch(Dispatchers.Default) {
                if (!shouldContinue(myGen, myEpoch, direction)) return@launch

                // 1. Send initial movement command immediately
                if (sendMovementAction(direction.command, myEpoch) && shouldContinue(myGen, myEpoch, direction)) {
                    BleDebugLog.d("HEARTBEAT_SENT: ${direction.command}")
                }

                // 2. Heartbeat loop — strictly exits on cancellation, generation bump, or epoch bump
                while (shouldContinue(myGen, myEpoch, direction)) {
                    delay(heartbeatIntervalMs)
                    if (!shouldContinue(myGen, myEpoch, direction)) break
                    if (sendMovementAction(direction.command, myEpoch) && shouldContinue(myGen, myEpoch, direction)) {
                        BleDebugLog.d("HEARTBEAT_SENT: ${direction.command}")
                    }
                }
            }
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.shouldContinue(
        expectedGen: Long,
        expectedEpoch: Long,
        direction: ArmDirection
    ): Boolean {
        return isActive &&
            generation.get() == expectedGen &&
            _activeDirection.value == direction &&
            currentEpochProvider() == expectedEpoch
    }

    /**
     * Triggered synchronously on finger UP, gesture cancellation, or button disable.
     * Cancels heartbeat synchronously, invalidates stale movement commands, and
     * queues STOP at highest priority before releasing the lock.
     */
    fun stopMovement(direction: ArmDirection? = null) {
        synchronized(stateLock) {
            val current = _activeDirection.value
            if (direction != null && current != direction) {
                // A newer direction has already taken over (multi-touch handoff) or already stopped.
                return
            }

            generation.incrementAndGet()
            heartbeatJob?.cancel()
            heartbeatJob = null
            _activeDirection.value = null

            BleDebugLog.d("HEARTBEAT_CANCELLED")
            queueStopImmediateAction("Release ${direction?.command ?: "MANUAL"}")
        }
    }

    /**
     * Unconditional halt for app backgrounding, window focus loss, navigation,
     * BLE disconnect, or Emergency Stop.
     */
    fun forceStop(reason: String = "Force stop") {
        synchronized(stateLock) {
            val wasActive = _activeDirection.value != null || heartbeatJob != null
            generation.incrementAndGet()
            heartbeatJob?.cancel()
            heartbeatJob = null
            _activeDirection.value = null

            if (wasActive) {
                BleDebugLog.d("HEARTBEAT_CANCELLED")
            }
            queueStopImmediateAction(reason)
        }
    }
}
