package com.oddbox.armcontroller.domain.model

enum class ArmAxis(val displayName: String, val minAngle: Int, val maxAngle: Int) {
    BASE("Base", 0, 180),
    SHOULDER("Shoulder", 15, 165),
    WRIST("Wrist", 0, 180)
}

