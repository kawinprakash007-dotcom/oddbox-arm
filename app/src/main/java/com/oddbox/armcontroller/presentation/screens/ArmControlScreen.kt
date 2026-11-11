package com.oddbox.armcontroller.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oddbox.armcontroller.data.model.BleConnectionState
import com.oddbox.armcontroller.domain.model.ArmAxis
import com.oddbox.armcontroller.domain.model.ArmDirection
import com.oddbox.armcontroller.presentation.components.ConnectionBadge
import com.oddbox.armcontroller.presentation.components.EmergencyStopButton
import com.oddbox.armcontroller.presentation.components.LogConsole
import com.oddbox.armcontroller.presentation.components.MagnetControl
import com.oddbox.armcontroller.presentation.components.ServoStatusCard
import com.oddbox.armcontroller.presentation.components.SpeedSelector
import com.oddbox.armcontroller.presentation.theme.BackgroundDark
import com.oddbox.armcontroller.presentation.theme.ButtonInactive
import com.oddbox.armcontroller.presentation.theme.DeepBlue
import com.oddbox.armcontroller.presentation.theme.ElectricBlue
import com.oddbox.armcontroller.presentation.theme.EmergencyRed
import com.oddbox.armcontroller.presentation.theme.TextDim
import com.oddbox.armcontroller.presentation.theme.TextMuted
import com.oddbox.armcontroller.presentation.theme.TextWhite
import com.oddbox.armcontroller.presentation.theme.WarningAmber
import com.oddbox.armcontroller.presentation.viewmodel.ArmControlUiState
import com.oddbox.armcontroller.presentation.viewmodel.ArmControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmControlScreen(
    viewModel: ArmControlViewModel,
    uiState: ArmControlUiState,
    onRequestPermissions: () -> Unit
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Safety net: leaving/disposing the control screen (e.g. navigation) stops movement immediately.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onMovementCancelled()
        }
    }

    LaunchedEffect(uiState.statusBanner) {
        val banner = uiState.statusBanner
        if (banner != null) {
            snackbarHostState.showSnackbar(banner)
            viewModel.clearStatusBanner()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = DeepBlue
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PrecisionManufacturing,
                                    contentDescription = "Logo",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ODDBOX ARM CONTROLLER",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = TextWhite
                            )
                            Text(
                                text = "3-AXIS PICK & PLACE SYSTEM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = ElectricBlue
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setShowSettingsDialog(true) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextWhite
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 1. Connection Header Badge
            ConnectionBadge(
                connectionState = uiState.connectionState,
                deviceName = uiState.connectedDeviceName,
                deviceAddress = uiState.connectedDeviceAddress,
                isScanning = uiState.isScanning,
                onScanClick = {
                    onRequestPermissions()
                    viewModel.startScan()
                },
                onDisconnectClick = {
                    viewModel.disconnectDevice()
                }
            )

            // Warning Notice if not connected
            AnimatedVisibility(visible = !uiState.isConnected) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = WarningAmber.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠ Arm is currently disconnected. Controls are disabled until connected to ODDBOX_ARM over Bluetooth LE.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WarningAmber
                        )
                    }
                }
            }

            // 2. Emergency Stop (ESTOP) - High visibility at the top of operations
            EmergencyStopButton(
                isEmergencyStopped = uiState.isEmergencyStopped,
                onEmergencyStop = { viewModel.triggerEmergencyStop() },
                onResetEmergencyStop = { viewModel.resetEmergencyStop() }
            )

            // 3. Movement Speed Selection
            SpeedSelector(
                selectedSpeed = uiState.selectedSpeed,
                enabled = uiState.isControlsEnabled,
                onSpeedSelected = { viewModel.setSpeed(it) }
            )

            // 4. Base Rotation Panel (MG996R)
            ServoStatusCard(
                axis = ArmAxis.BASE,
                servoModel = "MG996R Channel 0",
                currentAngle = uiState.telemetry.baseAngle,
                isConfirmed = uiState.telemetry.isConfirmed,
                primaryDirection = ArmDirection.BASE_LEFT,
                secondaryDirection = ArmDirection.BASE_RIGHT,
                primaryIcon = Icons.AutoMirrored.Filled.ArrowBack,
                secondaryIcon = Icons.AutoMirrored.Filled.ArrowForward,
                activeDirection = uiState.activeDirection,
                enabled = uiState.isControlsEnabled,
                onDirectionPress = { viewModel.onDirectionPress(it) },
                onDirectionRelease = { viewModel.onDirectionRelease(it) }
            )

            // 5. Shoulder Panel (MG996R)
            ServoStatusCard(
                axis = ArmAxis.SHOULDER,
                servoModel = "MG996R Channel 1",
                currentAngle = uiState.telemetry.shoulderAngle,
                isConfirmed = uiState.telemetry.isConfirmed,
                primaryDirection = ArmDirection.SHOULDER_UP,
                secondaryDirection = ArmDirection.SHOULDER_DOWN,
                primaryIcon = Icons.Default.ArrowUpward,
                secondaryIcon = Icons.Default.ArrowDownward,
                activeDirection = uiState.activeDirection,
                enabled = uiState.isControlsEnabled,
                onDirectionPress = { viewModel.onDirectionPress(it) },
                onDirectionRelease = { viewModel.onDirectionRelease(it) }
            )

            // 6. Wrist Panel (SG90)
            ServoStatusCard(
                axis = ArmAxis.WRIST,
                servoModel = "SG90 Channel 2",
                currentAngle = uiState.telemetry.wristAngle,
                isConfirmed = uiState.telemetry.isConfirmed,
                primaryDirection = ArmDirection.WRIST_UP,
                secondaryDirection = ArmDirection.WRIST_DOWN,
                primaryIcon = Icons.Default.ArrowUpward,
                secondaryIcon = Icons.Default.ArrowDownward,
                activeDirection = uiState.activeDirection,
                enabled = uiState.isControlsEnabled,
                onDirectionPress = { viewModel.onDirectionPress(it) },
                onDirectionRelease = { viewModel.onDirectionRelease(it) }
            )

            // 7. Electromagnet Panel (XY-MOS MOSFET 12V)
            MagnetControl(
                magnetState = uiState.telemetry.magnetState,
                isSafetyUnlocked = uiState.isMagnetSafetyUnlocked,
                enabled = uiState.isControlsEnabled,
                onSafetyToggle = { viewModel.toggleMagnetSafety(it) },
                onSetMagnetState = { viewModel.setMagnetState(it) }
            )

            // 8. Debug & Event Log Console
            LogConsole(
                logs = uiState.logs,
                onClearLogs = { viewModel.clearLogs() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Dialogs
        if (uiState.showScanDialog) {
            BleScanDialog(
                scannedDevices = uiState.scannedDevices,
                isScanning = uiState.isScanning,
                onDismiss = { viewModel.setShowScanDialog(false) },
                onStartScan = {
                    onRequestPermissions()
                    viewModel.startScan()
                },
                onStopScan = { viewModel.stopScan() },
                onConnect = { address ->
                    viewModel.connectDevice(address)
                }
            )
        }

        if (uiState.showSettingsDialog) {
            SettingsDialog(
                heartbeatIntervalMs = uiState.heartbeatIntervalMs,
                onHeartbeatChanged = { viewModel.updateHeartbeatInterval(it) },
                onDismiss = { viewModel.setShowSettingsDialog(false) }
            )
        }
    }
}

