/*
 * ODDBOX ARM CONTROLLER — ESP32 Firmware Reference
 * 
 * Hardware:
 *  - ESP32 DevKit V1
 *  - PCA9685 16-Channel 12-bit PWM I2C Driver
 *      - Channel 0: Base Servo (MG996R)
 *      - Channel 1: Shoulder Servo (MG996R)
 *      - Channel 2: Wrist Servo (SG90)
 *  - XY-MOS MOSFET Module (Electromagnet Switching)
 *      - Trigger Pin: GPIO 23 (Active HIGH)
 *      - Load: 12V DC Electromagnet
 *  - Power:
 *      - 5V Regulated supply for PCA9685 V+ / Servos
 *      - 12V Regulated supply for Electromagnet
 *      - Common Ground across ESP32, PCA9685, and Power Supplies
 * 
 * BLE Specifications:
 *  - Device Name: ODDBOX_ARM
 *  - Service UUID:        12345678-1234-1234-1234-1234567890ab
 *  - Command Char UUID:   12345678-1234-1234-1234-1234567890ac (Write / Write Without Response)
 *  - Status Char UUID:    12345678-1234-1234-1234-1234567890ad (Notify / Read)
 * 
 * Safety:
 *  - 600ms Command Watchdog: Movement stops automatically if no heartbeat received.
 *  - Mechanical limit clamping on Base (0-180), Shoulder (15-165), Wrist (0-180).
 */

#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID           "12345678-1234-1234-1234-1234567890ab"
#define CHARACTERISTIC_UUID_RX "12345678-1234-1234-1234-1234567890ac" // Command from Android
#define CHARACTERISTIC_UUID_TX "12345678-1234-1234-1234-1234567890ad" // Telemetry to Android

#define PIN_MAGNET_MOSFET 23

// PCA9685 PWM Settings (at 50Hz, standard analog servo pulse is 1ms to 2ms)
#define SERVOMIN  125 // ~0.6ms pulse (~0 deg)
#define SERVOMAX  575 // ~2.4ms pulse (~180 deg)

// PCA9685 Servo Channels
#define CH_BASE     0
#define CH_SHOULDER 1
#define CH_WRIST    2

Adafruit_PWMServoDriver pwm = Adafruit_PWMServoDriver(0x40);

BLEServer* pServer = NULL;
BLECharacteristic* pTxCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// Current Servo Positions (Degrees)
float baseAngle = 90.0f;
float shoulderAngle = 90.0f;
float wristAngle = 90.0f;

// Limits
const float BASE_MIN = 0.0f, BASE_MAX = 180.0f;
const float SHOULDER_MIN = 15.0f, SHOULDER_MAX = 165.0f;
const float WRIST_MIN = 0.0f, WRIST_MAX = 180.0f;

// Active Movement State
enum ActiveMove {
  MOVE_NONE,
  MOVE_BASE_LEFT,
  MOVE_BASE_RIGHT,
  MOVE_SHOULDER_UP,
  MOVE_SHOULDER_DOWN,
  MOVE_WRIST_UP,
  MOVE_WRIST_DOWN
};

volatile ActiveMove currentMovement = MOVE_NONE;
volatile float stepIncrement = 1.0f; // Adjusted by SPEED:1..3
volatile bool magnetActive = false;
volatile bool emergencyStopped = false;

unsigned long lastCommandTime = 0;
const unsigned long COMMAND_TIMEOUT_MS = 600; // Watchdog: halt movement after 600ms
unsigned long lastTelemetryTime = 0;
const unsigned long TELEMETRY_INTERVAL_MS = 100;

// Convert Angle (0-180) to PCA9685 Pulse Width (ticks)
int angleToPulse(float angle, float minAng, float maxAng) {
  float clamped = constrain(angle, minAng, maxAng);
  return map((int)clamped, 0, 180, SERVOMIN, SERVOMAX);
}

void setServoAngle(uint8_t channel, float angle, float minAng, float maxAng) {
  int pulse = angleToPulse(angle, minAng, maxAng);
  pwm.setPWM(channel, 0, pulse);
}

void sendTelemetry() {
  if (!deviceConnected || pTxCharacteristic == NULL) return;
  
  char buffer[64];
  snprintf(buffer, sizeof(buffer), "BASE:%d,SHOULDER:%d,WRIST:%d,MAGNET:%s",
           (int)baseAngle, (int)shoulderAngle, (int)wristAngle,
           magnetActive ? "ON" : "OFF");
  
  pTxCharacteristic->setValue(buffer);
  pTxCharacteristic->notify();
}

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("[BLE] Client Connected");
    }

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      currentMovement = MOVE_NONE;
      magnetActive = false;
      digitalWrite(PIN_MAGNET_MOSFET, LOW);
      Serial.println("[BLE] Client Disconnected. Motors halted, magnet de-energized.");
    }
};

class CommandCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue().c_str();
      rxValue.trim();
      if (rxValue.length() == 0) return;

      Serial.print("[BLE CMD] ");
      Serial.println(rxValue);

      // Handle Emergency Stop
      if (rxValue == "ESTOP") {
        emergencyStopped = true;
        currentMovement = MOVE_NONE;
        magnetActive = false;
        digitalWrite(PIN_MAGNET_MOSFET, LOW);
        sendTelemetry();
        return;
      }

      if (emergencyStopped) {
        if (rxValue == "STOP") {
          // Acknowledge stop
          currentMovement = MOVE_NONE;
        }
        return; // Ignore other motion commands while in ESTOP
      }

      // Movement Heartbeat Commands
      lastCommandTime = millis();

      if (rxValue == "BASE_LEFT") {
        currentMovement = MOVE_BASE_LEFT;
      } else if (rxValue == "BASE_RIGHT") {
        currentMovement = MOVE_BASE_RIGHT;
      } else if (rxValue == "SHOULDER_UP") {
        currentMovement = MOVE_SHOULDER_UP;
      } else if (rxValue == "SHOULDER_DOWN") {
        currentMovement = MOVE_SHOULDER_DOWN;
      } else if (rxValue == "WRIST_UP") {
        currentMovement = MOVE_WRIST_UP;
      } else if (rxValue == "WRIST_DOWN") {
        currentMovement = MOVE_WRIST_DOWN;
      } else if (rxValue == "STOP") {
        currentMovement = MOVE_NONE;
      } else if (rxValue == "MAGNET_ON") {
        magnetActive = true;
        digitalWrite(PIN_MAGNET_MOSFET, HIGH);
        sendTelemetry();
      } else if (rxValue == "MAGNET_OFF") {
        magnetActive = false;
        digitalWrite(PIN_MAGNET_MOSFET, LOW);
        sendTelemetry();
      } else if (rxValue == "SPEED:1") {
        stepIncrement = 0.5f; // Slow precision mode
      } else if (rxValue == "SPEED:2") {
        stepIncrement = 1.2f; // Medium mode
      } else if (rxValue == "SPEED:3") {
        stepIncrement = 2.5f; // Fast mode
      }
    }
};

void setup() {
  Serial.begin(115200);
  Serial.println("\n--- ODDBOX ARM CONTROLLER ESP32 FIRMWARE ---");

  // Electromagnet Pin
  pinMode(PIN_MAGNET_MOSFET, OUTPUT);
  digitalWrite(PIN_MAGNET_MOSFET, LOW);

  // Initialize PCA9685
  Wire.begin();
  pwm.begin();
  pwm.setOscillatorFrequency(27000000);
  pwm.setPWMFreq(50); // 50Hz servo update rate

  // Home Servos to 90 degrees
  setServoAngle(CH_BASE, baseAngle, BASE_MIN, BASE_MAX);
  setServoAngle(CH_SHOULDER, shoulderAngle, SHOULDER_MIN, SHOULDER_MAX);
  setServoAngle(CH_WRIST, wristAngle, WRIST_MIN, WRIST_MAX);
  delay(500);

  // Initialize BLE
  BLEDevice::init("ODDBOX_ARM");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Status (TX / Notify) Characteristic
  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // Command (RX / Write) Characteristic
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                           CHARACTERISTIC_UUID_RX,
                                           BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
                                         );
  pRxCharacteristic->setCallbacks(new CommandCallbacks());

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06); // functions that help with iPhone connections
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Advertising started as 'ODDBOX_ARM'. Waiting for connection...");
}

void loop() {
  unsigned long now = millis();

  // Watchdog check: If no heartbeat received within 600ms, immediately stop movement!
  if (currentMovement != MOVE_NONE && (now - lastCommandTime > COMMAND_TIMEOUT_MS)) {
    Serial.println("[WATCHDOG] Heartbeat timed out (>600ms). Movement stopped.");
    currentMovement = MOVE_NONE;
  }

  // Execute incremental movement on active axis
  if (currentMovement != MOVE_NONE && !emergencyStopped) {
    switch (currentMovement) {
      case MOVE_BASE_LEFT:
        baseAngle = constrain(baseAngle + stepIncrement, BASE_MIN, BASE_MAX);
        setServoAngle(CH_BASE, baseAngle, BASE_MIN, BASE_MAX);
        break;
      case MOVE_BASE_RIGHT:
        baseAngle = constrain(baseAngle - stepIncrement, BASE_MIN, BASE_MAX);
        setServoAngle(CH_BASE, baseAngle, BASE_MIN, BASE_MAX);
        break;
      case MOVE_SHOULDER_UP:
        shoulderAngle = constrain(shoulderAngle + stepIncrement, SHOULDER_MIN, SHOULDER_MAX);
        setServoAngle(CH_SHOULDER, shoulderAngle, SHOULDER_MIN, SHOULDER_MAX);
        break;
      case MOVE_SHOULDER_DOWN:
        shoulderAngle = constrain(shoulderAngle - stepIncrement, SHOULDER_MIN, SHOULDER_MAX);
        setServoAngle(CH_SHOULDER, shoulderAngle, SHOULDER_MIN, SHOULDER_MAX);
        break;
      case MOVE_WRIST_UP:
        wristAngle = constrain(wristAngle + stepIncrement, WRIST_MIN, WRIST_MAX);
        setServoAngle(CH_WRIST, wristAngle, WRIST_MIN, WRIST_MAX);
        break;
      case MOVE_WRIST_DOWN:
        wristAngle = constrain(wristAngle - stepIncrement, WRIST_MIN, WRIST_MAX);
        setServoAngle(CH_WRIST, wristAngle, WRIST_MIN, WRIST_MAX);
        break;
      default:
        break;
    }
  }

  // Transmit periodic telemetry feedback to Android
  if (deviceConnected && (now - lastTelemetryTime >= TELEMETRY_INTERVAL_MS)) {
    lastTelemetryTime = now;
    sendTelemetry();
  }

  // Handle re-advertising after disconnect
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);
    pServer->startAdvertising();
    Serial.println("[BLE] Restarted advertising");
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }

  delay(20); // 50Hz update loop
}

