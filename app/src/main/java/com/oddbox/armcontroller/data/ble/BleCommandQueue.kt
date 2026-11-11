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
    private val activeGattRef = java.util.concurrent.atomic.AtomicReference<BluetoothGatt?>(null)
    private val activeCharRef = java.util.concurrent.atomic.AtomicReference<BluetoothGattCharacteristic?>(null)

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
            // Use WRITE_TYPE_NO_RESPONSE — the ESP32 firmware command characteristic has
            // PROPERTY_WRITE | PROPERTY_WRITE_NR. With WRITE_TYPE_DEFAULT Android waits for a
            // GATT ATT confirmation that the ESP32 BLE stack never sends for WR-NR operations,
            // causing every write to time-out. WRITE_TYPE_NO_RESPONSE fires-and-forgets at the
            // ATT layer and onCharacteristicWrite is NOT guaranteed to be called — that is correct.
            val writeInitiated = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val res = gatt.writeCharacteristic(
                        characteristic,
                        request.payload,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                    res == BluetoothGatt.GATT_SUCCESS || res == 0
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = request.payload
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
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
                request.completion.complete(false)
                return@withLock
            }

            // For WRITE_TYPE_NO_RESPONSE the ATT layer sends no confirmation — complete immediately.
            // Add a small inter-write gap so the ESP32 BLE stack has time to process each packet.
            request.completion.complete(true)
            delay(BleConstants.NO_RESPONSE_INTER_WRITE_DELAY_MS)
        }
    }

    /**
     * Called by BluetoothGattCallback.onCharacteristicWrite — kept for API compatibility
     * but not relied upon for WRITE_TYPE_NO_RESPONSE operations.
     */
    fun onWriteCompleted(status: Int) {
        // No-op for WRITE_NR path; retained so BleManager.onCharacteristicWrite compiles cleanly.
    }

    /**
     * Enqueues a command for serialized transmission.
     * Returns true if the write was successfully initiated to the BLE stack.
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

