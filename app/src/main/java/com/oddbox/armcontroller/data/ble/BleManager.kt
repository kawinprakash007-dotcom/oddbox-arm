package com.oddbox.armcontroller.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.oddbox.armcontroller.data.model.BleConnectionState
import com.oddbox.armcontroller.data.model.BleLogEntry
import com.oddbox.armcontroller.data.model.BleLogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class BleManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val scope: CoroutineScope
) {
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _rawStatusFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val rawStatusFlow: SharedFlow<String> = _rawStatusFlow.asSharedFlow()

    private val _logsFlow = MutableStateFlow<List<BleLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<BleLogEntry>> = _logsFlow.asStateFlow()

    val commandQueue = BleCommandQueue(scope) { msg, isError ->
        log(if (isError) BleLogType.ERROR else BleLogType.TX_CMD, msg)
    }

    private var activeGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null

    private var targetDeviceAddress: String? = null
    private val reconnectAttempts = AtomicInteger(0)
    private var autoReconnectJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun log(type: BleLogType, message: String, details: String? = null) {
        val entry = BleLogEntry(
            type = type,
            message = message,
            details = details
        )
        _logsFlow.update { current ->
            (current + entry).takeLast(200) // Keep last 200 logs
        }
    }

    fun clearLogs() {
        _logsFlow.value = emptyList()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val address = gatt?.device?.address ?: targetDeviceAddress ?: "Unknown"
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(BleLogType.ERROR, "GATT connection status error: $status on $address")
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log(BleLogType.CONNECTION, "Connected to GATT server ($address). Discovering services...")
                    _connectionState.value = BleConnectionState.CONNECTING
                    reconnectAttempts.set(0)

                    // Brief delay before service discovery ensures GATT stability on diverse Android chipsets
                    mainHandler.postDelayed({
                        try {
                            gatt?.discoverServices()
                        } catch (e: SecurityException) {
                            log(BleLogType.ERROR, "SecurityException during discoverServices: ${e.message}")
                            disconnect()
                        }
                    }, 300)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    log(BleLogType.CONNECTION, "Disconnected from GATT server ($address)")
                    handleDisconnection()
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = BleConnectionState.CONNECTING
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    _connectionState.value = BleConnectionState.DISCONNECTING
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                log(BleLogType.ERROR, "Service discovery failed with status: $status")
                disconnect()
                return
            }

            log(BleLogType.CONNECTION, "Services discovered. Resolving ODDBOX_ARM service & characteristics...")
            val service = gatt.getService(BleConstants.SERVICE_UUID)
            if (service == null) {
                log(BleLogType.ERROR, "ODDBOX Service (${BleConstants.SERVICE_UUID}) NOT found on device!")
                disconnect()
                return
            }

            commandCharacteristic = service.getCharacteristic(BleConstants.COMMAND_CHAR_UUID)
            statusCharacteristic = service.getCharacteristic(BleConstants.STATUS_CHAR_UUID)

            if (commandCharacteristic == null) {
                log(BleLogType.ERROR, "Command characteristic (${BleConstants.COMMAND_CHAR_UUID}) NOT found!")
                disconnect()
                return
            }

            // Bind command queue before anything else so commands can flow
            commandQueue.bind(gatt, commandCharacteristic)

            if (statusCharacteristic == null) {
                // Firmware does not (yet) expose the status characteristic — that is OK.
                // We can still send commands; we just won't receive telemetry.
                log(BleLogType.CONNECTION, "Status characteristic (${BleConstants.STATUS_CHAR_UUID}) not found — " +
                        "command-only mode. Flash updated firmware to enable telemetry.")
                finalizeConnection(gatt)
                return
            }

            // Subscribe to status characteristic notifications
            subscribeToStatusNotifications(gatt, statusCharacteristic!!)
        }

        @SuppressLint("MissingPermission")
        private fun subscribeToStatusNotifications(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val success = gatt.setCharacteristicNotification(characteristic, true)
            if (!success) {
                log(BleLogType.ERROR, "Failed to enable local notifications for status characteristic")
                return
            }

            val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
            if (descriptor == null) {
                log(BleLogType.ERROR, "CCCD descriptor (0x2902) not found on status characteristic!")
                finalizeConnection(gatt)
                return
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                log(BleLogType.CONNECTION, "CCCD write initiated — awaiting onDescriptorWrite callback...")
                // finalizeConnection is called in onDescriptorWrite (see below)
            } catch (e: Exception) {
                log(BleLogType.ERROR, "Exception writing CCCD descriptor: ${e.message}")
                // Fall back: still declare connected so user can send commands
                finalizeConnection(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        private fun finalizeConnection(gatt: BluetoothGatt) {
            _connectionState.value = BleConnectionState.CONNECTED
            val name = try { gatt.device.name } catch (_: SecurityException) { null } ?: BleConstants.DEVICE_NAME
            _connectedDeviceName.value = name
            _connectedDeviceAddress.value = gatt.device.address
            log(BleLogType.CONNECTION, "ODDBOX Arm Controller is READY & CONNECTED")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            commandQueue.onWriteCompleted(status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(BleLogType.ERROR, "GATT write failed with status $status")
            }
        }

        // For Android 12 and below
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == BleConstants.STATUS_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: return
                handleIncomingTelemetry(data)
            }
        }

        // For Android 13+ (API 33+)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == BleConstants.STATUS_CHAR_UUID) {
                handleIncomingTelemetry(value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (gatt == null) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log(BleLogType.CONNECTION, "CCCD write confirmed — status notifications active")
            } else {
                log(BleLogType.ERROR, "CCCD descriptor write failed (status $status) — continuing without telemetry")
            }
            // Finalize connection regardless: if CCCD fails we still want command-sending to work
            if (_connectionState.value != BleConnectionState.CONNECTED) {
                finalizeConnection(gatt)
            }
        }
    }

    private fun handleIncomingTelemetry(bytes: ByteArray) {
        val text = String(bytes, Charsets.UTF_8).trim()
        if (text.isNotEmpty()) {
            log(BleLogType.RX_STATUS, "TELEMETRY: $text")
            _rawStatusFlow.tryEmit(text)
        }
    }

    private fun handleDisconnection() {
        commandQueue.clearQueue("Disconnected")
        commandQueue.bind(null, null)
        commandCharacteristic = null
        statusCharacteristic = null

        activeGatt?.close()
        activeGatt = null

        _connectionState.value = BleConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _connectedDeviceAddress.value = null

        log(BleLogType.CONNECTION, "Arm disconnected. Command queue purged.")
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            log(BleLogType.ERROR, "Cannot connect: Bluetooth is disabled")
            return false
        }

        targetDeviceAddress = address
        _connectionState.value = BleConnectionState.CONNECTING
        log(BleLogType.CONNECTION, "Connecting to device $address...")

        try {
            val device = bluetoothAdapter.getRemoteDevice(address)
            activeGatt?.close()
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
            return true
        } catch (e: SecurityException) {
            log(BleLogType.ERROR, "Bluetooth connect permission missing: ${e.message}")
            _connectionState.value = BleConnectionState.DISCONNECTED
            return false
        } catch (e: Exception) {
            log(BleLogType.ERROR, "Exception during connectGatt: ${e.message}")
            _connectionState.value = BleConnectionState.DISCONNECTED
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        _connectionState.value = BleConnectionState.DISCONNECTING
        log(BleLogType.CONNECTION, "Disconnecting from arm...")
        commandQueue.clearQueue("User disconnect")
        try {
            activeGatt?.disconnect()
        } catch (e: Exception) {
            log(BleLogType.ERROR, "Error during disconnect: ${e.message}")
            handleDisconnection()
        }
    }

    /**
     * Transmit command through the serialized GATT write queue.
     */
    suspend fun sendCommand(command: String, isEmergency: Boolean = false): Boolean {
        if (_connectionState.value != BleConnectionState.CONNECTED) {
            log(BleLogType.ERROR, "Cannot send '$command': Device not connected")
            return false
        }
        val sent = commandQueue.enqueue(command, isEmergency)
        if (sent) {
            log(BleLogType.TX_CMD, "TX: $command")
        } else {
            log(BleLogType.ERROR, "TX FAILED: $command")
        }
        return sent
    }

    fun release() {
        disconnect()
        commandQueue.release()
    }
}

