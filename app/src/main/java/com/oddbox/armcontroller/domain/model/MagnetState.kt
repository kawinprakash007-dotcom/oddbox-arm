package com.oddbox.armcontroller.domain.model

enum class MagnetState {
    OFF,
    ON,
    UNKNOWN;

    companion object {
        fun fromString(value: String): MagnetState {
            return when (value.trim().uppercase()) {
                "ON", "1", "TRUE" -> ON
                "OFF", "0", "FALSE" -> OFF
                else -> UNKNOWN
            }
        }
    }
}

