package com.oddbox.armcontroller

import com.oddbox.armcontroller.domain.model.ArmAxis
import com.oddbox.armcontroller.domain.model.ArmCommand
import com.oddbox.armcontroller.domain.model.ArmDirection
import com.oddbox.armcontroller.domain.model.ArmSpeed
import com.oddbox.armcontroller.domain.model.ArmTelemetry
import com.oddbox.armcontroller.domain.model.MagnetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmTelemetryTest {

    @Test
    fun testParseStandardTelemetry() {
        val raw = "BASE:90,SHOULDER:90,WRIST:90,MAGNET:OFF"
        val telemetry = ArmTelemetry.parse(raw)

        assertNotNull(telemetry)
        assertEquals(90, telemetry?.baseAngle)
        assertEquals(90, telemetry?.shoulderAngle)
        assertEquals(90, telemetry?.wristAngle)
        assertEquals(MagnetState.OFF, telemetry?.magnetState)
        assertTrue(telemetry?.isConfirmed == true)
    }

    @Test
    fun testParseActiveMagnetAndVariedAngles() {
        val raw = "BASE:180,SHOULDER:120,WRIST:45,MAGNET:ON"
        val telemetry = ArmTelemetry.parse(raw)

        assertNotNull(telemetry)
        assertEquals(180, telemetry?.baseAngle)
        assertEquals(120, telemetry?.shoulderAngle)
        assertEquals(45, telemetry?.wristAngle)
        assertEquals(MagnetState.ON, telemetry?.magnetState)
    }

    @Test
    fun testParseWithWhitespaceAndCaseInsensitivity() {
        val raw = " base: 75 , shoulder: 85 , wrist: 110 , magnet: on "
        val telemetry = ArmTelemetry.parse(raw)

        assertNotNull(telemetry)
        assertEquals(75, telemetry?.baseAngle)
        assertEquals(85, telemetry?.shoulderAngle)
        assertEquals(110, telemetry?.wristAngle)
        assertEquals(MagnetState.ON, telemetry?.magnetState)
    }

    @Test
    fun testParseEmptyOrInvalidString() {
        assertNull(ArmTelemetry.parse(""))
        assertNull(ArmTelemetry.parse("   "))
        
        val garbage = ArmTelemetry.parse("NOT_A_VALID_PACKET")
        assertNotNull(garbage)
        assertNull(garbage?.baseAngle)
        assertNull(garbage?.shoulderAngle)
        assertNull(garbage?.wristAngle)
        assertEquals(MagnetState.UNKNOWN, garbage?.magnetState)
    }

    @Test
    fun testProtocolCommandsIntegrity() {
        assertEquals("BASE_LEFT", ArmCommand.BASE_LEFT)
        assertEquals("BASE_RIGHT", ArmCommand.BASE_RIGHT)
        assertEquals("SHOULDER_UP", ArmCommand.SHOULDER_UP)
        assertEquals("SHOULDER_DOWN", ArmCommand.SHOULDER_DOWN)
        assertEquals("WRIST_UP", ArmCommand.WRIST_UP)
        assertEquals("WRIST_DOWN", ArmCommand.WRIST_DOWN)
        assertEquals("STOP", ArmCommand.STOP)
        assertEquals("MAGNET_ON", ArmCommand.MAGNET_ON)
        assertEquals("MAGNET_OFF", ArmCommand.MAGNET_OFF)
        assertEquals("ESTOP", ArmCommand.ESTOP)
        assertEquals("SPEED:1", ArmCommand.SPEED_1)
        assertEquals("SPEED:2", ArmCommand.SPEED_2)
        assertEquals("SPEED:3", ArmCommand.SPEED_3)
    }

    @Test
    fun testDirectionMapping() {
        assertEquals(ArmAxis.BASE, ArmDirection.BASE_LEFT.axis)
        assertEquals("BASE_LEFT", ArmDirection.BASE_LEFT.command)
        assertEquals(ArmAxis.SHOULDER, ArmDirection.SHOULDER_UP.axis)
        assertEquals("SHOULDER_UP", ArmDirection.SHOULDER_UP.command)
        assertEquals(ArmAxis.WRIST, ArmDirection.WRIST_DOWN.axis)
        assertEquals("WRIST_DOWN", ArmDirection.WRIST_DOWN.command)
    }

    @Test
    fun testSpeedCommands() {
        assertEquals("SPEED:1", ArmSpeed.SLOW.command)
        assertEquals("SPEED:2", ArmSpeed.MEDIUM.command)
        assertEquals("SPEED:3", ArmSpeed.FAST.command)
    }
}

