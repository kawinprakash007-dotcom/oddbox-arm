package com.oddbox.armcontroller.data.ble

import java.util.UUID

object BleConstants {
    // BLE Advertised Device Name
    const val DEVICE_NAME = "ODDBOX_ARM"

    // Primary Service UUID
    val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")

    // Command Characteristic (Write / Write Without Response)
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ac")

    // Status / Telemetry Characteristic (Notify / Read)
    val STATUS_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ad")

    // Standard Client Characteristic Configuration Descriptor (CCCD)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Timing Constants
    const val ESP32_COMMAND_TIMEOUT_MS = 600L     // Firmware drops movement if no refresh within 600ms
    const val HEARTBEAT_INTERVAL_MS = 200L        // Safe repeat interval while button held
    const val GATT_WRITE_TIMEOUT_MS = 1000L       // Per-command GATT response timeout
    const val BLE_SCAN_PERIOD_MS = 12000L         // Scan stops automatically after 12s
    const val RECONNECT_DELAY_MS = 3000L          // Reconnect delay
    const val MAX_RECONNECT_ATTEMPTS = 3
}

