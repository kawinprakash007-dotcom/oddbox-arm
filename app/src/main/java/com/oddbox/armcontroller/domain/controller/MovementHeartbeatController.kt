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

class MovementHeartbeatController(
    private val scope: CoroutineScope,
    private val sendCommandAction: suspend (String) -> Boolean,
    var heartbeatIntervalMs: Long = BleConstants.HEARTBEAT_INTERVAL_MS
) {
    private val _activeDirection = MutableStateFlow<ArmDirection?>(null)
    val activeDirection: StateFlow<ArmDirection?> = _activeDirection.asStateFlow()

    // Single serialized control job — replaced atomically on each press/release.
    // Using a plain @Volatile reference avoids the mutex-ordering race where
    // stopMovement's launch() can be scheduled before startMovement's inner work
    // executes, letting a direction command leak out after the STOP.
    @Volatile private var heartbeatJob: Job? = null

    /**
     * Called from the UI thread (Compose pointer input) on finger-DOWN.
     * Cancels any existing movement job first, marks the new direction, then
     * starts the heartbeat loop. The initial command and all heartbeats run
     * inside the job so they are automatically cancelled on release.
     */
    fun startMovement(direction: ArmDirection) {
        // Cancel whatever was running (may already be null — that's fine)
        heartbeatJob?.cancel()

        _activeDirection.value = direction

        heartbeatJob = scope.launch(Dispatchers.Default) {
            try {
                // 1. Send the first command immediately on press
                sendCommandAction(direction.command)

                // 2. Heartbeat: keep refreshing well inside the 600ms ESP32 watchdog
                while (isActive) {
                    delay(heartbeatIntervalMs)
                    if (isActive) sendCommandAction(direction.command)
                }
            } finally {
                // finally{} runs when the job is cancelled (finger released)
                // or when the coroutine finishes naturally.
                // Do NOT send STOP here — stopMovement() sends it explicitly
                // so we don't double-stop if another direction supersedes this one.
            }
        }
    }

    /**
     * Called from the UI thread (Compose pointer input) on finger-UP or gesture
     * cancellation. Cancels the heartbeat immediately, clears the active
     * direction, and sends STOP. Passing [direction] makes it a no-op when a
     * *different* direction has already taken over (multi-touch safety).
     */
    fun stopMovement(direction: ArmDirection? = null) {
        val current = _activeDirection.value
        if (direction != null && current != direction) return  // different axis — ignore

        heartbeatJob?.cancel()
        heartbeatJob = null
        _activeDirection.value = null

        // Send STOP asynchronously — avoids blocking the UI thread
        scope.launch(Dispatchers.Default) {
            sendCommandAction(ArmCommand.STOP)
        }
    }

    /**
     * Force-stops everything: app backgrounding, disconnect, ESTOP reset.
     * Safe to call from any thread.
     */
    fun forceStop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _activeDirection.value = null

        scope.launch(Dispatchers.Default) {
            sendCommandAction(ArmCommand.STOP)
        }
    }
}

