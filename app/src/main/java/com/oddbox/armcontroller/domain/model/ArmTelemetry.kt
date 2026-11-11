package com.oddbox.armcontroller.domain.model

data class ArmTelemetry(
    val baseAngle: Int? = null,
    val shoulderAngle: Int? = null,
    val wristAngle: Int? = null,
    val magnetState: MagnetState = MagnetState.UNKNOWN,
    val isConfirmed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val rawPacket: String = ""
) {
    companion object {
        val UNINITIALIZED = ArmTelemetry()

        /**
         * Parses status string from ESP32 telemetry characteristic.
         * Expected format: "BASE:90,SHOULDER:90,WRIST:90,MAGNET:OFF"
         */
        fun parse(raw: String): ArmTelemetry? {
            if (raw.isBlank()) return null
            val trimmed = raw.trim()
            var base: Int? = null
            var shoulder: Int? = null
            var wrist: Int? = null
            var magnet: MagnetState = MagnetState.UNKNOWN

            val tokens = trimmed.split(",")
            for (token in tokens) {
                val pair = token.split(":")
                if (pair.size == 2) {
                    val key = pair[0].trim().uppercase()
                    val value = pair[1].trim()
                    when (key) {
                        "BASE" -> base = value.toIntOrNull()
                        "SHOULDER" -> shoulder = value.toIntOrNull()
                        "WRIST" -> wrist = value.toIntOrNull()
                        "MAGNET" -> magnet = MagnetState.fromString(value)
                    }
                }
            }

            return ArmTelemetry(
                baseAngle = base,
                shoulderAngle = shoulder,
                wristAngle = wrist,
                magnetState = magnet,
                isConfirmed = true,
                timestamp = System.currentTimeMillis(),
                rawPacket = trimmed
            )
        }
    }
}

