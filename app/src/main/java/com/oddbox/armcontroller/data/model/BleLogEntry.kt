package com.oddbox.armcontroller.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BleLogType {
    TX_CMD,
    RX_STATUS,
    CONNECTION,
    ERROR,
    SAFETY
}

data class BleLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: BleLogType,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

