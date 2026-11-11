package com.oddbox.armcontroller.data.model

data class BleDeviceItem(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isOddboxArm: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = when {
            !name.isNullOrBlank() -> name
            isOddboxArm -> "ODDBOX_ARM"
            else -> "Unknown BLE Device ($address)"
        }
}

