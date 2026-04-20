# Walkthrough — Smart Hydration Bottle ESP32 Firmware

Step-by-step guide from flashing to daily use.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Flashing the Firmware](#2-flashing-the-firmware)
3. [First Boot — Load Cell Calibration](#3-first-boot--load-cell-calibration)
4. [IMU Calibration](#4-imu-calibration)
5. [Daily Use](#5-daily-use)
6. [Serial Command Reference](#6-serial-command-reference)
7. [Connecting the Android App](#7-connecting-the-android-app)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Prerequisites

| Item                  | Details                                                     |
|-----------------------|-------------------------------------------------------------|
| Arduino IDE           | v2.x or later (or PlatformIO)                               |
| ESP32 Core            | Espressif ESP32 board support (Boards Manager)               |
| Libraries             | See `libraries.md` for the full install checklist            |
| Hardware wired        | See `wiring_schematic.md` for all pin connections            |
| USB cable             | Micro-USB (or USB-C for newer ESP32 boards)                  |

---

## 2. Flashing the Firmware

1. Open `esp32.ino` in Arduino IDE.
   - The IDE will automatically pick up `config.h`, `sip_cache.h`, and `display_ui.h` (they are in the same folder).
2. Select board: **Tools → Board → ESP32 Dev Module**
3. Select port: **Tools → Port → /dev/cu.usbserial-XXXX** (macOS) or **COMx** (Windows)
4. Board settings:
   - Upload Speed: **921600**
   - Flash Frequency: **80 MHz**
   - Partition Scheme: **Default 4MB with spiffs** (or any default)
5. Click **Upload** (→ button).
6. Open **Serial Monitor** at **115200 baud**.

You should see:

```
╔══════════════════════════════════════════╗
║   Smart Hydration Bottle — ESP32 FW     ║
╚══════════════════════════════════════════╝

[I2C] Wire  → SDA=21 SCL=22 (MPU6050)
[I2C] Wire1 → SDA=25 SCL=26 (OLED)
[Display] SSD1306 initialised OK.
[LoadCell] No calibration found. Send 'c' to calibrate.
[IMU] MPU6050 connected.
[BLE] Advertising as SmartBottle

Ready.  Serial commands:
  c — Calibrate load cell
  i — Calibrate IMU
  r — Reset daily total
  s — Print status
  l — List cached sip events
   x — Factory reset sip cache (wipe EEPROM cache data)
   b — Send BLE test sip (123 ml)
  d — Check / re-init display
```

---

## 3. First Boot — Load Cell Calibration

The load cell **must** be calibrated before sip detection works. On first boot the firmware detects no saved calibration and prompts you.

### Steps:

1. In Serial Monitor, type **`c`** and press Enter.
2. **Step 1 — Tare:** Place the **empty bottle** on the load cell. Type **`t`** and press Enter. The scale zeroes.
3. **Step 2 — Choose method:**
   - Type **`w`** for **water-based calibration** (recommended):
     - Pour exactly **100ml** of water into the bottle using a measuring cup.
     - Type **`g`** when done. The firmware uses 100ml ≈ 100g as the reference.
   - Or type a **weight in grams** (e.g. `200`) if using a calibration weight.
4. **Step 3 — Verification:** The firmware measures the weight and compares it to the expected value. Within 10% = good.
5. The calibration factor is saved to EEPROM and survives reboots.

> **After calibration, the factor survives reboots.** You only need to recalibrate if you change the load cell or mounting.

### Re-calibrating later

Send **`c`** again at any time to redo calibration.

---

## 4. IMU Calibration

The IMU calibration sets gyro and accelerometer offsets so tilt detection is accurate.

### Steps:

1. Place the bottle **flat and perfectly still** on a level surface.
2. In Serial Monitor, type **`i`** and press Enter.
3. The firmware collects 2000 samples (~4 seconds). **Do not move the bottle.**
4. Offsets are printed and applied immediately.

> **Important:** IMU calibration is critical! Without it, the firmware will detect false tilts from table vibrations. Always run `i` after power-on.
> IMU offsets are stored in RAM only and reset on reboot. Run `i` after each power cycle.

---

## 5. Daily Use

1. **Power on** the bottle (ESP32 boots, loads calibration from EEPROM).
2. **Open the Android app** and connect to `SmartBottle` via BLE.
3. **Drink normally.** The firmware automatically:
   - Detects the tilt when you lift the bottle to drink
   - Waits for you to set the bottle down
   - Measures the weight difference
   - If ≥ 20 ml: logs the sip, updates the OLED, and notifies the app
4. **OLED display:**
   - Shows total ml consumed, progress arc, and BLE status
   - Turns on automatically when motion is detected
   - Turns off after 10 seconds of stillness (saves power)
5. **To reset the daily counter:** send `r` via Serial (or restart the device).

---

## 6. Serial Command Reference

| Command | Action                                                                      |
|---------|-----------------------------------------------------------------------------|
| `c`     | Enter **load cell calibration** mode (tare + water/weight calibration)       |
| `i`     | Enter **IMU calibration** mode (collect gyro/accel offsets)                  |
| `r`     | **Reset** today's consumed total to 0 ml                                     |
| `s`     | Print **status**: raw + EMA weight, IMU, BLE, cal factor, sip state, total   |
| `l`     | **List** the last 10 cached sip events (timestamps + ml) from EEPROM         |
| `x`     | **Factory reset cache**: wipes sip cache metadata + EEPROM sip record bytes   |
| `b`     | Send a **BLE test notification** (`123 ml`) to quickly verify phone receive   |
| `d`     | **Display check**: diagnose OLED, attempt re-init, show test pattern         |

All commands are single characters — just type the letter and press Enter in Arduino Serial Monitor at 115200 baud.

---

## 7. Connecting the Android App

The Android app (`smart-water-bottle-app-main`) uses `RealBottleSensorGateway` which:

1. **Scans** for BLE devices advertising service UUID `0000FFB0-...`
2. **Filters** for device names containing "bottle" (our device advertises as `SmartBottle`)
3. **Subscribes** to notify characteristic `0000FFB1-...`
4. **Parses** incoming notifications as plain integer strings (e.g. `"120"` → 120 ml)

### To connect:

1. Ensure the ESP32 is powered on and advertising (Serial shows `[BLE] Advertising as SmartBottle`).
2. Open the Android app.
3. Switch to **Real Bottle** sensor mode (if not already).
4. The app scans and shows `SmartBottle` in the device list.
5. Tap to connect.
6. Sip events will appear in the app as they are detected.

---

## 8. Troubleshooting

### Load cell reads 0 or erratic values
- Check HX711 wiring: DOUT → GPIO 19, SCK → GPIO 18
- Ensure the load cell is wired correctly to the HX711 (E+/E−/A+/A−)
- Recalibrate with `c`

### HX711 timeout on boot
- The Serial Monitor shows `Timeout, check MCU>HX711 wiring`
- Double-check soldering and wire connections
- Ensure HX711 VCC is connected to 3.3V

### MPU6050 not found
- Check I2C wiring: SDA → GPIO 21, SCL → GPIO 22
- Ensure MPU6050 AD0 pin is LOW (address 0x68)
- Run an I2C scanner sketch to verify the address

### OLED not displaying
- **OLED uses Wire1 (GPIO 25/26)** — NOT the same pins as MPU6050 (GPIO 21/22)
- Check wiring: OLED SDA → GPIO 25, OLED SCL → GPIO 26
- Send **`d`** to run display diagnostics and attempt re-init
- Verify OLED address is 0x3C (if 0x3D, change `OLED_ADDR` in `config.h`)
- The display auto-sleeps after 10 s — move the bottle to wake it

### BLE not visible on phone
- Ensure Bluetooth and Location are enabled on the phone
- The ESP32 must be running and Serial should show `Advertising as SmartBottle`
- Try restarting the ESP32
- Check the app is in Real Bottle mode

### Display stuck at "Smart Bottle Initialising..." (Battery power issue)
- If the device works on USB but hangs on battery power, it's usually due to poor battery power quality causing I2C locks or timeouts on boot.
- The firmware has been updated to stagger component boot times and disable the ESP32 brownout detector to get past the high power draw required for BLE initialization.
- If it still happens, make sure your LiPo battery is fully charged (voltage should be >3.7V).
- Double check that the TP4056 output is correctly providing adequate power to the ESP32 `VIN` pin.

### Sips not detected
- Is the load cell calibrated? (Send `s` to check — cal factor should not be "NOT SET")
- Is the weight reading stable? (Send `s` — weight should show a reasonable number in grams)
- Are you tilting the bottle enough? The IMU needs a clear tilt sustained for 500ms
- Run IMU calibration with `i` first — without it, table vibrations can be misread
- Check `SIP_MIN_ML` in `config.h` if threshold is too high

### Sip values are absurdly large (e.g. 422347 ml)
- The load cell calibration factor is wrong — the sensor is returning raw ADC values
- Run calibration with `c`, using the water-based method (`w`) for best results
- After calibration, send `s` and check that weight reads correctly in grams
- Sips above 500ml are automatically rejected (MAX_SIP_ML)

### False tilt detection when bottle is on table
- Run IMU calibration: send `i` with the bottle perfectly still on a flat surface
- The firmware uses EMA smoothing and requires 500ms sustained tilt to confirm
- If still too sensitive, increase `IMU_TILT_THRESHOLD` in `config.h` (default: 8000)

### Sip detected but app doesn't receive it
- Check BLE connection status icon on OLED (Bluetooth "B" vs "X")
- Send `s` to verify `BLE connected: YES`
- The payload is a plain integer string (e.g. `"120"`) matching the app's parser

### Display turns off unexpectedly
- This is normal — the display auto-sleeps after 10 seconds of no motion
- Move/tilt the bottle to wake it
- Increase `DISPLAY_TIMEOUT_MS` in `config.h` if you want a longer timeout

---

## File Structure

```
esp32/
├── esp32.ino              ← Main firmware (setup, loop, sip state machine)
├── config.h               ← All pin assignments, UUIDs, thresholds
├── display_ui.h           ← OLED UI rendering (arc, icons, animations)
├── sip_cache.h            ← EEPROM circular buffer for sip events
├── wiring_schematic.md    ← Pin-by-pin wiring tables
├── libraries.md           ← Library list with versions
└── walkthrough.md         ← This file
```
