package com.oddbox.armcontroller.domain.model

enum class ArmDirection(val command: String, val axis: ArmAxis, val label: String) {
    BASE_LEFT("BASE_LEFT", ArmAxis.BASE, "LEFT"),
    BASE_RIGHT("BASE_RIGHT", ArmAxis.BASE, "RIGHT"),
    SHOULDER_UP("SHOULDER_UP", ArmAxis.SHOULDER, "UP"),
    SHOULDER_DOWN("SHOULDER_DOWN", ArmAxis.SHOULDER, "DOWN"),
    WRIST_UP("WRIST_UP", ArmAxis.WRIST, "WRIST UP"),
    WRIST_DOWN("WRIST_DOWN", ArmAxis.WRIST, "WRIST DOWN")
}

