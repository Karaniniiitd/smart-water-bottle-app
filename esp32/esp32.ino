/*
 * esp32.ino — Smart Hydration Bottle  •  Main firmware
 *
 * Libraries (all taken from demo code — see libraries.md):
 *   HX711_ADC, MPU6050, Adafruit_SSD1306, Wire, EEPROM,
 *   BLEDevice / BLEServer / BLEUtils / BLE2902
 *
 * Hardware: ESP32 + HX711 load cell + MPU6050 + SSD1306 OLED
 */

// ─── Libraries (same as demo code) ────────────────────────────────────────────

#include "soc/soc.h"              // brownout register access
#include "soc/rtc_cntl_reg.h"     // brownout register address

#include <Wire.h>
#include <EEPROM.h>
#include <HX711_ADC.h>
#include <MPU6050.h>
#include <Adafruit_SSD1306.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <math.h>

// ─── Project headers ──────────────────────────────────────────────────────────

#include "config.h"
#include "sip_cache.h"
#include "display_ui.h"

// ═══════════════════════════════════════════════════════════════════════════════
// GLOBAL OBJECTS
// ═══════════════════════════════════════════════════════════════════════════════

// -- Load cell (HX711_ADC — same constructor as demo) --
HX711_ADC LoadCell(HX711_DOUT_PIN, HX711_SCK_PIN);

// -- IMU (MPU6050 — same object style as demo) --
MPU6050 mpu;

// -- Second I2C bus for OLED (separate from MPU6050) --
TwoWire Wire1_OLED = TwoWire(1);

// -- OLED display (on Wire1 to avoid conflicts with MPU6050 on Wire) --
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire1_OLED, OLED_RESET);

// -- BLE --
BLECharacteristic *pSipCharacteristic = NULL;
BLEServer         *pServer            = NULL;
bool               bleConnected       = false;

// ─── IMU offsets (same pattern as demo) ───────────────────────────────────────

long ax_offset = 0, ay_offset = 0, az_offset = 0;
long gx_offset = 0, gy_offset = 0, gz_offset = 0;
bool imuCalibrated = false;

// ─── EMA-smoothed IMU readings ────────────────────────────────────────────────

float ema_ax = 0, ema_ay = 0, ema_az = 0;
float ema_gx = 0, ema_gy = 0, ema_gz = 0;

// ─── EMA-smoothed weight ─────────────────────────────────────────────────────

float ema_weight       = 0;
bool  ema_weight_primed = false;   // first reading seeds the EMA

// ─── State variables ──────────────────────────────────────────────────────────

int   totalConsumedMl  = 0;             // today's running total
bool  loadCellReady    = false;         // calibration applied
float preWeight        = 0;             // EMA weight before tilt
float currentWeight    = 0;             // latest EMA weight

// Sip-detection state machine
enum SipState { IDLE, TILT_PENDING, TILTED, SETTLING };
SipState sipState = IDLE;

unsigned long tiltStartAt      = 0;     // when raw tilt first appeared
unsigned long tiltConfirmedAt  = 0;     // when sustained tilt was confirmed
unsigned long settleStartAt    = 0;     // when bottle returned upright

// Display idle timer
unsigned long lastMotionAt     = 0;

// Smooth display refresh rate
unsigned long lastDisplayUpdate = 0;
const unsigned long DISPLAY_INTERVAL_MS = 50;   // ~20 fps

// ═══════════════════════════════════════════════════════════════════════════════
// BLE CALLBACKS (same pattern as demo BT_example.ino)
// ═══════════════════════════════════════════════════════════════════════════════

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    bleConnected = true;
    Serial.println(F("[BLE] Device connected"));
  }

  void onDisconnect(BLEServer* pServer) {
    bleConnected = false;
    Serial.println(F("[BLE] Device disconnected — restarting advertising"));
    // Restart advertising so the phone can reconnect
    delay(500);
    pServer->startAdvertising();
  }
};

// ═══════════════════════════════════════════════════════════════════════════════
// BLE SETUP
// ═══════════════════════════════════════════════════════════════════════════════

void setupBLE() {
  BLEDevice::init(BLE_DEVICE_NAME);

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pSipCharacteristic = pService->createCharacteristic(
                          CHARACTERISTIC_UUID,
                          BLECharacteristic::PROPERTY_NOTIFY |
                          BLECharacteristic::PROPERTY_READ
                        );

  // Required for notifications (same as demo)
  pSipCharacteristic->addDescriptor(new BLE2902());

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->start();

  Serial.println(F("[BLE] Advertising as SmartBottle"));
}

// ── Send sip notification (same pattern as demo sendBLE) ──

void sendSipBLE(int ml) {
  if (bleConnected && pSipCharacteristic != NULL) {
    String payload = String(ml);
    pSipCharacteristic->setValue(payload.c_str());
    pSipCharacteristic->notify();
    Serial.print(F("[BLE] Notified sip: "));
    Serial.print(payload);
    Serial.println(F(" ml"));
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LOAD CELL SETUP & CALIBRATION
// ═══════════════════════════════════════════════════════════════════════════════

void setupLoadCell() {
  Serial.println(F("[LoadCell] Starting HX711..."));
  LoadCell.begin();

  // Give the HX711 extra time to power up on battery
  delay(200);

  // ── Start WITHOUT auto-tare (the library's tare() loops forever
  //    if HX711 is unresponsive — hangs on battery power-up) ──
  unsigned long stabilizingtime = LOADCELL_STABILIZE_MS;
  LoadCell.start(stabilizingtime, false);   // false = skip internal tare

  if (LoadCell.getTareTimeoutFlag() || LoadCell.getSignalTimeoutFlag()) {
    Serial.println(F("[LoadCell] Timeout! Check HX711 wiring. Continuing without load cell."));
    loadCellReady = false;
    return;
  }

  // ── Attempt tare with our own timeout (shortened for battery) ──
  Serial.println(F("[LoadCell] Attempting tare..."));
  LoadCell.tareNoDelay();
  unsigned long tareStart = millis();
  while (!LoadCell.getTareStatus()) {
    LoadCell.update();
    if (millis() - tareStart > 3000) {   // 3s timeout (was 5s)
      Serial.println(F("[LoadCell] Tare timed out — continuing without tare."));
      break;
    }
    delay(1);
    yield();   // feed the WDT
  }
  if (LoadCell.getTareStatus()) {
    Serial.println(F("[LoadCell] Tare complete."));
  }

  // ── Apply saved calibration factor (if any) ──
  byte flag = EEPROM.read(EEPROM_CALIBRATED_FLAG_ADDR);
  if (flag == 0xAA) {
    float calFactor;
    EEPROM.get(EEPROM_CAL_FACTOR_ADDR, calFactor);
    LoadCell.setCalFactor(calFactor);
    loadCellReady = true;
    Serial.print(F("[LoadCell] Loaded cal factor from EEPROM: "));
    Serial.println(calFactor);
  } else {
    LoadCell.setCalFactor(1.0);
    loadCellReady = false;
    Serial.println(F("[LoadCell] No calibration found. Send 'c' to calibrate."));
  }

  // ── Prime the weight EMA (with timeout) ──
  unsigned long primeStart = millis();
  while (!LoadCell.update()) {
    if (millis() - primeStart > 2000) {   // 2s timeout (was 3s)
      Serial.println(F("[LoadCell] EMA prime timed out."));
      break;
    }
    yield();   // feed the WDT
  }
  if (LoadCell.update()) {
    float initial = LoadCell.getData();
    ema_weight = initial;
    ema_weight_primed = true;
    currentWeight = initial;
  }
  Serial.println(F("[LoadCell] Setup complete."));
}

// ── Interactive calibration with water-based option ──

void calibrateLoadCell() {
  Serial.println(F(""));
  Serial.println(F("╔══════════════════════════════════════════╗"));
  Serial.println(F("║      LOAD CELL CALIBRATION MODE         ║"));
  Serial.println(F("╚══════════════════════════════════════════╝"));
  Serial.println(F(""));
  Serial.println(F("Step 1: Place the EMPTY bottle on the load cell."));
  Serial.println(F("        Send 't' to tare (zero) the scale."));

  displayCalibration("Step 1: Tare", "Send 't' in Serial");

  boolean _resume = false;
  while (_resume == false) {
    LoadCell.update();
    if (Serial.available() > 0) {
      char inByte = Serial.read();
      if (inByte == 't') LoadCell.tareNoDelay();
    }
    if (LoadCell.getTareStatus() == true) {
      Serial.println(F("[Cal] Tare complete."));
      _resume = true;
    }
  }

  Serial.println(F(""));
  Serial.println(F("Step 2: Choose calibration method:"));
  Serial.println(F("   'w' — Water-based: pour exactly 100ml of water (recommended)"));
  Serial.println(F("   Or type a weight in grams (e.g. '200') for a known mass"));

  displayCalibration("Step 2: Method", "'w'=water or grams");

  float known_mass = 0;
  _resume = false;
  while (_resume == false) {
    LoadCell.update();
    if (Serial.available() > 0) {
      char peekChar = Serial.peek();
      if (peekChar == 'w' || peekChar == 'W') {
        Serial.read();  // consume 'w'
        // ── Water-based calibration ──
        Serial.println(F(""));
        Serial.println(F("[Cal] WATER-BASED CALIBRATION"));
        Serial.println(F("      Pour exactly 100ml of water into the bottle."));
        Serial.println(F("      (Use a measuring cup or a marked bottle)"));
        Serial.println(F("      Then send 'g' when ready."));

        displayCalibration("Pour 100ml water", "Send 'g' when done");

        boolean waterReady = false;
        while (!waterReady) {
          LoadCell.update();
          if (Serial.available() > 0) {
            char c = Serial.read();
            if (c == 'g' || c == 'G') {
              waterReady = true;
            }
          }
        }

        known_mass = 100.0;   // 100ml water ≈ 100g
        Serial.println(F("[Cal] Using 100ml water = 100g as reference."));
        _resume = true;
      } else {
        known_mass = Serial.parseFloat();
        if (known_mass > 0) {
          Serial.print(F("[Cal] Known mass: "));
          Serial.print(known_mass);
          Serial.println(F(" g"));
          _resume = true;
        }
      }
    }
  }

  LoadCell.refreshDataSet();
  float newCalibrationValue = LoadCell.getNewCalibration(known_mass);

  Serial.print(F("[Cal] New calibration factor: "));
  Serial.println(newCalibrationValue);

  // ── Verification step ──
  Serial.println(F(""));
  Serial.println(F("Step 3: Verification"));
  Serial.println(F("        Current reading should be close to the known weight."));

  displayCalibration("Step 3: Verify", "Checking...");

  // Take a few readings to verify
  delay(500);
  float verifySum = 0;
  int verifyCount = 0;
  unsigned long verifyStart = millis();
  while (millis() - verifyStart < 2000) {
    if (LoadCell.update()) {
      verifySum += LoadCell.getData();
      verifyCount++;
    }
    delay(10);
  }

  if (verifyCount > 0) {
    float avgReading = verifySum / verifyCount;
    Serial.print(F("        Measured: "));
    Serial.print(avgReading, 1);
    Serial.print(F(" g  (expected: "));
    Serial.print(known_mass, 1);
    Serial.println(F(" g)"));

    float error = abs(avgReading - known_mass);
    if (error < known_mass * 0.1) {
      Serial.println(F("        ✓ Looks good! Within 10% tolerance."));
    } else {
      Serial.print(F("        ⚠ Off by "));
      Serial.print(error, 1);
      Serial.println(F("g. Consider recalibrating."));
    }
  }

  // Save to EEPROM
  EEPROM.put(EEPROM_CAL_FACTOR_ADDR, newCalibrationValue);
  EEPROM.write(EEPROM_CALIBRATED_FLAG_ADDR, 0xAA);
  EEPROM.commit();

  Serial.println(F("[Cal] Saved to EEPROM."));
  Serial.println(F("[Cal] Calibration complete!"));
  Serial.println(F(""));

  loadCellReady = true;

  // Re-prime EMA
  ema_weight = LoadCell.getData();
  ema_weight_primed = true;

  displayCalibration("Calibration", "DONE!");
  delay(1500);
}

// ═══════════════════════════════════════════════════════════════════════════════
// IMU SETUP & CALIBRATION
// ═══════════════════════════════════════════════════════════════════════════════

void setupIMU() {
  // Give MPU6050 time to power-up on battery
  delay(100);
  mpu.initialize();
  delay(50);

  // Retry connection up to 3 times (I2C can be flaky on battery cold-start)
  bool connected = false;
  for (int attempt = 0; attempt < 3; attempt++) {
    if (mpu.testConnection()) {
      connected = true;
      break;
    }
    Serial.print(F("[IMU] Connection attempt "));
    Serial.print(attempt + 1);
    Serial.println(F(" failed, retrying..."));
    delay(100);
    mpu.initialize();
    delay(50);
  }

  if (!connected) {
    Serial.println(F("[IMU] MPU6050 connection FAILED! Check wiring."));
  } else {
    Serial.println(F("[IMU] MPU6050 connected."));
  }
}

// ── Interactive IMU calibration (follows demo IMU.ino pattern) ──

void calibrateIMU() {
  Serial.println(F(""));
  Serial.println(F("╔══════════════════════════════════════════╗"));
  Serial.println(F("║        IMU CALIBRATION MODE              ║"));
  Serial.println(F("╚══════════════════════════════════════════╝"));
  Serial.println(F(""));
  Serial.println(F("Place the bottle FLAT and STILL on a level surface."));
  Serial.println(F("Collecting 2000 samples... DO NOT MOVE."));

  displayCalibration("IMU Calibration", "Keep still...");

  long ax = 0, ay = 0, az = 0;
  long gx = 0, gy = 0, gz = 0;

  int samples = IMU_CALIBRATION_SAMPLES;

  for (int i = 0; i < samples; i++) {
    int16_t rax, ray, raz, rgx, rgy, rgz;
    mpu.getMotion6(&rax, &ray, &raz, &rgx, &rgy, &rgz);

    ax += rax;
    ay += ray;
    az += (raz - 16384);   // gravity correction (same as demo)

    gx += rgx;
    gy += rgy;
    gz += rgz;

    delay(2);
  }

  ax_offset = ax / samples;
  ay_offset = ay / samples;
  az_offset = az / samples;

  gx_offset = gx / samples;
  gy_offset = gy / samples;
  gz_offset = gz / samples;

  imuCalibrated = true;

  // Seed the EMA with calibrated zeros
  ema_ax = 0; ema_ay = 0; ema_az = 0;
  ema_gx = 0; ema_gy = 0; ema_gz = 0;

  Serial.println(F("[IMU] Calibration done."));
  Serial.print(F("      Accel offsets: "));
  Serial.print(ax_offset); Serial.print(F(", "));
  Serial.print(ay_offset); Serial.print(F(", "));
  Serial.println(az_offset);
  Serial.print(F("      Gyro  offsets: "));
  Serial.print(gx_offset); Serial.print(F(", "));
  Serial.print(gy_offset); Serial.print(F(", "));
  Serial.println(gz_offset);
  Serial.println();

  displayCalibration("IMU Cal", "DONE!");
  delay(1500);
}

// ═══════════════════════════════════════════════════════════════════════════════
// IMU READING WITH EMA SMOOTHING
// ═══════════════════════════════════════════════════════════════════════════════

// Call once per loop iteration to update smoothed IMU values
void updateIMU() {
  int16_t raw_ax, raw_ay, raw_az, raw_gx, raw_gy, raw_gz;
  mpu.getMotion6(&raw_ax, &raw_ay, &raw_az, &raw_gx, &raw_gy, &raw_gz);

  // Apply calibration offsets (same pattern as demo)
  if (imuCalibrated) {
    raw_ax -= ax_offset;
    raw_ay -= ay_offset;
    raw_az -= az_offset;
    raw_gx -= gx_offset;
    raw_gy -= gy_offset;
    raw_gz -= gz_offset;
  }

  // Exponential moving average — smooths out vibrations and noise
  float a = IMU_EMA_ALPHA;
  ema_ax = a * raw_ax + (1.0f - a) * ema_ax;
  ema_ay = a * raw_ay + (1.0f - a) * ema_ay;
  ema_az = a * raw_az + (1.0f - a) * ema_az;
  ema_gx = a * raw_gx + (1.0f - a) * ema_gx;
  ema_gy = a * raw_gy + (1.0f - a) * ema_gy;
  ema_gz = a * raw_gz + (1.0f - a) * ema_gz;
}

// ─── Detect tilt using EMA-smoothed values ────────────────────────────────────

bool detectTiltRaw() {
  // Deviation from upright — when standing, gravity is on Z, X/Y ≈ 0
  float deviation = fabs(ema_ax) + fabs(ema_ay);
  return deviation > (float)IMU_TILT_THRESHOLD;
}

bool detectMotion() {
  float gyroMag  = fabs(ema_gx) + fabs(ema_gy) + fabs(ema_gz);
  float accelDev = fabs(ema_ax) + fabs(ema_ay);
  return (gyroMag > (float)IMU_MOTION_THRESHOLD) ||
         (accelDev > (float)IMU_MOTION_THRESHOLD);
}

// ═══════════════════════════════════════════════════════════════════════════════
// WEIGHT READING WITH EMA SMOOTHING
// ═══════════════════════════════════════════════════════════════════════════════

void updateWeight() {
  if (LoadCell.update()) {
    float raw = LoadCell.getData();
    if (!ema_weight_primed) {
      ema_weight = raw;
      ema_weight_primed = true;
    } else {
      float a = WEIGHT_EMA_ALPHA;
      ema_weight = a * raw + (1.0f - a) * ema_weight;
    }
    currentWeight = ema_weight;
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SIP DETECTION STATE MACHINE (with sustained tilt & smoothing)
// ═══════════════════════════════════════════════════════════════════════════════

void processSipDetection() {
  if (!loadCellReady) return;

  bool tiltedNow = detectTiltRaw();
  unsigned long now = millis();

  switch (sipState) {

    case IDLE:
      if (tiltedNow) {
        // Start watching — but don't commit yet (could be vibration)
        tiltStartAt = now;
        sipState = TILT_PENDING;
      }
      break;

    case TILT_PENDING:
      // Require SUSTAINED tilt for TILT_SUSTAIN_MS to filter table bumps
      if (!tiltedNow) {
        // Tilt stopped before sustained — it was just noise
        sipState = IDLE;
      } else if (now - tiltStartAt >= TILT_SUSTAIN_MS) {
        // Sustained tilt confirmed — this is a real drinking gesture
        preWeight = currentWeight;     // snapshot EMA-smoothed weight
        tiltConfirmedAt = now;
        sipState = TILTED;
        Serial.print(F("[Sip] Tilt confirmed — pre-weight: "));
        Serial.print(preWeight, 1);
        Serial.println(F(" g"));
      }
      break;

    case TILTED:
      if (!tiltedNow) {
        // Bottle returned upright — start settling
        settleStartAt = now;
        sipState = SETTLING;
        Serial.println(F("[Sip] Upright — settling..."));
      } else if (now - tiltConfirmedAt > SIP_WINDOW_MS) {
        // Tilted too long — just carrying/moving, not drinking
        sipState = IDLE;
        Serial.println(F("[Sip] Tilt window expired — reset."));
      }
      break;

    case SETTLING:
      if (tiltedNow) {
        // Tilted again while settling — go back to tilted
        tiltConfirmedAt = now;
        sipState = TILTED;
      } else if (now - settleStartAt > WEIGHT_SETTLE_MS) {
        // Scale has stabilised — compute delta from smoothed weights
        float postWeight = currentWeight;
        float delta = preWeight - postWeight;   // positive = water removed
        int sipMl = (int)(delta + 0.5f);         // 1g ≈ 1ml

        Serial.print(F("[Sip] Delta: "));
        Serial.print(preWeight, 1);
        Serial.print(F(" → "));
        Serial.print(postWeight, 1);
        Serial.print(F(" = "));
        Serial.print(delta, 1);
        Serial.println(F(" g"));

        // ── Validate sip ──
        if (sipMl >= SIP_MIN_ML && sipMl <= MAX_SIP_ML) {
          Serial.print(F("[Sip] ✓ Valid sip: "));
          Serial.print(sipMl);
          Serial.println(F(" ml"));

          totalConsumedMl += sipMl;
          sendSipBLE(sipMl);
          sipCachePush(now, sipMl);
          displayTriggerSplash(sipMl);

        } else if (sipMl > MAX_SIP_ML) {
          Serial.print(F("[Sip] ✗ Rejected — "));
          Serial.print(sipMl);
          Serial.println(F(" ml exceeds MAX_SIP_ML. Likely sensor noise."));
          Serial.println(F("      Have you run calibration? Send 'c' to calibrate."));

        } else if (sipMl > 0 && sipMl < SIP_MIN_ML) {
          Serial.print(F("[Sip] — Delta too small ("));
          Serial.print(sipMl);
          Serial.println(F(" ml) — ignored."));
        }
        // Negative delta = water added, which we silently ignore

        sipState = IDLE;
      }
      break;
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SERIAL COMMAND HANDLER
// ═══════════════════════════════════════════════════════════════════════════════

void handleSerial() {
  if (Serial.available() > 0) {
    char cmd = Serial.read();

    switch (cmd) {

      case 'c':
        calibrateLoadCell();
        break;

      case 'i':
        calibrateIMU();
        break;

      case 'r':
        totalConsumedMl = 0;
        Serial.println(F("[Reset] Daily total reset to 0 ml."));
        break;

      case 'd':
        displayCheck();
        break;

      case 's': {
        Serial.println(F(""));
        Serial.println(F("╔══════════════════════════════════════════╗"));
        Serial.println(F("║          CURRENT STATUS                  ║"));
        Serial.println(F("╚══════════════════════════════════════════╝"));

        Serial.print(F("  Weight (raw)   : "));
        if (LoadCell.update()) Serial.print(LoadCell.getData(), 1);
        else Serial.print(currentWeight, 1);
        Serial.println(F(" g"));

        Serial.print(F("  Weight (EMA)   : "));
        Serial.print(ema_weight, 1);
        Serial.println(F(" g"));

        Serial.print(F("  IMU Accel(EMA) : "));
        Serial.print(ema_ax, 0); Serial.print(F(", "));
        Serial.print(ema_ay, 0); Serial.print(F(", "));
        Serial.println(ema_az, 0);
        Serial.print(F("  IMU Gyro (EMA) : "));
        Serial.print(ema_gx, 0); Serial.print(F(", "));
        Serial.print(ema_gy, 0); Serial.print(F(", "));
        Serial.println(ema_gz, 0);
        Serial.print(F("  Tilt detected  : "));
        Serial.println(detectTiltRaw() ? "YES" : "NO");
        Serial.print(F("  IMU calibrated : "));
        Serial.println(imuCalibrated ? "YES" : "NO");

        Serial.print(F("  BLE connected  : "));
        Serial.println(bleConnected ? "YES" : "NO");

        byte flag = EEPROM.read(EEPROM_CALIBRATED_FLAG_ADDR);
        if (flag == 0xAA) {
          float cf;
          EEPROM.get(EEPROM_CAL_FACTOR_ADDR, cf);
          Serial.print(F("  Cal factor     : "));
          Serial.println(cf);
        } else {
          Serial.println(F("  Cal factor     : NOT SET — send 'c' to calibrate!"));
        }

        Serial.print(F("  Sip state      : "));
        switch (sipState) {
          case IDLE:         Serial.println(F("IDLE")); break;
          case TILT_PENDING: Serial.println(F("TILT_PENDING")); break;
          case TILTED:       Serial.println(F("TILTED")); break;
          case SETTLING:     Serial.println(F("SETTLING")); break;
        }

        Serial.print(F("  Today's total  : "));
        Serial.print(totalConsumedMl);
        Serial.println(F(" ml"));

        Serial.print(F("  Daily goal     : "));
        Serial.print(DAILY_GOAL_ML);
        Serial.println(F(" ml"));

        Serial.print(F("  Display        : "));
        Serial.println(isDisplayOn() ? "ON" : "OFF");
        Serial.println();
        break;
      }

      case 'l':
        sipCachePrint();
        break;

      case 'x':
        sipCacheFactoryReset();
        break;

      case 'b': {
        const int testSipMl = 123;
        Serial.print(F("[BLE] Manual test notify: "));
        Serial.print(testSipMl);
        Serial.println(F(" ml"));
        sendSipBLE(testSipMl);
        break;
      }

      default:
        break;
    }
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SETUP
// ═══════════════════════════════════════════════════════════════════════════════

void setup() {
  // ── Disable brownout detector ──
  // On battery, the ESP32 BOD triggers at ~3.3V during current spikes
  // (BLE + I2C + HX711 init) and resets the chip in a loop.
  // The TP4056 output is 3.7-4.2V which is fine, but transient dips
  // during heavy init can trip the detector.
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(SERIAL_BAUD);

  // Longer startup delay on battery — let voltage rails stabilise
  delay(500);

  Serial.println(F(""));
  Serial.println(F("╔══════════════════════════════════════════╗"));
  Serial.println(F("║   Smart Hydration Bottle — ESP32 FW     ║"));
  Serial.println(F("╚══════════════════════════════════════════╝"));
  Serial.println(F(""));

  // ── EEPROM ──
  EEPROM.begin(EEPROM_SIZE);

  // ── I2C bus 1: OLED (Wire1) — init FIRST so we can show splash ──
  Wire1_OLED.begin(OLED_SDA_PIN, OLED_SCL_PIN);
  delay(50);    // let I2C bus settle
  Serial.print(F("[I2C] Wire1 → SDA="));
  Serial.print(OLED_SDA_PIN);
  Serial.print(F(" SCL="));
  Serial.print(OLED_SCL_PIN);
  Serial.println(F(" (OLED)"));

  // ── OLED — show splash immediately ──
  displayInit();
  displayBootSplash();
  delay(100);   // let display render before pulling more current

  // ── I2C bus 0: MPU6050 (Wire) ──
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  delay(50);    // let I2C bus settle
  Serial.print(F("[I2C] Wire  → SDA="));
  Serial.print(I2C_SDA_PIN);
  Serial.print(F(" SCL="));
  Serial.print(I2C_SCL_PIN);
  Serial.println(F(" (MPU6050)"));

  // ── IMU (low power draw, quick init) ──
  setupIMU();
  delay(100);

  // ── Load Cell (HX711 needs stabilisation time) ──
  setupLoadCell();
  delay(100);

  // ── BLE (biggest current spike — init LAST) ──
  Serial.println(F("[BLE] Starting BLE (may cause brief voltage dip)..."));
  delay(200);   // let everything else settle first
  setupBLE();

  // ── Sip cache ──
  sipCacheInit();

  lastMotionAt = millis();

  Serial.println(F(""));
  Serial.println(F("Ready.  Serial commands:"));
  Serial.println(F("  c — Calibrate load cell"));
  Serial.println(F("  i — Calibrate IMU"));
  Serial.println(F("  r — Reset daily total"));
  Serial.println(F("  s — Print status"));
  Serial.println(F("  l — List cached sip events"));
  Serial.println(F("  x — Factory reset sip cache (wipe EEPROM cache data)"));
  Serial.println(F("  b — Send BLE test sip (123 ml)"));
  Serial.println(F("  d — Check / re-init display"));
  Serial.println(F(""));

  // Update display from splash to normal UI
  displayRender(totalConsumedMl, DAILY_GOAL_ML, bleConnected);

  // Re-enable brownout detector now that init is complete
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 1);

  delay(100);  // brief pause then start main loop
}

// ═══════════════════════════════════════════════════════════════════════════════
// LOOP
// ═══════════════════════════════════════════════════════════════════════════════

void loop() {
  unsigned long now = millis();

  // ── Update sensor EMAs (call every loop) ──
  updateIMU();
  updateWeight();

  // ── Motion detection → display wake/sleep ──
  if (detectMotion()) {
    lastMotionAt = now;
    displayOn();
  } else if (now - lastMotionAt > DISPLAY_TIMEOUT_MS) {
    displayOff();
  }

  // ── Sip detection state machine ──
  processSipDetection();

  // ── Serial commands ──
  handleSerial();

  // ── Display update (throttled) ──
  if (now - lastDisplayUpdate >= DISPLAY_INTERVAL_MS) {
    displayRender(totalConsumedMl, DAILY_GOAL_ML, bleConnected);
    lastDisplayUpdate = now;
  }

  delay(10);   // yield
}
