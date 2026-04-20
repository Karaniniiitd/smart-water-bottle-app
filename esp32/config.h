/*
 * config.h — Smart Hydration Bottle
 * All pin assignments, BLE UUIDs, thresholds, and tuning constants.
 */

#ifndef CONFIG_H
#define CONFIG_H

// ─── Pin Assignments ───────────────────────────────────────────────────────────

// HX711 Load Cell
#define HX711_DOUT_PIN  19
#define HX711_SCK_PIN   18

// I2C bus for MPU6050 (Wire — bus 0)
#define I2C_SDA_PIN     21
#define I2C_SCL_PIN     22

// OLED I2C (Wire1 — bus 1, separate from MPU6050)
#define OLED_SDA_PIN    25
#define OLED_SCL_PIN    26

// ─── OLED Display ──────────────────────────────────────────────────────────────

#define SCREEN_WIDTH    128
#define SCREEN_HEIGHT   64
#define OLED_RESET      -1
#define OLED_ADDR       0x3C

// ─── BLE ───────────────────────────────────────────────────────────────────────

#define BLE_DEVICE_NAME         "SmartBottle"
#define SERVICE_UUID            "0000FFB0-0000-1000-8000-00805F9B34FB"
#define CHARACTERISTIC_UUID     "0000FFB1-0000-1000-8000-00805F9B34FB"

// ─── Sip Detection ────────────────────────────────────────────────────────────

#define SIP_MIN_ML              20      // Minimum weight delta (ml) to count as a sip
#define SIP_WINDOW_MS           6000    // Window after tilt to accept a weight delta as sip
#define WEIGHT_SETTLE_MS        2500    // Wait for scale to settle after motion stops
#define MAX_SIP_ML              500     // Maximum plausible sip — anything larger is noise
#define TILT_SUSTAIN_MS         500     // Tilt must be sustained this long to count as drinking

// ─── IMU ───────────────────────────────────────────────────────────────────────

#define IMU_TILT_THRESHOLD      8000    // Accel deviation to detect tilt (raised to filter table vibrations)
#define IMU_MOTION_THRESHOLD    4000    // Accel deviation to wake the display
#define IMU_CALIBRATION_SAMPLES 2000    // Number of samples for gyro/accel offset calibration
#define IMU_EMA_ALPHA           0.15f   // Exponential moving average smoothing factor for IMU
#define WEIGHT_EMA_ALPHA        0.10f   // EMA smoothing factor for load cell readings

// ─── Display Power / Idle ──────────────────────────────────────────────────────

#define DISPLAY_TIMEOUT_MS      10000   // Turn display off after 10 s of no motion

// ─── Daily Goal ────────────────────────────────────────────────────────────────

#define DAILY_GOAL_ML           2500

// ─── EEPROM Layout ─────────────────────────────────────────────────────────────
//  0 ..  3  → calibration factor (float, 4 bytes)
//  4 ..  7  → tare offset (long, 4 bytes)
//  8 ..  8  → calibrated flag (byte)
// 16 .. 16  → sip cache write index (byte)
// 17 .. 17  → sip cache count    (byte)
// 20 .. 179 → 10 sip records × 16 bytes each:
//               bytes 0-7: timestamp (unsigned long long)
//               bytes 8-11: ml (int)
//               bytes 12-15: reserved

#define EEPROM_SIZE                 512

#define EEPROM_CAL_FACTOR_ADDR      0   // float (4 bytes)
#define EEPROM_TARE_OFFSET_ADDR     4   // long  (4 bytes)
#define EEPROM_CALIBRATED_FLAG_ADDR 8   // byte
#define EEPROM_SIP_INDEX_ADDR       16  // byte  — next write position
#define EEPROM_SIP_COUNT_ADDR       17  // byte  — total stored (max 10)
#define EEPROM_SIP_DATA_START       20  // 10 × 16 bytes = 160 bytes

#define SIP_CACHE_MAX               10
#define SIP_RECORD_SIZE             16  // bytes per record

// ─── Sampling / Loop Timing ───────────────────────────────────────────────────

#define LOADCELL_STABILIZE_MS   2000
#define SERIAL_BAUD             115200

#endif // CONFIG_H
