# ODDBOX ARM CONTROLLER
### Professional Native Android Application for 3-Axis Robotic Arm & Electromagnet BLE Control

The **ODDBOX Arm Controller** is a native Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3**, architected to provide real-time, low-latency Bluetooth Low Energy (BLE) control of a physical three-axis robotic arm with an electromagnet end-effector via an ESP32 microcontroller.

---

## 1. System Architecture & Hardware Setup

### Physical Topology
```
[Android Phone: ODDBOX ARM CONTROLLER]
                 |
             (BLE Link)
                 v
         [ESP32 DevKit V1]
          |             |
     (I2C Bus)      (GPIO 23)
          v             v
    [PCA9685 PWM]   [XY-MOS MOSFET Module]
      |   |   |         |
     CH0 CH1 CH2       12V DC
      |   |   |         |
    Base Shldr Wrist   [Electromagnet]
    MG996 MG996 SG90
```

### Hardware Components
1. **ESP32 DevKit V1**: BLE peripheral server, command deserializer, and hardware safety watchdog.
2. **PCA9685 16-Channel 12-bit PWM Driver**: Connected via I2C (`SDA -> GPIO 21`, `SCL -> GPIO 22`).
   - **Channel 0**: Base Rotation — TowerPro MG996R (0° to 180° range).
   - **Channel 1**: Shoulder Elevation — TowerPro MG996R (15° to 165° range).
   - **Channel 2**: Wrist Pitch — TowerPro SG90 (0° to 180° range).
3. **XY-MOS MOSFET Switch**: Connected to ESP32 `GPIO 23` to switch high-current 12V DC power to the picking electromagnet.
4. **Isolated Power Supplies**:
   - **5V Regulated Supply (3A+)**: Dedicated to the PCA9685 V+ power terminal for servos.
   - **12V DC Regulated Supply**: Dedicated to the electromagnet through the XY-MOS module.
   - **Common Ground (GND)**: All grounds (ESP32, PCA9685, 5V, 12V) are bonded together.

---

## 2. BLE Communication Protocol

| Parameter | Value |
|---|---|
| **Advertised Device Name** | `ODDBOX_ARM` |
| **Primary Service UUID** | `12345678-1234-1234-1234-1234567890ab` |
| **Command Characteristic (RX)** | `12345678-1234-1234-1234-1234567890ac` (Write / Write Without Response) |
| **Status Characteristic (TX)** | `12345678-1234-1234-1234-1234567890ad` (Notify / Read) |
| **CCCD Descriptor UUID** | `00002902-0000-1000-8000-00805f9b34fb` |

### Command Set
- `BASE_LEFT` / `BASE_RIGHT` — Incremental base rotation
- `SHOULDER_UP` / `SHOULDER_DOWN` — Incremental shoulder elevation
- `WRIST_UP` / `WRIST_DOWN` — Incremental wrist tilt
- `STOP` — Halts any active axis movement immediately
- `MAGNET_ON` — Energizes 12V electromagnet (requires Safety Interlock unlock)
- `MAGNET_OFF` — De-energizes electromagnet
- `ESTOP` — Emergency Stop: immediate halt of all motors + magnet power cutoff
- `SPEED:1` / `SPEED:2` / `SPEED:3` — Sets movement step multiplier (Slow / Medium / Fast)

### Telemetry Feedback Format
Incoming notifications from the ESP32 Status Characteristic:
```
BASE:90,SHOULDER:90,WRIST:90,MAGNET:OFF
```
The application parses these packets and updates the live angle gauges and magnet status indicators with verified confirmation badges.

---

## 3. Industrial Safety & Reliability Implementation

1. **Press-and-Hold Heartbeat Mechanism**:
   - Firmware watchdog halts movement if no heartbeat packet is received within **600ms**.
   - The app transmits continuous movement heartbeats every **200ms** while a direction button is held down.
   - Upon finger release, touch cancel, app backgrounding, or navigation, a `STOP` command is dispatched immediately.
2. **Conflicting Direction Lockout**:
   - An internal coroutine Mutex enforces that only **one single axis/direction** can be energized at a time.
3. **Serialized GATT Write Command Queue**:
   - Prevents Android BLE GATT busy crashes (`GATT_BUSY` / Error 133). Every transmission is serialized and awaited.
   - Disconnections or E-STOP immediately purge all stale queue items.
4. **Dual-Action Electromagnet Safety**:
   - High-voltage 12V coil cannot be activated accidentally.
   - Requires explicit operator activation of the **Safety Interlock** switch before the `MAGNET ON` button is unlocked.
   - High-visibility pulsing hazard banner indicates whenever coil is energized.
5. **Emergency Stop (ESTOP)**:
   - High-visibility red button dispatches `ESTOP` and `MAGNET_OFF`.
   - Software locks controls until the operator performs a deliberate system reset.

---

## 4. Project Architecture

The codebase adheres strictly to **Clean MVVM** architecture:

```
com.oddbox.armcontroller
├── data
│   ├── ble
│   │   ├── BleCommandQueue.kt       // Thread-safe serialized GATT write queue
│   │   ├── BleConstants.kt          // Protocol UUIDs and timing configurations
│   │   ├── BleManager.kt            // Connection management, CCCD notifications, callbacks
│   │   └── BleScanner.kt            // Low-latency BLE peripheral discovery
│   ├── model
│   │   ├── BleConnectionState.kt    // Disconnected / Scanning / Connecting / Connected
│   │   ├── BleDeviceItem.kt         // Scanned BLE device with RSSI & filtering
│   │   └── BleLogEntry.kt           // Diagnostic logging model with timestamps
│   └── repository
│       ├── ArmBleRepository.kt      // Primary repository wrapping BLE layer & telemetry parser
│       └── BleRepository.kt         // Repository abstraction interface
├── domain
│   ├── controller
│   │   ├── ArmSafetyManager.kt      // E-STOP lock & Electromagnet safety enforcement
│   │   └── MovementHeartbeatController.kt // Press-and-hold 200ms heartbeat & instant STOP
│   └── model
│       ├── ArmAxis.kt               // Base, Shoulder, Wrist specs & angle limits
│       ├── ArmCommand.kt            // Protocol command constants
│       ├── ArmDirection.kt          // Direction mappings
│       ├── ArmSpeed.kt              // SLOW, MEDIUM, FAST presets
│       ├── ArmTelemetry.kt          // Parser for ESP32 telemetry packet
│       └── MagnetState.kt           // OFF, ON, UNKNOWN
├── presentation
│   ├── components
│   │   ├── ConnectionBadge.kt       // Real-time connection pill with pulsing LED
│   │   ├── DirectionButton.kt       // High-tactility press-and-hold gesture handler
│   │   ├── EmergencyStopButton.kt   // Heavy-duty ESTOP button with reset unlock
│   │   ├── LogConsole.kt            // Live rolling packet monitor (TX/RX/SYS/ERR)
│   │   ├── MagnetControl.kt         // Safety interlock & 12V coil control panel
│   │   ├── ServoStatusCard.kt       // Axis angle gauge, position indicator, telemetry badge
│   │   └── SpeedSelector.kt         // Step multiplier selector
│   ├── screens
│   │   ├── ArmControlScreen.kt      // Main dark industrial dashboard
│   │   ├── BleScanDialog.kt         // Device scan & selection dialog
│   │   └── SettingsDialog.kt        // Heartbeat interval slider & protocol inspector
│   ├── theme
│   │   ├── Color.kt                 // Navy, slate, electric blue, warning amber, ESTOP red
│   │   ├── Theme.kt                 // Dark industrial Material 3 theme
│   │   └── Type.kt                  // Clean industrial typography
│   └── viewmodel
│       ├── ArmControlUiState.kt     // Immutable UI state representation
│       └── ArmControlViewModel.kt   // StateFlow-driven business logic
└── MainActivity.kt                  // Android 12+ runtime permissions & lifecycle hooks
```

---

## 5. Building & Running

### Requirements
- **Android Studio Ladybug / Meerkat** or newer
- **JDK 17 or JDK 21**
- **Android SDK Platform 34+** (Min SDK: 26, Target SDK: 36)

### Build Command
Run from terminal in the project root:
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Installing on Physical Android Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 6. Testing & Physical Integration

1. **Flash the ESP32**:
   - Open `firmware/ESP32_ODDBOX_ARM.ino` in Arduino IDE or PlatformIO.
   - Install required libraries: `ESP32 BLE Arduino` and `Adafruit PWM Servo Driver`.
   - Upload sketch to ESP32 DevKit.
2. **Launch Application**:
   - Open **ODDBOX Arm Controller** on your Android smartphone.
   - Grant Bluetooth and Location runtime permissions when prompted.
   - Tap **CONNECT**; select `ODDBOX_ARM` from the scan list.
3. **Verify Movement**:
   - Hold `LEFT` or `RIGHT` on Base Rotation: confirm MG996R moves smoothly.
   - Release button: confirm movement stops instantly and telemetry shows updated angle.
   - Test **EMERGENCY STOP**: verify all motors immediately stop and magnet turns off.

