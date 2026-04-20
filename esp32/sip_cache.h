/*
 * sip_cache.h — Circular buffer sip event cache backed by EEPROM
 */

#ifndef SIP_CACHE_H
#define SIP_CACHE_H

#include <EEPROM.h>
#include "config.h"

// ─── Sip Record ────────────────────────────────────────────────────────────────

struct SipRecord {
  unsigned long timestamp;   // millis() when the sip was logged
  int           ml;          // millilitres
  byte          _pad[8];     // reserved, keeps record at 16 bytes
};

inline void sipCacheReadMeta(byte &idx, byte &count) {
  idx = EEPROM.read(EEPROM_SIP_INDEX_ADDR);
  count = EEPROM.read(EEPROM_SIP_COUNT_ADDR);

  // Fresh/erased EEPROM typically reads 0xFF. Clamp to valid range.
  bool repaired = false;
  if (idx == 0xFF || idx >= SIP_CACHE_MAX) {
    idx = 0;
    repaired = true;
  }
  if (count == 0xFF || count > SIP_CACHE_MAX) {
    count = 0;
    repaired = true;
  }

  if (repaired) {
    EEPROM.write(EEPROM_SIP_INDEX_ADDR, idx);
    EEPROM.write(EEPROM_SIP_COUNT_ADDR, count);
    EEPROM.commit();
  }
}

// ─── Cache Functions ───────────────────────────────────────────────────────────

void sipCacheInit() {
  byte idx = 0, count = 0;
  sipCacheReadMeta(idx, count);
}

void sipCachePush(unsigned long ts, int ml) {
  byte idx = 0, count = 0;
  sipCacheReadMeta(idx, count);

  SipRecord rec;
  rec.timestamp = ts;
  rec.ml        = ml;
  memset(rec._pad, 0, sizeof(rec._pad));

  int addr = EEPROM_SIP_DATA_START + idx * SIP_RECORD_SIZE;
  EEPROM.put(addr, rec);

  idx = (idx + 1) % SIP_CACHE_MAX;
  if (count < SIP_CACHE_MAX) count++;

  EEPROM.write(EEPROM_SIP_INDEX_ADDR, idx);
  EEPROM.write(EEPROM_SIP_COUNT_ADDR, count);
  EEPROM.commit();
}

void sipCachePrint() {
  byte idx = 0, count = 0;
  sipCacheReadMeta(idx, count);

  if (count == 0) {
    Serial.println(F("[Cache] No sip events stored."));
    return;
  }

  Serial.println(F("╔══════════════════════════════════════╗"));
  Serial.println(F("║       SIP EVENT CACHE (EEPROM)       ║"));
  Serial.println(F("╠════════╦══════════════╦══════════════╣"));
  Serial.println(F("║  Slot  ║  Timestamp   ║     mL       ║"));
  Serial.println(F("╠════════╬══════════════╬══════════════╣"));

  // Walk backwards from newest
  for (int i = 0; i < count; i++) {
    int slot = ((int)idx - 1 - i + SIP_CACHE_MAX) % SIP_CACHE_MAX;
    int addr = EEPROM_SIP_DATA_START + slot * SIP_RECORD_SIZE;
    SipRecord rec;
    EEPROM.get(addr, rec);

    char line[50];
    snprintf(line, sizeof(line), "║  %3d   ║  %10lu  ║  %6d      ║", i + 1, rec.timestamp, rec.ml);
    Serial.println(line);
  }

  Serial.println(F("╚════════╩══════════════╩══════════════╝"));
}

void sipCacheClear() {
  EEPROM.write(EEPROM_SIP_INDEX_ADDR, 0);
  EEPROM.write(EEPROM_SIP_COUNT_ADDR, 0);
  EEPROM.commit();
  Serial.println(F("[Cache] Cleared."));
}

void sipCacheFactoryReset() {
  // Wipe all cache record bytes to known zero state.
  for (int i = 0; i < SIP_CACHE_MAX * SIP_RECORD_SIZE; i++) {
    EEPROM.write(EEPROM_SIP_DATA_START + i, 0);
  }

  // Reset metadata.
  EEPROM.write(EEPROM_SIP_INDEX_ADDR, 0);
  EEPROM.write(EEPROM_SIP_COUNT_ADDR, 0);
  EEPROM.commit();

  Serial.println(F("[Cache] Factory reset complete (metadata + records wiped)."));
}

#endif // SIP_CACHE_H
