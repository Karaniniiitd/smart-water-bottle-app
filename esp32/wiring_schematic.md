# Wiring Schematic — Smart Hydration Bottle

All connections use an **ESP32 Dev Board** (30-pin layout).
The OLED and MPU6050 use **separate I2C buses** (Wire and Wire1) on different GPIO pins to avoid conflicts.

---

## ESP32 ↔ HX711 (Load Cell Amplifier)

| ESP32 Pin | HX711 Pin | Notes                      |
|-----------|-----------|----------------------------|
| GPIO 19   | DOUT      | Data out                   |
| GPIO 18   | SCK       | Serial clock               |
| 3.3V      | VCC       | Power supply (3.3V OK)     |
| GND       | GND       | Common ground              |

> **Load Cell → HX711:** Connect the 4 wires from the load cell (E+, E−, A+, A−) to the HX711 board's corresponding terminals. Follow the colour code on your specific load cell datasheet.

---

## ESP32 ↔ MPU6050 (IMU) — I2C Bus 0 (Wire)

| ESP32 Pin | MPU6050 Pin | Notes                     |
|-----------|-------------|---------------------------|
| GPIO 21   | SDA         | I2C data (Wire, bus 0)    |
| GPIO 22   | SCL         | I2C clock (Wire, bus 0)   |
| 3.3V      | VCC         | Power supply              |
| GND       | GND         | Common ground             |
| —         | AD0         | Leave floating or tie LOW (address 0x68) |
| —         | INT         | Not used                  |

---

## ESP32 ↔ SSD1306 OLED (128×64, I2C) — I2C Bus 1 (Wire1)

| ESP32 Pin | OLED Pin | Notes                        |
|-----------|----------|------------------------------|
| GPIO 25   | SDA      | I2C data (Wire1, bus 1)      |
| GPIO 26   | SCL      | I2C clock (Wire1, bus 1)     |
| 3.3V      | VCC      | Power supply                 |
| GND       | GND      | Common ground                |

> **I2C Address:** `0x3C` (most common for 128×64 OLEDs). If yours uses `0x3D`, change `OLED_ADDR` in `config.h`.
> **Important:** The OLED is on a **separate I2C bus** from the MPU6050. Do NOT connect OLED SDA/SCL to GPIO 21/22.

---

## Power Rails

| Source           | Destination         | Voltage | Notes                              |
|------------------|---------------------|---------|------------------------------------|
| LiPo Battery     | TP4056 B+/B−        | 3.7V    | Battery input                      |
| TP4056 OUT+/OUT− | ESP32 VIN / GND     | 3.7–4.2V| Powers ESP32 via on-board regulator|
| ESP32 3.3V       | HX711 VCC           | 3.3V    | Load cell amplifier power          |
| ESP32 3.3V       | MPU6050 VCC         | 3.3V    | IMU power                          |
| ESP32 3.3V       | SSD1306 VCC         | 3.3V    | OLED power                         |
| ESP32 GND        | All GND rails       | —       | Common ground everywhere           |

> **TP4056 Charging:** Connect a micro-USB cable to the TP4056 module to charge the LiPo. The TP4056 handles charging safely.

---

## ASCII Block Diagram

```
                        ┌──────────────┐
    USB/LiPo ──────────►│   TP4056     │
                        │  Charger     │
                        └──┬───────┬───┘
                           │OUT+   │OUT−
                           ▼       ▼
                     ┌──────────────────────────┐
                     │      ESP32 Dev Board      │
                     │                           │
         GPIO 19 ───►│◄── HX711 DOUT            │
         GPIO 18 ───►│──► HX711 SCK             │
                     │                           │
         GPIO 21 ───►│◄─► SDA  (Wire  → MPU)    │
         GPIO 22 ───►│──► SCL  (Wire  → MPU)    │
                     │                           │
         GPIO 25 ───►│◄─► SDA  (Wire1 → OLED)   │
         GPIO 26 ───►│──► SCL  (Wire1 → OLED)   │
                     │                           │
              3.3V ──┤──► VCC to all modules     │
              GND  ──┤──► GND to all modules     │
                     └──────────────────────────┘

               ┌──────────┐  ┌──────────┐  ┌──────────┐
               │  HX711   │  │ MPU6050  │  │ SSD1306  │
               │ +LoadCell│  │  (IMU)   │  │  (OLED)  │
               │ GPIO18/19│  │ GPIO21/22│  │ GPIO25/26│
               └──────────┘  └──────────┘  └──────────┘
```

---

## Pin Summary Table

| GPIO | Function         | I2C Bus | Peripheral |
|------|-----------------|---------|------------|
| 18   | HX711 SCK       | —       | Load Cell  |
| 19   | HX711 DOUT      | —       | Load Cell  |
| 21   | I2C SDA (Wire)  | Bus 0   | MPU6050    |
| 22   | I2C SCL (Wire)  | Bus 0   | MPU6050    |
| 25   | I2C SDA (Wire1) | Bus 1   | OLED       |
| 26   | I2C SCL (Wire1) | Bus 1   | OLED       |

> **Why separate I2C buses?** The MPU6050 and SSD1306 can conflict when sharing a bus on the ESP32. Using two hardware I2C peripherals (Wire + Wire1) on separate GPIO pairs avoids this entirely. The ESP32 supports two I2C buses natively.
