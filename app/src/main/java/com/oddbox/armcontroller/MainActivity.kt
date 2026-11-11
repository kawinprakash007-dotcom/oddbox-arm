package com.oddbox.armcontroller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.oddbox.armcontroller.presentation.screens.ArmControlScreen
import com.oddbox.armcontroller.presentation.theme.BackgroundDark
import com.oddbox.armcontroller.presentation.theme.OddboxArmTheme
import com.oddbox.armcontroller.presentation.viewmodel.ArmControlViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ArmControlViewModel by viewModels {
        ArmControlViewModel.Factory(application)
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.startScan()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to connect to the robotic arm.", Toast.LENGTH_LONG).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBluetoothAndScan()
        } else {
            Toast.makeText(
                this,
                "Bluetooth & Location permissions are required for BLE communication.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OddboxArmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    ArmControlScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onRequestPermissions = { requestRequiredPermissions() }
                    )
                }
            }
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    fun hasPermissions(): Boolean {
        return getRequiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestRequiredPermissions() {
        if (!hasPermissions()) {
            permissionLauncher.launch(getRequiredPermissions())
        } else {
            checkBluetoothAndScan()
        }
    }

    private fun checkBluetoothAndScan() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            Toast.makeText(this, "Bluetooth LE hardware is not available on this device.", Toast.LENGTH_LONG).show()
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                bluetoothEnableLauncher.launch(enableBtIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Please enable Bluetooth in Android settings.", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.startScan()
        }
    }

    override fun onPause() {
        super.onPause()
        // Safety requirement: losing foreground/resumed state halts physical movement immediately
        viewModel.onAppBackgrounded()
    }

    override fun onStop() {
        super.onStop()
        // Safety requirement: Backgrounding halts physical movement immediately
        viewModel.onAppBackgrounded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Notification shade / permission dialog / system overlay interrupts touch input
        if (!hasFocus) {
            viewModel.onMovementCancelled()
        }
    }
}

