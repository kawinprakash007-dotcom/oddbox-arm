package com.oddbox.armcontroller.domain.model

enum class ArmSpeed(val command: String, val label: String, val level: Int) {
    SLOW("SPEED:1", "SLOW", 1),
    MEDIUM("SPEED:2", "MEDIUM", 2),
    FAST("SPEED:3", "FAST", 3)
}

