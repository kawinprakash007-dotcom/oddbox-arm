package com.oddbox.armcontroller.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import com.oddbox.armcontroller.domain.model.ArmCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Dual-lane serialized BLE GATT write queue.
 *
 * - [urgentChannel] carries STOP, ESTOP, and MAGNET_OFF and always preempts
 *   [normalChannel].
 * - [movementEpoch] invalidates any in-flight or queued directional movement
 *   commands the instant a release/STOP/ESTOP/disconnect occurs, guaranteeing an
 *   old movement command can never execute after STOP.
 */
class BleCommandQueue(
    private val scope: CoroutineScope,
    private val customWriter: ((payload: ByteArray) -> Boolean)? = null,
    private val waitForWriteCallback: Boolean = customWriter == null,
    private val onLog: (message: String, isError: Boolean) -> Unit
) {
    private data class WriteRequest(
        val command: String,
        val payload: ByteArray,
        val isStop: Boolean = false,
        val isUrgent: Boolean = false,
        val isDirectional: Boolean = false,
        val epoch: Long = 0L,
        val completion: CompletableDeferred<Boolean>
    )

    private val urgentChannel = Channel<WriteRequest>(capacity = Channel.UNLIMITED)
    private val normalChannel = Channel<WriteRequest>(capacity = Channel.UNLIMITED)

    private val activeGattRef = AtomicReference<BluetoothGatt?>(null)
    private val activeCharRef = AtomicReference<BluetoothGattCharacteristic?>(null)
    private val activeWriteSignal = AtomicReference<CompletableDeferred<Unit>?>(null)

    private val movementEpoch = AtomicLong(0L)

    private var workerJob: Job? = null
    private val queueMutex = Mutex()

    fun currentMovementEpoch(): Long = movementEpoch.get()

    fun bind(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
        activeGattRef.set(gatt)
        activeCharRef.set(characteristic)
        if (workerJob == null || workerJob?.isActive != true) {
            startWorker()
        }
    }

    private fun startWorker() {
        workerJob?.cancel()
        workerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                // Always service urgent commands (STOP / ESTOP / MAGNET_OFF) first.
                val request = urgentChannel.tryReceive().getOrNull()
                    ?: select {
                        urgentChannel.onReceive { it }
                        normalChannel.onReceive { it }
                    }
                processWrite(request)
            }
        }
    }

    private suspend fun processWrite(request: WriteRequest) {
        // Fast stale check before waiting on mutex or touching BLE.
        if (request.isDirectional && request.epoch != movementEpoch.get()) {
            request.completion.complete(false)
            return
        }

        queueMutex.withLock {
            // Re-check inside mutex in case STOP invalidated this epoch while we waited.
            if (request.isDirectional && request.epoch != movementEpoch.get()) {
                request.completion.complete(false)
                return@withLock
            }

            if (customWriter == null && (activeGattRef.get() == null || activeCharRef.get() == null)) {
                onLog("Dropped command '${request.command}': BLE GATT or characteristic not ready", true)
                BleDebugLog.w("DROPPED: ${request.command} — GATT not ready")
                request.completion.complete(false)
                return@withLock
            }

            val maxAttempts = if (request.isUrgent) BleConstants.STOP_MAX_WRITE_RETRIES else 1
            var writeInitiated = false

            for (attempt in 1..maxAttempts) {
                if (request.isDirectional && request.epoch != movementEpoch.get()) {
                    request.completion.complete(false)
                    return@withLock
                }

                if (customWriter == null && (activeGattRef.get() == null || activeCharRef.get() == null)) {
                    break
                }

                val signal = CompletableDeferred<Unit>()
                activeWriteSignal.set(signal)

                writeInitiated = attemptWrite(request)
                if (writeInitiated) {
                    break
                }

                // Write rejected (most commonly BluetoothGatt mDeviceBusy still true from the
                // immediately preceding WRITE_NO_RESPONSE). Wait briefly for onCharacteristicWrite
                // or short retry backoff so critical STOP/ESTOP packets never drop.
                if (attempt < maxAttempts) {
                    if (waitForWriteCallback) {
                        withTimeoutOrNull(BleConstants.STOP_RETRY_DELAY_MS) { signal.await() }
                    } else {
                        delay(BleConstants.STOP_RETRY_DELAY_MS)
                    }
                }
            }

            if (!writeInitiated) {
                activeWriteSignal.set(null)
                onLog("Failed to initiate GATT write for '${request.command}'", true)
                BleDebugLog.w("WRITE_FAILED: ${request.command}")
                request.completion.complete(false)
                return@withLock
            }

            if (request.isStop) {
                BleDebugLog.d("STOP_SENT")
                onLog("TX: STOP (sent)", false)
            }

            // Write has been handed to the BLE stack — unblock the caller right away.
            request.completion.complete(true)

            // Wait briefly for onCharacteristicWrite (if enabled on this device) so Android's
            // internal mDeviceBusy clears before the next write, capped tightly so a missing
            // callback can never block subsequent urgent commands.
            if (waitForWriteCallback) {
                val pendingSignal = activeWriteSignal.get()
                if (pendingSignal != null) {
                    val acked = withTimeoutOrNull(BleConstants.WRITE_SETTLE_TIMEOUT_MS) {
                        pendingSignal.await()
                    } != null
                    if (!acked) {
                        activeWriteSignal.compareAndSet(pendingSignal, null)
                        delay(BleConstants.NO_RESPONSE_INTER_WRITE_DELAY_MS)
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun attemptWrite(request: WriteRequest): Boolean {
        customWriter?.let { return it(request.payload) }

        val gatt = activeGattRef.get() ?: return false
        val characteristic = activeCharRef.get() ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt.writeCharacteristic(
                    characteristic,
                    request.payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
                res == BluetoothGatt.GATT_SUCCESS || res == 0
            } else {
                characteristic.value = request.payload
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            onLog("SecurityException writing command '${request.command}': ${e.message}", true)
            false
        } catch (e: Exception) {
            onLog("Exception writing command '${request.command}': ${e.message}", true)
            false
        }
    }

    /**
     * Called by BluetoothGattCallback.onCharacteristicWrite when Android clears mDeviceBusy.
     */
    fun onWriteCompleted(@Suppress("UNUSED_PARAMETER") status: Int) {
        activeWriteSignal.getAndSet(null)?.complete(Unit)
    }

    /**
     * Synchronously invalidates the current movement epoch and drains any queued
     * directional commands from [normalChannel]. Non-directional commands (e.g. SPEED)
     * are preserved. Safe to call from any thread including the UI thread.
     */
    fun invalidatePendingMovement(reason: String = "STOP"): Long {
        val newEpoch = movementEpoch.incrementAndGet()
        val preserved = mutableListOf<WriteRequest>()
        var drainedCount = 0

        while (true) {
            val item = normalChannel.tryReceive().getOrNull() ?: break
            if (item.isDirectional) {
                item.completion.complete(false)
                drainedCount++
            } else {
                preserved.add(item)
            }
        }
        preserved.forEach { normalChannel.trySend(it) }

        if (drainedCount > 0) {
            BleDebugLog.d("Drained $drainedCount stale directional command(s) ($reason)")
            onLog("Drained $drainedCount pending directional command(s) ($reason)", false)
        }
        return newEpoch
    }

    /**
     * Synchronously enqueues STOP with highest priority, first invalidating and draining
     * all pending directional commands so nothing queued can run after STOP.
     */
    fun queueStopNow(reason: String = "Release"): CompletableDeferred<Boolean> {
        val epoch = invalidatePendingMovement(reason)
        BleDebugLog.d("STOP_QUEUED")

        val completion = CompletableDeferred<Boolean>()
        val request = WriteRequest(
            command = ArmCommand.STOP,
            payload = (ArmCommand.STOP + "\n").toByteArray(Charsets.UTF_8),
            isStop = true,
            isUrgent = true,
            isDirectional = false,
            epoch = epoch,
            completion = completion
        )
        urgentChannel.trySend(request)
        return completion
    }

    /**
     * Suspends until the prioritized STOP write has been handed to BLE.
     */
    suspend fun enqueueStop(): Boolean = queueStopNow().await()

    /**
     * Synchronously enqueues an urgent command (e.g. ESTOP, MAGNET_OFF) ahead of normal items.
     */
    fun queueUrgentNow(
        command: String,
        invalidateMovement: Boolean
    ): CompletableDeferred<Boolean> {
        val epoch = if (invalidateMovement) {
            invalidatePendingMovement(command)
        } else {
            movementEpoch.get()
        }
        if (command == ArmCommand.STOP || command == ArmCommand.ESTOP) {
            BleDebugLog.d("STOP_QUEUED")
        }

        val completion = CompletableDeferred<Boolean>()
        val request = WriteRequest(
            command = command,
            payload = (command + "\n").toByteArray(Charsets.UTF_8),
            isStop = command == ArmCommand.STOP,
            isUrgent = true,
            isDirectional = false,
            epoch = epoch,
            completion = completion
        )
        urgentChannel.trySend(request)
        return completion
    }

    suspend fun enqueueUrgent(command: String, invalidateMovement: Boolean): Boolean =
        queueUrgentNow(command, invalidateMovement).await()

    /**
     * Enqueues a directional movement command bound to [expectedEpoch].
     * Immediately rejects without queuing if a STOP/release already invalidated [expectedEpoch].
     */
    suspend fun enqueueMovement(command: String, expectedEpoch: Long): Boolean {
        if (expectedEpoch != movementEpoch.get()) {
            return false
        }
        val completion = CompletableDeferred<Boolean>()
        val request = WriteRequest(
            command = command,
            payload = (command + "\n").toByteArray(Charsets.UTF_8),
            isStop = false,
            isUrgent = false,
            isDirectional = true,
            epoch = expectedEpoch,
            completion = completion
        )
        normalChannel.send(request)
        return completion.await()
    }

    /**
     * General enqueue for non-movement commands (SPEED:*, MAGNET_ON, etc.) or direct calls.
     */
    suspend fun enqueue(command: String, @Suppress("UNUSED_PARAMETER") isEmergency: Boolean = false): Boolean {
        return when (command) {
            ArmCommand.STOP -> enqueueStop()
            ArmCommand.ESTOP -> enqueueUrgent(command, invalidateMovement = true)
            ArmCommand.MAGNET_OFF -> enqueueUrgent(command, invalidateMovement = false)
            else -> {
                val isDir = isDirectionalCommand(command)
                val epoch = movementEpoch.get()
                if (isDir) {
                    enqueueMovement(command, epoch)
                } else {
                    val completion = CompletableDeferred<Boolean>()
                    val request = WriteRequest(
                        command = command,
                        payload = (command + "\n").toByteArray(Charsets.UTF_8),
                        isStop = false,
                        isUrgent = false,
                        isDirectional = false,
                        epoch = epoch,
                        completion = completion
                    )
                    normalChannel.send(request)
                    completion.await()
                }
            }
        }
    }

    /**
     * Synchronously drains both urgent and normal queues (e.g. on disconnect or release).
     */
    fun clearQueue(reason: String) {
        movementEpoch.incrementAndGet()
        activeWriteSignal.getAndSet(null)?.complete(Unit)

        var drainedCount = 0
        while (true) {
            val item = urgentChannel.tryReceive().getOrNull() ?: break
            item.completion.complete(false)
            drainedCount++
        }
        while (true) {
            val item = normalChannel.tryReceive().getOrNull() ?: break
            item.completion.complete(false)
            drainedCount++
        }
        if (drainedCount > 0) {
            BleDebugLog.d("Queue cleared ($drainedCount pending commands dropped: $reason)")
            onLog("Cleared $drainedCount pending commands from queue ($reason)", false)
        }
    }

    fun release() {
        workerJob?.cancel()
        workerJob = null
        clearQueue("Release")
        activeGattRef.set(null)
        activeCharRef.set(null)
    }

    companion object {
        private val DIRECTIONAL_COMMANDS = setOf(
            ArmCommand.BASE_LEFT,
            ArmCommand.BASE_RIGHT,
            ArmCommand.SHOULDER_UP,
            ArmCommand.SHOULDER_DOWN,
            ArmCommand.WRIST_UP,
            ArmCommand.WRIST_DOWN
        )

        fun isDirectionalCommand(command: String): Boolean = command in DIRECTIONAL_COMMANDS
    }
}
