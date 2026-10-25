# SakaSense device ↔ Kasaka app — Bluetooth LE protocol (v1)

This is the contract the Android app expects from the SakaSense field device (ESP32).
The app side lives in `app/src/main/java/com/sakasense/kasaka/device/`. **If the firmware
already uses different UUIDs or payloads, change `DeviceProtocol.kt` to match and update this file.**

## Advertising

- Device name starts with `SakaSense` or `KS-` (e.g. `KS-ESP32-004821`), **or**
- the advertisement includes the service UUID below.

The app scans for 12 seconds and lists any device matching either rule.

## GATT service

| Item | UUID | Properties |
|---|---|---|
| Service | `7d2a0001-5a4b-4c53-9a1e-5341534b4153` | — |
| Reading | `7d2a0002-5a4b-4c53-9a1e-5341534b4153` | read, notify |
| Command | `7d2a0003-5a4b-4c53-9a1e-5341534b4153` | write (with response) |
| Info | `7d2a0004-5a4b-4c53-9a1e-5341534b4153` | read (optional) |

The Reading characteristic must have the standard CCCD descriptor (`0x2902`) so the app can subscribe.

## Session flow

1. App connects, requests an MTU of **247**, and discovers services.
2. App enables notifications on **Reading**.
3. App reads **Info** (optional): `{"sn":"KS-ESP32-004821","fw":"2.1.0","bat":86}`
4. When the farmer taps **Measure soil**, the app writes the ASCII string `MEASURE` to **Command**.
5. The device takes a reading (the app waits up to 45 s) and sends one JSON object as a
   notification on **Reading**.

## Reading payload

```json
{"ph":6.21,"n":48,"p":40,"k":58,"bat":86,"sn":"KS-ESP32-004822","ts":1730000000,
 "crops":[{"crop":"Tomato","score":74},{"crop":"Sweet potato","score":68}],"gb":"3902"}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `ph` | number | yes | Soil pH |
| `n`, `p`, `k` | integer | yes | Nitrogen, phosphorus, potassium in mg/kg |
| `bat` | integer | no | Battery % |
| `sn` | string | no | Serial number (falls back to Info `sn`) |
| `ts` | integer | no | Unix time (seconds) of the reading |
| `crops` | array | no | The device's own crop ranking. If present, the app shows it instead of its own |
| `gb` | string | no | Guidebook code shown on the device LCD. Must exist in the app's Guidebook to be used |

- Unknown fields are ignored, so the firmware can add fields without breaking older apps.
- With MTU 247, keep the JSON under ~240 bytes. If it is longer, split it across several
  notifications. The app concatenates chunks until it has a complete `{…}` object.

## Without Bluetooth

Farmers can always type the four values from the device LCD in **Scan → Enter values manually**.
The app runs the same analysis offline.
