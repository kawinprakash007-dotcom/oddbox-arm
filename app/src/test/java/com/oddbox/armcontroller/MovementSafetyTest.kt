package com.oddbox.armcontroller

import com.oddbox.armcontroller.data.ble.BleCommandQueue
import com.oddbox.armcontroller.data.ble.BleDebugLog
import com.oddbox.armcontroller.domain.controller.MovementHeartbeatController
import com.oddbox.armcontroller.domain.model.ArmCommand
import com.oddbox.armcontroller.domain.model.ArmDirection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class MovementSafetyTest {

    private val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @After
    fun tearDown() {
        BleDebugLog.testListener = null
        testScope.cancel()
    }

    @Test
    fun testPressAndHoldSendsHeartbeatsAndReleaseSendsStopWithRequiredLogs() = runBlocking {
        val sentCommands = Collections.synchronizedList(mutableListOf<String>())
        val debugEvents = Collections.synchronizedList(mutableListOf<String>())
        val firstHeartbeatSent = CompletableDeferred<Unit>()
        val stopWritten = CompletableDeferred<Unit>()

        BleDebugLog.testListener = { msg -> debugEvents.add(msg) }

        val queue = BleCommandQueue(
            scope = testScope,
            customWriter = { payload ->
                val cmd = String(payload, Charsets.UTF_8).trim()
                sentCommands.add(cmd)
                if (cmd == ArmCommand.BASE_RIGHT && !firstHeartbeatSent.isCompleted) {
                    firstHeartbeatSent.complete(Unit)
                }
                if (cmd == ArmCommand.STOP) {
                    stopWritten.complete(Unit)
                }
                true
            },
            waitForWriteCallback = false,
            onLog = { _, _ -> }
        )
        queue.bind(null, null)

        val controller = MovementHeartbeatController(
            scope = testScope,
            sendMovementAction = { cmd, epoch -> queue.enqueueMovement(cmd, epoch) },
            queueStopImmediateAction = { reason -> queue.queueStopNow(reason) },
            invalidateMovementQueueAction = { queue.invalidatePendingMovement("Test") },
            currentEpochProvider = { queue.currentMovementEpoch() },
            heartbeatIntervalMs = 40L
        )

        controller.startMovement(ArmDirection.BASE_RIGHT)
        withTimeout(2000L) { firstHeartbeatSent.await() }
        // Let a couple of heartbeats fire
        kotlinx.coroutines.delay(110L)

        assertEquals(ArmDirection.BASE_RIGHT, controller.activeDirection.value)

        controller.stopMovement(ArmDirection.BASE_RIGHT)
        withTimeout(2000L) { stopWritten.await() }

        // Wait well past multiple heartbeat periods to verify nothing leaks after STOP
        kotlinx.coroutines.delay(150L)

        assertNull(controller.activeDirection.value)
        assertTrue(sentCommands.count { it == ArmCommand.BASE_RIGHT } >= 2)
        assertEquals(ArmCommand.STOP, sentCommands.last())

        // Verify required ODDBOX_BLE log events occurred in order
        assertTrue(debugEvents.any { it == "HEARTBEAT_SENT: BASE_RIGHT" })
        assertTrue(debugEvents.contains("HEARTBEAT_CANCELLED"))
        assertTrue(debugEvents.contains("STOP_QUEUED"))
        assertTrue(debugEvents.contains("STOP_SENT"))

        queue.release()
    }

    @Test
    fun testStopDrainsQueuedDirectionalCommandsAndBlocksPostStopLeaks() = runBlocking {
        val transmitted = Collections.synchronizedList(mutableListOf<String>())
        val stopWritten = CompletableDeferred<Unit>()

        val queue = BleCommandQueue(
            scope = testScope,
            customWriter = { payload ->
                val cmd = String(payload, Charsets.UTF_8).trim()
                transmitted.add(cmd)
                if (cmd == ArmCommand.STOP) {
                    stopWritten.complete(Unit)
                }
                true
            },
            waitForWriteCallback = false,
            onLog = { _, _ -> }
        )
        queue.bind(null, null)

        val staleEpoch = queue.currentMovementEpoch()
        // Pre-fill normal channel with movement commands, then immediately enqueue STOP.
        // Because enqueueStop calls invalidatePendingMovement synchronously, stale commands
        // must be drained and STOP must arrive last with zero movement afterwards.
        val deferred1 = async { queue.enqueueMovement(ArmCommand.SHOULDER_UP, staleEpoch) }
        val deferred2 = async { queue.enqueueMovement(ArmCommand.SHOULDER_UP, staleEpoch) }
        kotlinx.coroutines.delay(5L)

        val stopOk = queue.enqueueStop()
        withTimeout(2000L) { stopWritten.await() }

        // Attempting to enqueue with the stale epoch after STOP must be rejected immediately
        val postStopStaleAccepted = queue.enqueueMovement(ArmCommand.SHOULDER_UP, staleEpoch)
        deferred1.await()
        deferred2.await()
        kotlinx.coroutines.delay(40L)

        assertTrue(stopOk)
        assertFalse(postStopStaleAccepted)
        assertEquals(ArmCommand.STOP, transmitted.last())

        val stopIndex = transmitted.indexOfLast { it == ArmCommand.STOP }
        assertTrue(stopIndex >= 0)
        assertEquals(transmitted.size - 1, stopIndex)

        queue.release()
    }

    @Test
    fun testStopRetriesWhenGattTemporarilyBusy() = runBlocking {
        val attempts = AtomicInteger(0)
        val transmitted = Collections.synchronizedList(mutableListOf<String>())
        val stopWritten = CompletableDeferred<Unit>()

        val queue = BleCommandQueue(
            scope = testScope,
            customWriter = { payload ->
                val cmd = String(payload, Charsets.UTF_8).trim()
                if (cmd == ArmCommand.STOP) {
                    val count = attempts.incrementAndGet()
                    if (count < 3) {
                        // Simulate BluetoothGatt mDeviceBusy returning false on first 2 attempts
                        return@BleCommandQueue false
                    }
                    transmitted.add(cmd)
                    stopWritten.complete(Unit)
                    return@BleCommandQueue true
                }
                transmitted.add(cmd)
                true
            },
            waitForWriteCallback = false,
            onLog = { _, _ -> }
        )
        queue.bind(null, null)

        val ok = queue.enqueueStop()
        withTimeout(2000L) { stopWritten.await() }

        assertTrue(ok)
        assertEquals(3, attempts.get())
        assertEquals(listOf(ArmCommand.STOP), transmitted)

        queue.release()
    }

    @Test
    fun testConflictingDirectionsAndReleaseOfInactiveButton() = runBlocking {
        val transmitted = Collections.synchronizedList(mutableListOf<String>())
        val shoulderWritten = CompletableDeferred<Unit>()
        val finalStopWritten = CompletableDeferred<Unit>()
        val stopCount = AtomicInteger(0)

        val queue = BleCommandQueue(
            scope = testScope,
            customWriter = { payload ->
                val cmd = String(payload, Charsets.UTF_8).trim()
                transmitted.add(cmd)
                if (cmd == ArmCommand.SHOULDER_UP && !shoulderWritten.isCompleted) {
                    shoulderWritten.complete(Unit)
                }
                if (cmd == ArmCommand.STOP) {
                    val n = stopCount.incrementAndGet()
                    if (n >= 2 && !finalStopWritten.isCompleted) {
                        finalStopWritten.complete(Unit)
                    }
                }
                true
            },
            waitForWriteCallback = false,
            onLog = { _, _ -> }
        )
        queue.bind(null, null)

        val controller = MovementHeartbeatController(
            scope = testScope,
            sendMovementAction = { cmd, epoch -> queue.enqueueMovement(cmd, epoch) },
            queueStopImmediateAction = { reason -> queue.queueStopNow(reason) },
            invalidateMovementQueueAction = { queue.invalidatePendingMovement("Switch") },
            currentEpochProvider = { queue.currentMovementEpoch() },
            heartbeatIntervalMs = 50L
        )

        controller.startMovement(ArmDirection.BASE_LEFT)
        kotlinx.coroutines.delay(40L)

        // Switch immediately to SHOULDER_UP while BASE_LEFT was pressed
        controller.startMovement(ArmDirection.SHOULDER_UP)
        withTimeout(2000L) { shoulderWritten.await() }
        assertEquals(ArmDirection.SHOULDER_UP, controller.activeDirection.value)

        // Late release of BASE_LEFT must NOT stop active SHOULDER_UP movement
        controller.stopMovement(ArmDirection.BASE_LEFT)
        assertEquals(ArmDirection.SHOULDER_UP, controller.activeDirection.value)

        // Releasing SHOULDER_UP must stop immediately
        controller.stopMovement(ArmDirection.SHOULDER_UP)
        withTimeout(2000L) { finalStopWritten.await() }
        kotlinx.coroutines.delay(100L)

        assertNull(controller.activeDirection.value)
        assertEquals(ArmCommand.STOP, transmitted.last())

        queue.release()
    }

    @Test
    fun testImmediateForceStopAfterZeroDelayTap() = runBlocking {
        val transmitted = Collections.synchronizedList(mutableListOf<String>())
        val stopWritten = CompletableDeferred<Unit>()

        val queue = BleCommandQueue(
            scope = testScope,
            customWriter = { payload ->
                val cmd = String(payload, Charsets.UTF_8).trim()
                transmitted.add(cmd)
                if (cmd == ArmCommand.STOP) {
                    stopWritten.complete(Unit)
                }
                true
            },
            waitForWriteCallback = false,
            onLog = { _, _ -> }
        )
        queue.bind(null, null)

        val controller = MovementHeartbeatController(
            scope = testScope,
            sendMovementAction = { cmd, epoch -> queue.enqueueMovement(cmd, epoch) },
            queueStopImmediateAction = { reason -> queue.queueStopNow(reason) },
            invalidateMovementQueueAction = { queue.invalidatePendingMovement("Tap") },
            currentEpochProvider = { queue.currentMovementEpoch() },
            heartbeatIntervalMs = 30L
        )

        // Press and immediately force-stop (0ms hold — worst-case cancellation race)
        controller.startMovement(ArmDirection.WRIST_UP)
        controller.forceStop("Immediate cancellation")

        withTimeout(2000L) { stopWritten.await() }
        kotlinx.coroutines.delay(100L)

        assertNull(controller.activeDirection.value)
        assertEquals(ArmCommand.STOP, transmitted.last())

        queue.release()
    }
}
