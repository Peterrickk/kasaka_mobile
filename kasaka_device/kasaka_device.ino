/*
 * SakaSense field device firmware (ESP32) for the Kasaka mobile app
 * -----------------------------------------------------------------
 * Reads soil pH and N, P, K (mg/kg) from an RS485 Modbus soil probe, judges them with the
 * Kasaka Guidebook (docs/guidebook/kasaka-guidebook.html): the four reading boxes, the 1-7 code
 * and the best of the book's crops. Shows the result on a 16x2 I2C LCD and sends it to the
 * Kasaka app over Bluetooth LE.
 *
 * Crop: hold the button for 2 seconds to step through Auto (best crop) and the book's crops; the
 * choice is remembered after power-off. When the app measures, it tells the device which crop the
 * plot has (CROP:<name>) so the device shows the code for that crop.
 *
 * BLE contract: kasakamobile/docs/DEVICE_PROTOCOL.md (v1). If you change a UUID or field
 * here, change DeviceProtocol.kt in the app too.
 *
 * Board:     ESP32 Dev Module (Arduino core for ESP32 2.x or 3.x)
 * Libraries: none to install. BLE and Wire ship with the ESP32 core; the LCD driver is below.
 *
 * Wiring
 *   Soil probe (RS485, 4800 baud, address 1) -> MAX485 module:
 *     MAX485 RO -> GPIO16 (RX2)     MAX485 DI -> GPIO17 (TX2)
 *     MAX485 DE + RE (tied) -> GPIO4
 *     MAX485 A/B -> probe A/B (yellow/blue on most probes), probe power per its label
 *   LCD 16x2 I2C (0x27): SDA -> GPIO21, SCL -> GPIO22, VCC 5 V
 *   Button: GPIO0 (the BOOT button) to GND. Press = measure now, hold 2 s = next crop
 *   Battery (optional): LiPo+ -> 100k -> GPIO35 -> 100k -> GND
 *
 * No probe yet? Set DEMO_MODE to true to send realistic random readings so the app
 * can be tested end to end.
 */

#include <Wire.h>
#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ---------------- Settings ----------------
#define DEMO_MODE      false
#define FW_VERSION     "2.0.0"   // 2.x: Kasaka Guidebook codes 1-7

#define RS485_RX       16
#define RS485_TX       17
#define RS485_DE_RE    4
#define PROBE_BAUD     4800
#define PROBE_ADDR     0x01
// Current probe: "RS485 output 3-pin NPK soil sensor" (N, P, K only; confirmed with probe_scan).
#define REG_NPK        0x001E   // N, P, K in mg/kg at 0x001E, 0x001F, 0x0020

// pH: the current probe has none, so a demo pH around neutral soil (6.3-6.8) is sent.
// When the pH-capable probe arrives: set PH_FROM_PROBE to true, set REG_PH from probe_scan,
// and check PH_SCALE (10 = value is pH x10, 100 = pH x100).
#define PH_FROM_PROBE  false
#define REG_PH         0x0006
#define PH_SCALE       100.0

#define BUTTON_PIN     0
#define BATTERY_PIN    35

// ---------------- BLE (must match DeviceProtocol.kt) ----------------
#define SERVICE_UUID   "7d2a0001-5a4b-4c53-9a1e-5341534b4153"
#define READING_UUID   "7d2a0002-5a4b-4c53-9a1e-5341534b4153"
#define COMMAND_UUID   "7d2a0003-5a4b-4c53-9a1e-5341534b4153"
#define INFO_UUID      "7d2a0004-5a4b-4c53-9a1e-5341534b4153"

// ======================================================================
// Minimal 16x2 LCD driver for the common PCF8574 I2C backpack (no library needed)
// Backpack pins: P0=RS, P1=RW, P2=EN, P3=backlight, P4-P7=D4-D7
// ======================================================================
class I2cLcd : public Print {
 public:
  I2cLcd(uint8_t addr) : addr(addr) {}

  void init() {
    Wire.begin();
    delay(50);
    write4(0x30); delay(5);         // reset into 8-bit mode three times...
    write4(0x30); delay(5);
    write4(0x30); delayMicroseconds(150);
    write4(0x20);                   // ...then switch to 4-bit mode
    command(0x28);                  // 2 lines, 5x8 font
    command(0x0C);                  // display on, cursor off
    clear();
    command(0x06);                  // left to right
  }
  void backlight() { light = 0x08; expander(0); }
  void clear() { command(0x01); delay(2); }
  void setCursor(uint8_t col, uint8_t row) { command(0x80 | (col + (row ? 0x40 : 0))); }
  size_t write(uint8_t c) override { send(c, 0x01); return 1; }
  using Print::write;

 private:
  uint8_t addr, light = 0;
  void command(uint8_t c) { send(c, 0); }
  void send(uint8_t value, uint8_t rs) {
    write4((value & 0xF0) | rs);
    write4(((value << 4) & 0xF0) | rs);
  }
  void write4(uint8_t data) {
    expander(data | 0x04);          // EN high
    delayMicroseconds(1);
    expander(data & ~0x04);         // EN low latches the nibble
    delayMicroseconds(50);
  }
  void expander(uint8_t data) {
    Wire.beginTransmission(addr);
    Wire.write(data | light);
    Wire.endTransmission();
  }
};

I2cLcd lcd(0x27);   // some backpacks use 0x3F
HardwareSerial probe(2);

BLEServer *server;
BLECharacteristic *readingChar;
BLECharacteristic *infoChar;
volatile bool connected = false;
volatile bool measureRequested = false;
#define AUTO_CROP -1            // no crop chosen: the code is for the best-scoring crop
volatile int appCrop = AUTO_CROP;   // crop of the plot the app is measuring (CROP:<name>)
int farmerCrop = AUTO_CROP;         // crop picked on the device with a long press; saved
Preferences prefs;
uint16_t connId = 0;
char serial[20];

// ---------------- Kasaka Guidebook crops (same as CropProfiles.kt) ----------------
// One row of a crop's "reading your numbers" table: below lowBelow = LOW, below goodFrom =
// SLIGHTLY LOW, up to goodTo = GOOD, above = HIGH. Change the book first, then this table.
struct Row { float lowBelow, goodFrom, goodTo; };
// BOX_ prefix: Arduino already defines LOW and HIGH.
enum Level { BOX_LOW, BOX_SLIGHTLY_LOW, BOX_GOOD, BOX_HIGH };
enum Sour { POOR, FAIR, BEST };   // "Copes with sour soil?" (book page 7)
struct Crop {
  const char *name;   // as in the app
  const char *lcd;    // fits the 16x2 LCD
  Row ph, n, p, k;
  Sour sour;
  bool limeOnlyWhenLow;     // sweet potato: code 2 only below pH 4.5
  bool highNitrogenHurts;   // sweet potato, ampalaya
};
const Crop CROPS[] = {
  {"Kangkong",     "Kangkong", {5.0, 5.5, 7.0}, {25, 40, 100}, { 8, 15, 40}, {50,  80, 200}, FAIR, false, false},
  {"Pechay",       "Pechay",   {5.0, 5.5, 7.0}, {30, 50, 110}, {12, 20, 50}, {60, 100, 220}, POOR, false, false},
  {"Sweet potato", "Kamote",   {4.5, 5.5, 6.8}, {15, 25,  60}, { 8, 15, 40}, {75, 120, 250}, BEST, true,  true},
  {"Okra",         "Okra",     {5.0, 5.5, 7.0}, {25, 40,  90}, {12, 20, 50}, {60, 100, 220}, FAIR, false, false},
  {"Eggplant",     "Eggplant", {5.0, 5.5, 6.8}, {30, 50, 110}, {12, 20, 50}, {70, 110, 230}, FAIR, false, false},
  {"Ampalaya",     "Ampalaya", {5.0, 5.5, 6.5}, {25, 40, 100}, {12, 20, 50}, {60, 100, 200}, FAIR, false, true},
  {"Corn",         "Corn",     {5.0, 5.5, 7.0}, {30, 50, 110}, {10, 15, 40}, {50,  80, 200}, FAIR, false, false},
};
const int CROP_COUNT = sizeof(CROPS) / sizeof(CROPS[0]);

// ---------------- Kasaka Guidebook codes (same as Guidebook.kt, book page 39) ----------------
// Code = enum value + 1. Short titles fit the LCD after "Code N ".
enum Condition { BALANCED, ACIDIC, ALKALINE, N_LOW, P_LOW, K_LOW, MULTI_LOW };
const char *CODE_TITLE[] = {"All good", "Too sour", "Alkaline", "N low", "P low", "K low", "Several low"};

struct Reading { float ph; int n, p, k; bool ok; float temp, hum; };
struct Ranked  { int crop; int score; };

// ======================================================================
// Soil probe (Modbus RTU, function 0x03)
// ======================================================================
uint16_t modbusCrc(const uint8_t *buf, int len) {
  uint16_t crc = 0xFFFF;
  for (int i = 0; i < len; i++) {
    crc ^= buf[i];
    for (int b = 0; b < 8; b++) crc = (crc & 1) ? (crc >> 1) ^ 0xA001 : crc >> 1;
  }
  return crc;
}

// Reads `count` consecutive registers into `out`.
bool readRegistersOnce(uint16_t reg, uint8_t count, uint16_t *out) {
  uint8_t req[8] = {PROBE_ADDR, 0x03, (uint8_t)(reg >> 8), (uint8_t)reg, 0x00, count};
  uint16_t crc = modbusCrc(req, 6);
  req[6] = crc & 0xFF;
  req[7] = crc >> 8;

  while (probe.available()) probe.read();
  digitalWrite(RS485_DE_RE, HIGH);
  probe.write(req, 8);
  probe.flush();
  digitalWrite(RS485_DE_RE, LOW);

  const int len = 5 + 2 * count;
  uint8_t res[32];
  int got = 0;
  unsigned long start = millis();
  while (got < len && millis() - start < 600) {
    if (probe.available()) res[got++] = probe.read();
  }
  if (got < len || res[0] != PROBE_ADDR || res[1] != 0x03) return false;
  if (modbusCrc(res, len - 2) != (uint16_t)(res[len - 2] | (res[len - 1] << 8))) return false;
  for (int i = 0; i < count; i++) out[i] = (res[3 + 2 * i] << 8) | res[4 + 2 * i];
  return true;
}

// Retries a few times so one noisy reply doesn't fail the whole scan.
bool readRegisters(uint16_t reg, uint8_t count, uint16_t *out) {
  for (int attempt = 0; attempt < 3; attempt++) {
    if (readRegistersOnce(reg, count, out)) return true;
    delay(200);
  }
  return false;
}

float demoPh() { return random(63, 69) / 10.0; }   // typical healthy soil, 6.3-6.8

// Demo temperature and humidity (26-30 C, 45-65 %); the current probe has no such sensor.
void demoTempHum(Reading &r) {
  r.temp = random(260, 300) / 10.0;
  r.hum = random(450, 650) / 10.0;
}

Reading readSoil() {
  Reading r = {0, 0, 0, 0, false, 0, 0};
  demoTempHum(r);
  if (DEMO_MODE) {
    delay(2000);
    r.ph = demoPh();
    r.n = random(25, 95);
    r.p = random(15, 60);
    r.k = random(35, 95);
    r.ok = true;
    return r;
  }
  // Average 3 samples to steady the readings; skip a sample that fails instead of giving up.
  float ph = 0; long n = 0, p = 0, k = 0;
  int good = 0, goodPh = 0;
  for (int i = 0; i < 3; i++) {
    uint16_t npk[3];
    if (readRegisters(REG_NPK, 3, npk)) {
      n += npk[0]; p += npk[1]; k += npk[2];
      good++;
    }
    uint16_t vPh;
    if (PH_FROM_PROBE && readRegisters(REG_PH, 1, &vPh)) {
      ph += vPh / PH_SCALE;
      goodPh++;
    }
    delay(1000);
  }
  if (good == 0) return r;
  r.n = n / good; r.p = p / good; r.k = k / good;
  r.ph = goodPh ? ph / goodPh : demoPh();   // falls back to demo pH if the probe has none
  r.ok = true;
  return r;
}

int batteryPercent() {
  float volts = analogReadMilliVolts(BATTERY_PIN) * 2 / 1000.0;   // 1:2 divider
  if (volts < 2.5) return -1;                                      // no battery wired
  return constrain((int)((volts - 3.3) / (4.2 - 3.3) * 100), 0, 100);
}

// ======================================================================
// On-device analysis (same rules as SoilAnalyzer.kt)
// ======================================================================
// The book's tables use one decimal for pH, so 5.45 reads as 5.5, like the farmer sees it.
float bookPh(float ph) { return roundf(ph * 10) / 10.0; }

Level level(float v, const Row &r) {
  if (v < r.lowBelow) return BOX_LOW;
  if (v < r.goodFrom) return BOX_SLIGHTLY_LOW;
  if (v <= r.goodTo) return BOX_GOOD;
  return BOX_HIGH;
}
bool isShort(Level l) { return l == BOX_LOW || l == BOX_SLIGHTLY_LOW; }

// pH comes first: low or slightly low pH is code 2 (sweet potato: only below 4.5), high pH is 3.
// Otherwise two or more short nutrients is 7, one is 4, 5 or 6.
Condition soilCondition(const Reading &s, const Crop &c) {
  Level ph = level(bookPh(s.ph), c.ph);
  bool sour = c.limeOnlyWhenLow ? ph == BOX_LOW : isShort(ph);
  if (sour) return ACIDIC;
  if (ph == BOX_HIGH) return ALKALINE;
  bool n = isShort(level(s.n, c.n)), p = isShort(level(s.p, c.p)), k = isShort(level(s.k, c.k));
  int lows = n + p + k;
  if (lows >= 2) return MULTI_LOW;
  if (n) return N_LOW;
  if (p) return P_LOW;
  if (k) return K_LOW;
  return BALANCED;
}

// "NP low" style text for code 7, so the LCD says which nutrients are short.
void shortList(const Reading &s, const Crop &c, char *out, size_t len) {
  snprintf(out, len, "%s%s%s low", isShort(level(s.n, c.n)) ? "N" : "", isShort(level(s.p, c.p)) ? "P" : "",
           isShort(level(s.k, c.k)) ? "K" : "");
}

float nutrientScore(int v, const Row &r, bool highHurts) {
  switch (level(v, r)) {
    case BOX_GOOD: return 100;
    case BOX_SLIGHTLY_LOW: return 65;
    case BOX_LOW: return 30;
    default: return highHurts ? 40 : 85;
  }
}

int suitability(const Reading &s, const Crop &c) {
  float phScore;
  switch (level(bookPh(s.ph), c.ph)) {
    case BOX_GOOD: phScore = 100; break;
    case BOX_SLIGHTLY_LOW: phScore = c.sour == BEST ? 85 : (c.sour == FAIR ? 60 : 45); break;
    case BOX_LOW: phScore = c.sour == BEST ? 45 : (c.sour == FAIR ? 20 : 10); break;
    default: phScore = 30;
  }
  return lround(0.35 * phScore + 0.25 * nutrientScore(s.n, c.n, c.highNitrogenHurts) +
                0.20 * nutrientScore(s.p, c.p, false) + 0.20 * nutrientScore(s.k, c.k, false));
}

void rankCrops(const Reading &s, Ranked top[3]) {
  for (int i = 0; i < 3; i++) top[i] = {-1, -1};
  for (int c = 0; c < CROP_COUNT; c++) {
    int score = suitability(s, CROPS[c]);
    for (int i = 0; i < 3; i++) {
      if (score > top[i].score) {
        for (int j = 2; j > i; j--) top[j] = top[j - 1];
        top[i] = {c, score};
        break;
      }
    }
  }
}

// Name as the app sends it ("Sweet potato") or as the LCD shows it ("Kamote"); unknown = best crop.
int findCrop(const String &name) {
  for (int i = 0; i < CROP_COUNT; i++)
    if (name.equalsIgnoreCase(CROPS[i].name) || name.equalsIgnoreCase(CROPS[i].lcd)) return i;
  return AUTO_CROP;
}

// ======================================================================
// BLE
// ======================================================================
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *s, esp_ble_gatts_cb_param_t *param) override {
    connected = true;
    connId = param->connect.conn_id;
  }
  void onDisconnect(BLEServer *s) override {
    connected = false;   // loop() restarts advertising so the app can reconnect
  }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    String cmd = String(c->getValue().c_str());
    cmd.trim();
    cmd.toUpperCase();
    if (cmd.startsWith("CROP:")) appCrop = findCrop(cmd.substring(5));   // "CROP:" = best crop
    else if (cmd == "MEASURE") measureRequested = true;   // handled in loop(), not in the BLE task
  }
};

void updateInfo() {
  char json[80];
  int bat = batteryPercent();
  if (bat >= 0) snprintf(json, sizeof(json), "{\"sn\":\"%s\",\"fw\":\"%s\",\"bat\":%d}", serial, FW_VERSION, bat);
  else          snprintf(json, sizeof(json), "{\"sn\":\"%s\",\"fw\":\"%s\"}", serial, FW_VERSION);
  infoChar->setValue(json);
}

// Sends the JSON as one or more notifications; the app joins chunks until the object is complete.
void notifyReading(const String &json) {
  readingChar->setValue(json.c_str());   // also readable by the app
  if (!connected) return;
  int chunk = server->getPeerMTU(connId) - 3;
  if (chunk < 20) chunk = 20;
  for (int i = 0; i < (int)json.length(); i += chunk) {
    String part = json.substring(i, min((int)json.length(), i + chunk));
    readingChar->setValue((uint8_t *)part.c_str(), part.length());
    readingChar->notify();
    delay(20);
  }
}

void setupBle() {
  BLEDevice::init(serial);
  BLEDevice::setMTU(247);
  server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);
  readingChar = service->createCharacteristic(READING_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  readingChar->addDescriptor(new BLE2902());   // CCCD so the app can subscribe

  BLECharacteristic *commandChar = service->createCharacteristic(COMMAND_UUID, BLECharacteristic::PROPERTY_WRITE);
  commandChar->setCallbacks(new CommandCallbacks());

  infoChar = service->createCharacteristic(INFO_UUID, BLECharacteristic::PROPERTY_READ);
  updateInfo();
  service->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();
}

// ======================================================================
// Measure, show, send
// ======================================================================
#define AUTO_MEASURE_MS  10000   // take a reading on its own every 10 s
#define LCD_PAGE_MS      3000    // switch between the values page and the crop page

Reading last = {0, 0, 0, 0, false, 0, 0};
int lastCrop = 0;           // crop the code is for
bool lastAuto = true;       // that crop was the best match, not one the farmer chose
Condition lastCond = BALANCED;
bool probeError = false;

void lcdShow(const char *line1, const char *line2) {
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print(line1);
  lcd.setCursor(0, 1); lcd.print(line2);
}

// Cycles: page 0 soil values, page 1 temperature + humidity, page 2 crop + Guidebook code.
// "App" shows when the phone is connected.
#define LCD_PAGES 3
void lcdPage(int page) {
  char l1[17], l2[17];
  if (probeError) {
    snprintf(l2, sizeof(l2), "Retrying...  %s", connected ? "App" : "");
    lcdShow("Probe error", l2);
  } else if (!last.ok) {
    lcdShow("SakaSense", "Reading soil...");
  } else if (page == 0) {
    snprintf(l1, sizeof(l1), "pH %.1f  N %d", last.ph, last.n);
    snprintf(l2, sizeof(l2), "P %-5d  K %d", last.p, last.k);
    lcdShow(l1, l2);
  } else if (page == 1) {
    snprintf(l1, sizeof(l1), "Tempt: %.1fC", last.temp);
    snprintf(l2, sizeof(l2), " Hum: %.1f%%  %s", last.hum, connected ? "App" : "");
    lcdShow(l1, l2);
  } else {
    snprintf(l1, sizeof(l1), "%s %s", lastAuto ? "Best:" : "Crop:", CROPS[lastCrop].lcd);
    if (lastCond == MULTI_LOW) {
      char lows[8];
      shortList(last, CROPS[lastCrop], lows, sizeof(lows));
      snprintf(l2, sizeof(l2), "Code 7 %s", lows);
    } else {
      snprintf(l2, sizeof(l2), "Code %d %s", lastCond + 1, CODE_TITLE[lastCond]);
    }
    lcdShow(l1, l2);
  }
}

// Takes a reading, shows it, and sends it to the app when `sendToApp` (the app asked for it).
// The code is for `crop`, or for the best-scoring book crop when it is AUTO_CROP.
void measure(bool sendToApp, int crop) {
  if (sendToApp) lcdShow("Measuring soil..", "Keep probe still");
  Reading r = readSoil();
  probeError = !r.ok;
  if (!r.ok) {
    Serial.println("Probe error: no reply from the NPK probe");
    lcdPage(0);
    return;   // the app times out and asks the farmer to retry
  }

  Ranked top[3];
  rankCrops(r, top);
  lastAuto = crop == AUTO_CROP;
  lastCrop = lastAuto ? top[0].crop : crop;
  lastCond = soilCondition(r, CROPS[lastCrop]);
  last = r;
  lcdPage(0);

  String json = "{\"ph\":" + String(r.ph, 2) + ",\"n\":" + r.n + ",\"p\":" + r.p + ",\"k\":" + r.k;
  // Extra fields; the app ignores ones it doesn't know yet.
  json += ",\"temp\":" + String(r.temp, 1) + ",\"hum\":" + String(r.hum, 1);
  int bat = batteryPercent();
  if (bat >= 0) json += ",\"bat\":" + String(bat);
  json += ",\"sn\":\"" + String(serial) + "\",\"crops\":[";
  for (int i = 0; i < 3 && top[i].crop >= 0; i++) {
    if (i) json += ",";
    json += "{\"crop\":\"" + String(CROPS[top[i].crop].name) + "\",\"score\":" + top[i].score + "}";
  }
  json += "],\"gb\":\"" + String(lastCond + 1) + "\",\"gbCrop\":\"" + String(CROPS[lastCrop].name) + "\"}";

  Serial.println(json);
  if (sendToApp) notifyReading(json);
  else readingChar->setValue(json.c_str());   // keep the latest reading readable without pushing it
  updateInfo();
}

void setup() {
  Serial.begin(115200);
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(RS485_DE_RE, OUTPUT);
  digitalWrite(RS485_DE_RE, LOW);
  probe.begin(PROBE_BAUD, SERIAL_8N1, RS485_RX, RS485_TX);
  randomSeed(esp_random());

  // Serial number from the chip MAC, e.g. KS-ESP32-004821 (the app scans for "KS-")
  uint64_t mac = ESP.getEfuseMac();
  snprintf(serial, sizeof(serial), "KS-ESP32-%06lu", (unsigned long)((mac >> 24) % 1000000));

  prefs.begin("kasaka", false);
  farmerCrop = prefs.getInt("crop", AUTO_CROP);
  if (farmerCrop >= CROP_COUNT) farmerCrop = AUTO_CROP;

  lcd.init();
  lcd.backlight();
  lcdShow("SakaSense", serial);

  setupBle();
  Serial.printf("%s ready (fw %s)%s\n", serial, FW_VERSION, DEMO_MODE ? " - DEMO MODE" : "");
  delay(1500);
  measure(false, farmerCrop);   // first reading right after power-on, no button needed
}

void loop() {
  static bool wasConnected = false;
  static unsigned long lastMeasure = millis();
  static unsigned long lastPage = 0;
  static int page = 0;

  // Background connection handling: after the app leaves, advertise again so it can reconnect.
  if (connected != wasConnected) {
    wasConnected = connected;
    Serial.println(connected ? "App connected" : "App disconnected");
    if (!connected) {
      delay(300);
      BLEDevice::startAdvertising();
    }
    lcdPage(page);
  }

  // Button: short press = measure now, hold 2 s = next crop (Auto, Kangkong, ... Corn).
  if (digitalRead(BUTTON_PIN) == LOW) {
    delay(50);
    if (digitalRead(BUTTON_PIN) == LOW) {
      unsigned long down = millis();
      while (digitalRead(BUTTON_PIN) == LOW && millis() - down < 2000) delay(10);
      if (millis() - down >= 2000) {
        farmerCrop = farmerCrop + 1 >= CROP_COUNT ? AUTO_CROP : farmerCrop + 1;
        prefs.putInt("crop", farmerCrop);
        lcdShow("Crop for code:", farmerCrop == AUTO_CROP ? "Auto (best crop)" : CROPS[farmerCrop].lcd);
        while (digitalRead(BUTTON_PIN) == LOW) delay(10);
        delay(1200);
      }
      measure(connected, farmerCrop);
      lastMeasure = millis();
      lastPage = millis();
    }
  }

  if (measureRequested) {                    // "Measure" tapped in the app; the app sent the plot's crop
    measureRequested = false;
    measure(true, appCrop);
    lastMeasure = millis();
  } else if (millis() - lastMeasure >= AUTO_MEASURE_MS) {
    measure(false, farmerCrop);
    lastMeasure = millis();
  }

  if (millis() - lastPage >= LCD_PAGE_MS) {
    lastPage = millis();
    page = (page + 1) % LCD_PAGES;
    lcdPage(page);
  }
  delay(10);
}
