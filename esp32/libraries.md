# Libraries — Smart Hydration Bottle ESP32 Firmware

Every library listed below was identified from the existing demo/toy code in the `MUC/` folder. **No additional libraries were introduced.**

---

## Libraries Used

| Library           | Header(s)                                            | Demo Source File              | Version / Notes                                       |
|-------------------|------------------------------------------------------|-------------------------------|-------------------------------------------------------|
| **HX711_ADC**     | `<HX711_ADC.h>`                                     | `Calibration_LoadCell.ino`    | HX711_ADC by Olav Kallhovd — Install via Arduino Library Manager. Tested with v1.2.12 |
| **EEPROM**        | `<EEPROM.h>`                                        | `Calibration_LoadCell.ino`    | Built-in with ESP32 Arduino core — no install needed   |
| **MPU6050**       | `<MPU6050.h>`                                       | `IMU.ino`                     | MPU6050 by Electronic Cats (jrowberg I2Cdevlib port). Install via Library Manager. Tested with v0.6.0 |
| **Wire**          | `<Wire.h>`                                          | `Display_sineWave_Demo.ino`, `IMU.ino` | Built-in with ESP32 Arduino core                      |
| **Adafruit SSD1306** | `<Adafruit_SSD1306.h>`                           | `Display_sineWave_Demo.ino`   | Adafruit SSD1306 — Install via Library Manager. Tested with v2.5.7. Also installs Adafruit GFX as a dependency. |
| **BLEDevice**     | `<BLEDevice.h>`, `<BLEUtils.h>`, `<BLEServer.h>`, `<BLE2902.h>` | `BT_example.ino`  | Built-in with ESP32 Arduino core (ESP32 BLE Arduino) — no install needed |

---

## Board / Core

| Component              | Value                                    |
|------------------------|------------------------------------------|
| Board                  | ESP32 Dev Module                         |
| Arduino Core           | esp32 by Espressif Systems (v2.x or v3.x) |
| Install via            | Arduino Boards Manager → search "esp32"  |

---

## Install Checklist (Arduino IDE)

1. **Board:** Tools → Board → Boards Manager → search `esp32` → install
2. **HX711_ADC:** Tools → Manage Libraries → search `HX711_ADC` → install
3. **MPU6050:** Tools → Manage Libraries → search `MPU6050` by Electronic Cats → install
4. **Adafruit SSD1306:** Tools → Manage Libraries → search `Adafruit SSD1306` → install (will auto-install Adafruit GFX)
5. **Wire, EEPROM, BLE:** Already included with the ESP32 core — no manual install needed
