package com.oddbox.armcontroller.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class BleCommandQueue(
    private val scope: CoroutineScope,
    private val onLog: (message: String, isError: Boolean) -> Unit
) {
    private data class WriteRequest(
        val command: String,
        val payload: ByteArray,
        val isEmergency: Boolean = false,
        val completion: CompletableDeferred<Boolean>
    )

    private val queueChannel = Channel<WriteRequest>(capacity = Channel.UNLIMITED)
    private val isProcessing = AtomicBoolean(false)
    private val activeGattRef = java.util.concurrent.atomic.AtomicReference<BluetoothGatt?>(null)
    private val activeCharRef = java.util.concurrent.atomic.AtomicReference<BluetoothGattCharacteristic?>(null)

    private var activeCompletion: CompletableDeferred<Boolean>? = null
    private var workerJob: Job? = null
    private val queueMutex = Mutex()

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
            for (request in queueChannel) {
                processWrite(request)
            }
        }
    }

    private suspend fun processWrite(request: WriteRequest) {
        val gatt = activeGattRef.get()
        val characteristic = activeCharRef.get()

        if (gatt == null || characteristic == null) {
            onLog("Dropped command '${request.command}': BLE GATT or characteristic not ready", true)
            request.completion.complete(false)
            return
        }

        queueMutex.withLock {
            val deferred = CompletableDeferred<Boolean>()
            activeCompletion = deferred

            val writeInitiated = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val writeType = if (request.isEmergency) {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    } else {
                        // Use WRITE_TYPE_DEFAULT for reliable confirmed delivery
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    val res = gatt.writeCharacteristic(characteristic, request.payload, writeType)
                    res == BluetoothGatt.GATT_SUCCESS || res == 0
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = request.payload
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
            } catch (e: SecurityException) {
                onLog("SecurityException writing command '${request.command}': ${e.message}", true)
                false
            } catch (e: Exception) {
                onLog("Exception writing command '${request.command}': ${e.message}", true)
                false
            }

            if (!writeInitiated) {
                onLog("Failed to initiate GATT write for '${request.command}'", true)
                activeCompletion = null
                request.completion.complete(false)
                return@withLock
            }

            // Wait for GATT callback with timeout
            val success = withTimeoutOrNull(BleConstants.GATT_WRITE_TIMEOUT_MS) {
                deferred.await()
            } ?: false

            activeCompletion = null
            if (!success) {
                onLog("GATT write timed out or rejected for '${request.command}'", true)
            }
            request.completion.complete(success)
        }
    }

    /**
     * Called by BluetoothGattCallback.onCharacteristicWrite
     */
    fun onWriteCompleted(status: Int) {
        val isOk = status == BluetoothGatt.GATT_SUCCESS
        activeCompletion?.complete(isOk)
    }

    /**
     * Enqueues a command for serialized transmission.
     * Returns true if successfully delivered & confirmed by GATT layer.
     */
    suspend fun enqueue(command: String, isEmergency: Boolean = false): Boolean {
        val payload = (command + "\n").toByteArray(Charsets.UTF_8)
        val completion = CompletableDeferred<Boolean>()
        val request = WriteRequest(
            command = command,
            payload = payload,
            isEmergency = isEmergency,
            completion = completion
        )
        queueChannel.send(request)
        return completion.await()
    }

    /**
     * Clears all pending commands (e.g. on disconnect or Emergency Stop).
     */
    fun clearQueue(reason: String) {
        scope.launch {
            queueMutex.withLock {
                activeCompletion?.complete(false)
                activeCompletion = null
                // Drain channel
                var drainedCount = 0
                while (true) {
                    val item = queueChannel.tryReceive().getOrNull() ?: break
                    item.completion.complete(false)
                    drainedCount++
                }
                if (drainedCount > 0) {
                    onLog("Cleared $drainedCount pending commands from queue ($reason)", false)
                }
            }
        }
    }

    fun release() {
        workerJob?.cancel()
        workerJob = null
        clearQueue("Release")
        activeGattRef.set(null)
        activeCharRef.set(null)
    }
}

