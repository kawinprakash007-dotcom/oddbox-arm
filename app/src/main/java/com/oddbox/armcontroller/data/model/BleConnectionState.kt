package com.oddbox.armcontroller.data.model

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING;

    val isConnected: Boolean
        get() = this == CONNECTED

    val isBusy: Boolean
        get() = this == SCANNING || this == CONNECTING || this == DISCONNECTING
}

