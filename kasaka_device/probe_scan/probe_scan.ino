/*
 * SakaSense probe scanner - finds the RS485 soil probe's speed, address and registers.
 * Same wiring as kasaka_device.ino (RO->GPIO16, DI->GPIO17, DE+RE->GPIO4).
 * Upload, open Serial Monitor at 115200 baud, and copy the output.
 */

#define RS485_RX     16
#define RS485_TX     17
#define RS485_DE_RE  4

HardwareSerial probe(2);

uint16_t modbusCrc(const uint8_t *buf, int len) {
  uint16_t crc = 0xFFFF;
  for (int i = 0; i < len; i++) {
    crc ^= buf[i];
    for (int b = 0; b < 8; b++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
  }
  return crc;
}

// Sends one "read holding registers" request and prints whatever comes back. Returns bytes received.
int query(uint8_t addr, uint16_t reg, uint16_t count) {
  uint8_t req[8] = {addr, 0x03, (uint8_t)(reg >> 8), (uint8_t)reg, (uint8_t)(count >> 8), (uint8_t)count};
  uint16_t crc = modbusCrc(req, 6);
  req[6] = crc & 0xFF;
  req[7] = crc >> 8;

  while (probe.available()) probe.read();
  digitalWrite(RS485_DE_RE, HIGH);
  probe.write(req, 8);
  probe.flush();
  digitalWrite(RS485_DE_RE, LOW);

  uint8_t res[64];
  int got = 0;
  unsigned long start = millis();
  while (millis() - start < 600 && got < (int)sizeof(res)) {
    if (probe.available()) { res[got++] = probe.read(); start = millis() - 500; }  // stop 100 ms after the last byte
  }
  if (got == 0) return 0;

  Serial.printf("  addr %d, reg 0x%04X x%d -> %d bytes:", addr, reg, count, got);
  for (int i = 0; i < got; i++) Serial.printf(" %02X", res[i]);
  bool valid = got >= 5 && res[1] == 0x03 && modbusCrc(res, got - 2) == (uint16_t)(res[got - 2] | (res[got - 1] << 8));
  Serial.println(valid ? "  (VALID)" : "  (garbled - check A/B, ground, power)");
  if (valid) {
    for (int i = 0; i < res[2] / 2; i++)
      Serial.printf("    reg 0x%04X = %u\n", reg + i, (res[3 + 2 * i] << 8) | res[4 + 2 * i]);
  }
  return got;
}

void setup() {
  Serial.begin(115200);
  pinMode(RS485_DE_RE, OUTPUT);
  digitalWrite(RS485_DE_RE, LOW);
  delay(1500);
  Serial.println("\n=== SakaSense probe scan ===");

  const long bauds[] = {4800, 9600, 2400, 19200};
  const uint8_t addrs[] = {1, 2, 3, 0xFF};
  bool anything = false;

  for (long baud : bauds) {
    probe.begin(baud, SERIAL_8N1, RS485_RX, RS485_TX);
    Serial.printf("\nBaud %ld\n", baud);
    for (uint8_t a : addrs) {
      if (query(a, 0x0000, 7)) anything = true;   // 7-in-1 probes: moisture, temp, EC, pH, N, P, K
      if (query(a, 0x001E, 3)) anything = true;   // NPK-only probes: N, P, K
      if (query(a, 0x0006, 1)) anything = true;   // pH-only probes (pH x100)
    }
    probe.end();
  }

  Serial.println(anything
      ? "\nDone. Copy everything above and send it."
      : "\nNO RESPONSE at all. Check: probe power voltage, shared GND, DE+RE on GPIO4, RO->16, DI->17.");
}

void loop() {}
