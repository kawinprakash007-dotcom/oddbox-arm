package com.oddbox.armcontroller.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.oddbox.armcontroller.data.model.BleDeviceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BleScanner(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val scope: CoroutineScope,
    private val onLog: (String, Boolean) -> Unit
) {
    private val _scannedDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceItem>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var leScanner: BluetoothLeScanner? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = try {
                device.name
            } catch (e: SecurityException) {
                null
            } ?: result.scanRecord?.deviceName

            val address = device.address ?: return
            val rssi = result.rssi
            val isOddbox = name?.equals(BleConstants.DEVICE_NAME, ignoreCase = true) == true ||
                    result.scanRecord?.serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID } == true

            val item = BleDeviceItem(
                name = name,
                address = address,
                rssi = rssi,
                isOddboxArm = isOddbox,
                lastSeenTimestamp = System.currentTimeMillis()
            )

            _scannedDevices.update { current ->
                val list = current.filterNot { it.address == address }.toMutableList()
                list.add(item)
                // Sort with ODDBOX_ARM first, then by strongest RSSI
                list.sortedWith(
                    compareByDescending<BleDeviceItem> { it.isOddboxArm }
                        .thenByDescending { it.rssi }
                )
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            onLog("BLE Scan failed with error code: $errorCode", true)
        }
    }

    private val stopScanRunnable = Runnable {
        stopScan()
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onLog("Cannot scan: Bluetooth adapter is disabled or unavailable", true)
            return false
        }

        if (_isScanning.value) {
            return true
        }

        leScanner = bluetoothAdapter.bluetoothLeScanner
        if (leScanner == null) {
            onLog("Bluetooth LE Scanner not available on this device", true)
            return false
        }

        _scannedDevices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return try {
            leScanner?.startScan(null, settings, scanCallback)
            _isScanning.value = true
            handler.postDelayed(stopScanRunnable, BleConstants.BLE_SCAN_PERIOD_MS)
            onLog("BLE scan started (searching for ${BleConstants.DEVICE_NAME})...", false)
            true
        } catch (e: SecurityException) {
            onLog("Scan permission missing: ${e.message}", true)
            _isScanning.value = false
            false
        } catch (e: Exception) {
            onLog("Scan start exception: ${e.message}", true)
            _isScanning.value = false
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        handler.removeCallbacks(stopScanRunnable)
        if (_isScanning.value) {
            try {
                leScanner?.stopScan(scanCallback)
            } catch (_: Exception) {}
            _isScanning.value = false
            onLog("BLE scan stopped", false)
        }
    }

    fun release() {
        stopScan()
        _scannedDevices.value = emptyList()
    }
}

