/*
 * display_ui.h — OLED UI for the Smart Hydration Bottle
 *
 * Uses Adafruit_SSD1306 with the exact same API pattern as the demo code.
 * Provides: progress arc, total ml, BLE status icon, and animated transitions.
 */

#ifndef DISPLAY_UI_H
#define DISPLAY_UI_H

#include <Wire.h>
#include <Adafruit_SSD1306.h>
#include <math.h>
#include "config.h"

// ─── Global display instance (created by caller) ──────────────────────────────

extern Adafruit_SSD1306 display;

// ─── State ─────────────────────────────────────────────────────────────────────

static float  _displayedMl   = 0;     // smoothly animated current value
static float  _displayedPct  = 0;     // animated progress percentage
static bool   _displayOn     = true;

// ─── Bitmap icons (8×8) ────────────────────────────────────────────────────────

// BLE connected icon — simple Bluetooth "B" shape
static const unsigned char PROGMEM icon_ble_on[] = {
  0b00011000,
  0b00010100,
  0b01010010,
  0b00110100,
  0b00110100,
  0b01010010,
  0b00010100,
  0b00011000
};

// BLE disconnected icon — X shape
static const unsigned char PROGMEM icon_ble_off[] = {
  0b10000001,
  0b01000010,
  0b00100100,
  0b00011000,
  0b00011000,
  0b00100100,
  0b01000010,
  0b10000001
};

// Water drop icon 8×8
static const unsigned char PROGMEM icon_drop[] = {
  0b00010000,
  0b00010000,
  0b00111000,
  0b01111100,
  0b11111110,
  0b11111110,
  0b01111100,
  0b00111000
};

// ─── Helpers ───────────────────────────────────────────────────────────────────

void displayInit() {
  // I2C bus recovery: toggle SCL to unstick any hung slave device.
  // On battery cold-start, the I2C bus can be in an undefined state
  // because there's no USB providing clean pull-ups during power-on.
  extern TwoWire Wire1_OLED;
  pinMode(OLED_SCL_PIN, OUTPUT);
  for (int i = 0; i < 16; i++) {
    digitalWrite(OLED_SCL_PIN, LOW);
    delayMicroseconds(5);
    digitalWrite(OLED_SCL_PIN, HIGH);
    delayMicroseconds(5);
  }
  // Re-init Wire1 after manual toggle
  Wire1_OLED.begin(OLED_SDA_PIN, OLED_SCL_PIN);
  delay(50);

  // Try to init display up to 3 times (battery power-on can be flaky)
  bool ok = false;
  for (int attempt = 0; attempt < 3; attempt++) {
    if (display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR)) {
      ok = true;
      break;
    }
    Serial.print(F("[Display] Init attempt "));
    Serial.print(attempt + 1);
    Serial.println(F(" failed, retrying..."));
    delay(200);
  }

  if (!ok) {
    Serial.println(F("[Display] SSD1306 not found on I2C!"));
    Serial.println(F("          Check wiring: SDA->GPIO 25, SCL->GPIO 26"));
    Serial.println(F("          Send 'd' to re-check display."));
    // Don't halt — let firmware run without display
    _displayOn = false;
    return;
  }
  display.clearDisplay();
  display.setTextColor(WHITE);
  display.display();
  _displayOn = true;
  Serial.println(F("[Display] SSD1306 initialised OK."));
}

void displayOff() {
  if (_displayOn) {
    display.clearDisplay();
    display.display();
    display.ssd1306_command(SSD1306_DISPLAYOFF);
    _displayOn = false;
  }
}

void displayOn() {
  if (!_displayOn) {
    // Just send the display-on command — don't re-call begin() which
    // can hang if I2C is bus-locked (common on battery power-up).
    display.ssd1306_command(SSD1306_DISPLAYON);
    _displayOn = true;
  }
}

bool isDisplayOn() {
  return _displayOn;
}

// ─── Draw a progress arc (quarter-circle style) ───────────────────────────────
//  Draws a filled arc from 6-o-clock CCW proportional to `pct` (0.0–1.0)
//  centred at (cx, cy) with given radius and thickness.

static void _drawArc(int cx, int cy, int radius, int thickness, float pct) {
  if (pct <= 0.0f) return;
  if (pct > 1.0f) pct = 1.0f;

  float startAngle = M_PI / 2;                       // 6 o-clock
  float sweep      = pct * 2.0f * M_PI;              // full circle = 100 %

  for (int t = 0; t < thickness; t++) {
    int r = radius - t;
    int steps = (int)(sweep / 0.04f);                 // resolution
    if (steps < 1) steps = 1;
    for (int i = 0; i <= steps; i++) {
      float angle = startAngle + (sweep * i / steps);
      int px = cx + (int)(r * cos(angle));
      int py = cy - (int)(r * sin(angle));            // Y inverted on OLED
      if (px >= 0 && px < SCREEN_WIDTH && py >= 0 && py < SCREEN_HEIGHT) {
        display.drawPixel(px, py, WHITE);
      }
    }
  }
}

// ─── Sip splash animation (brief burst on sip) ────────────────────────────────

static unsigned long _splashUntil = 0;
static int           _splashMl    = 0;

void displayTriggerSplash(int sipMl) {
  _splashMl    = sipMl;
  _splashUntil = millis() + 1200;   // show for 1.2 s
}

// ─── Main render function — call every loop iteration ─────────────────────────

void displayRender(int totalMl, int goalMl, bool bleConnected) {
  if (!_displayOn) return;

  // Smooth animation: lerp towards target values
  float targetMl  = (float)totalMl;
  float targetPct = (goalMl > 0) ? ((float)totalMl / (float)goalMl) : 0;
  if (targetPct > 1.0f) targetPct = 1.0f;

  _displayedMl  += (targetMl  - _displayedMl)  * 0.15f;
  _displayedPct += (targetPct - _displayedPct) * 0.12f;

  display.clearDisplay();

  // ── Progress arc (left side) ──

  int arcCx = 30;
  int arcCy = 34;
  int arcR  = 24;
  _drawArc(arcCx, arcCy, arcR, 3, _displayedPct);

  // Percentage inside the arc
  int pctVal = (int)(_displayedPct * 100);
  display.setTextSize(1);
  if (pctVal < 10) {
    display.setCursor(arcCx - 3, arcCy - 4);
  } else if (pctVal < 100) {
    display.setCursor(arcCx - 6, arcCy - 4);
  } else {
    display.setCursor(arcCx - 9, arcCy - 4);
  }
  display.print(pctVal);
  display.print("%");

  // ── Total ml (right side, large) ──

  display.setTextSize(2);
  int dispMl = (int)(_displayedMl + 0.5f);

  // Right-align at x=120
  int digits = 1;
  if (dispMl >= 10)   digits = 2;
  if (dispMl >= 100)  digits = 3;
  if (dispMl >= 1000) digits = 4;
  if (dispMl >= 10000) digits = 5;
  int textX = 118 - (digits * 12);                   // 12 px per char at size 2
  display.setCursor(textX, 16);
  display.print(dispMl);

  // "ml" label
  display.setTextSize(1);
  display.setCursor(104, 34);
  display.print("ml");

  // ── Water drop icon ──
  display.drawBitmap(66, 18, icon_drop, 8, 8, WHITE);

  // ── Top bar ──
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.print("Hydra");

  // BLE icon (top right)
  if (bleConnected) {
    display.drawBitmap(SCREEN_WIDTH - 10, 0, icon_ble_on, 8, 8, WHITE);
  } else {
    display.drawBitmap(SCREEN_WIDTH - 10, 0, icon_ble_off, 8, 8, WHITE);
  }

  // ── Goal bar (bottom) ──
  display.drawRect(0, 58, SCREEN_WIDTH, 6, WHITE);
  int fillW = (int)(_displayedPct * (SCREEN_WIDTH - 2));
  if (fillW > 0) {
    display.fillRect(1, 59, fillW, 4, WHITE);
  }

  // ── Sip splash overlay ──
  if (millis() < _splashUntil) {
    display.setTextSize(1);
    display.setCursor(68, 46);
    display.print("+");
    display.print(_splashMl);
    display.print("ml");
  }

  display.display();
}

// ─── Boot splash ──────────────────────────────────────────────────────────────

void displayBootSplash() {
  if (!_displayOn) return;
  display.clearDisplay();
  display.setTextSize(2);
  display.setCursor(8, 8);
  display.print("Smart");
  display.setCursor(8, 28);
  display.print("Bottle");
  display.setTextSize(1);
  display.setCursor(8, 52);
  display.print("Initialising...");
  display.display();
}

// ─── Calibration screen ───────────────────────────────────────────────────────

void displayCalibration(const char* line1, const char* line2) {
  if (!_displayOn) return;
  displayOn();
  display.clearDisplay();
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.print("== CALIBRATION ==");
  display.setCursor(0, 20);
  display.print(line1);
  display.setCursor(0, 36);
  display.print(line2);
  display.display();
}

// ─── Display diagnostic check (Serial command 'd') ────────────────────────────

void displayCheck() {
  Serial.println(F(""));
  Serial.println(F("╔══════════════════════════════════════════╗"));
  Serial.println(F("║        DISPLAY DIAGNOSTICS               ║"));
  Serial.println(F("╚══════════════════════════════════════════╝"));
  Serial.print(F("  OLED I2C bus  : Wire1 (SDA=GPIO "));
  Serial.print(OLED_SDA_PIN);
  Serial.print(F(", SCL=GPIO "));
  Serial.print(OLED_SCL_PIN);
  Serial.println(F(")"));
  Serial.print(F("  I2C address   : 0x"));
  Serial.println(OLED_ADDR, HEX);
  Serial.print(F("  Display state : "));
  Serial.println(_displayOn ? "ON" : "OFF (not detected)");

  Serial.println(F(""));
  Serial.println(F("  Attempting re-init..."));

  if (display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR)) {
    _displayOn = true;
    display.clearDisplay();
    display.setTextColor(WHITE);
    display.setTextSize(2);
    display.setCursor(10, 10);
    display.print("DISPLAY");
    display.setCursor(10, 32);
    display.print("  OK!");
    display.setTextSize(1);
    display.setCursor(10, 54);
    display.print("SDA=25 SCL=26");
    display.display();
    Serial.println(F("  ✓ Display found and working!"));
    Serial.println(F("  You should see 'DISPLAY OK!' on screen."));
  } else {
    Serial.println(F("  ✗ Display NOT found!"));
    Serial.println(F(""));
    Serial.println(F("  Troubleshooting:"));
    Serial.println(F("  1. Check wiring: SDA→GPIO 25, SCL→GPIO 26"));
    Serial.println(F("  2. Check VCC→3.3V and GND→GND"));
    Serial.println(F("  3. Try address 0x3D: change OLED_ADDR in config.h"));
    Serial.println(F("  4. Ensure OLED is a 128x64 SSD1306 module"));
  }
  Serial.println(F(""));
}

#endif // DISPLAY_UI_H

