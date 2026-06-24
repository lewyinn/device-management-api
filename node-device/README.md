# Device Management API with Node.js Express

Backend Express untuk mengelola device IoT, menerima telemetry dari HTTP dan MQTT, menyimpan master data ke PostgreSQL, menyimpan telemetry ke Cassandra, lalu mengirim update real-time ke frontend lewat WebSocket.

---

## Quick Start

```bash
npm install
npm run dev
```

Server:

```text
http://localhost:3000
```

Swagger:

```text
http://localhost:3000/api-docs
```

WebSocket:

```text
ws://localhost:3000/ws
```

---

### Main Responsibility

| Layer | Technology | Responsibility |
| --- | --- | --- |
| REST API | Express | CRUD device, telemetry history, telemetry latest, recent telemetry |
| SQL Database | PostgreSQL + Sequelize | Transactional data seperti `devices` |
| NoSQL Database | Cassandra | Time-series data `device_telemetries` |
| MQTT Subscriber | `mqtt` package | Subscribe telemetry dari device simulator atau real device |
| WebSocket | `ws` package | Push live telemetry dan device registration ke dashboard |
| Notification | Telegram Bot API | Kirim callback notification saat device berhasil dibuat |
| API Docs | Swagger UI | Dokumentasi endpoint REST |

---

## Data Architecture

### PostgreSQL

PostgreSQL dipakai untuk data transactional yang butuh konsistensi dan mudah di-query secara relational.

Table utama:

```text
devices
```

Isi data:

```text
id      = UUID device
name    = nama device, contoh Sensor-Suhu
type    = tipe device, contoh PM2120
status  = active atau inactive
```

### Cassandra

Cassandra dipakai untuk telemetry/time-series karena data sensor bisa masuk terus menerus dan volumenya besar.

Table:

```sql
CREATE TABLE device_telemetries (
    device_id uuid,
    record_month text,
    ts bigint,
    temperature double,
    humidity double,
    PRIMARY KEY ((device_id, record_month), ts)
) WITH CLUSTERING ORDER BY (ts DESC);
```

Partition key:

```text
(device_id, record_month)
```

Artinya data telemetry dipisah per device dan per bulan. Query history bisa mengambil data berdasarkan device dan range bulan.

Clustering key:

```text
ts DESC
```

Artinya data terbaru keluar lebih dulu.

---

## Telemetry Write Flow

### 1. MQTT Telemetry

Device simulator atau real device publish ke topic:

```text
gedung-solu/monitoring/lantai-1/devices/{device_uuid}/telemetry
```

Payload:

```json
{
  "ts": 1781490918553,
  "temperature": 27.47,
  "humidity": 61.1
}
```

Backend melakukan:

```text
1. Subscribe ke topic gedung-solu/monitoring/lantai-1/devices/+/telemetry
2. Ambil device_id dari topic
3. Parse JSON payload
4. Validasi device_id ada di PostgreSQL
5. Ambil ts, temperature, humidity dari payload
6. Hitung record_month dari ts
7. Insert telemetry ke Cassandra
8. Broadcast telemetry ke WebSocket channel
```

File:

```text
src/mqtt/telemetrySubscriber.js
src/repository/telemetry.cassandra.repository.js
```

### 2. HTTP Telemetry

Endpoint:

```http
POST /api/v1/devices/{device_id}/telemetry
```

Request body:

```json
{
  "values": {
    "temperature": 28.5,
    "humidity": 75.2
  }
}
```

---

## Telemetry Read Flow

### History by Device

```http
GET /api/v1/devices/{device_id}/telemetry?start_month=2026-01&end_month=2026-12
```

Default:

```text
start_month = 2026-01
end_month   = 2026-12
```

Backend query Cassandra per bulan, gabungkan hasilnya, lalu sort terbaru dulu.

### Latest by Device

```http
GET /api/v1/devices/{device_id}/telemetry/latest
```

Backend mengambil telemetry terbaru berdasarkan `ts` terbesar.

### Recent Across Devices

```http
GET /api/v1/telemetry/recent?limit=10
```

Endpoint ini dipakai untuk initial load dashboard, misalnya table "latest telemetry" dari semua device.

---

## WebSocket Realtime Flow

Frontend connect ke:

```text
ws://localhost:3000/ws
```

Setelah connect, client kirim message subscribe:

```json
{
  "type": "subscribe",
  "channels": ["telemetry", "devices"]
}
```

Channel yang tersedia:

| Channel | Fungsi |
| --- | --- |
| `telemetry` | Semua telemetry terbaru dari semua device |
| `device_telemetry:{device_uuid}` | Telemetry realtime untuk satu device |
| `devices` | Event saat device baru berhasil didaftarkan |

Event telemetry:

```json
{
  "type": "telemetry_received",
  "data": {
    "device_id": "550e8400-e29b-41d4-a716-446655440000",
    "deviceName": "Sensor-Suhu",
    "deviceType": "PM2120",
    "data": {
      "ts": 1781490918553,
      "temperature": 27.47,
      "humidity": 61.1
    }
  }
}
```

Event device registered:

```json
{
  "type": "device_registered",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Sensor-Suhu",
    "type": "PM2120",
    "status": "active"
  }
}
```

---

## HTTP Protection

Protection hanya dipasang di endpoint device utama:

| Endpoint | Protection | Tujuan |
| --- | --- | --- |
| `POST /api/v1/devices` | Throttling | Mencegah spam register device terlalu cepat |
| `GET /api/v1/devices` | Rate limit | Membatasi jumlah request list device dalam window tertentu |

Telemetry tidak diberi rate limit/throttling karena telemetry realtime masuk lewat MQTT, bukan polling HTTP.

Response throttling:

```json
{
  "error": "Request throttled",
  "type": "throttling",
  "details": "Device registration endpoint only accepts one request every 1000 milliseconds",
  "retry_after_ms": 953
}
```

Response rate limit:

```json
{
  "error": "Rate limit exceeded",
  "type": "rate_limiting",
  "details": "Device read endpoint only allows 60 requests every 60 seconds",
  "retry_after_seconds": 59
}
```

---

## Telegram Callback

Saat device berhasil dibuat lewat:

```http
POST /api/v1/devices
```

Backend akan:

```text
1. Simpan device ke PostgreSQL
2. Return response 201 ke client
3. Broadcast device_registered ke WebSocket channel devices
4. Kirim notifikasi Telegram secara async
5. Kalau Telegram gagal, error hanya dicatat di log
```

Config:

```env
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

---

## Environment Variables

Contoh `.env`:

```env
PORT=3000
DATABASE_URL=postgres://user:password@localhost:5432/device_db

DB_POOL_MAX=10
DB_POOL_MIN=0
DB_POOL_ACQUIRE=30000
DB_POOL_IDLE=10000

CASSANDRA_CONTACT_POINTS=127.0.0.1
CASSANDRA_LOCAL_DATACENTER=datacenter1
CASSANDRA_KEYSPACE=device_management

MQTT_BROKER_URL=mqtt://127.0.0.1:1883
MQTT_TELEMETRY_TOPIC=gedung-solu/monitoring/lantai-1/devices/+/telemetry

TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

Database pool:

| Variable | Fungsi |
| --- | --- |
| `DB_POOL_MAX` | Maksimal koneksi SQL yang boleh dibuka |
| `DB_POOL_MIN` | Minimal koneksi SQL yang dijaga tetap hidup |
| `DB_POOL_ACQUIRE` | Batas waktu menunggu koneksi tersedia dalam millisecond |
| `DB_POOL_IDLE` | Waktu koneksi idle sebelum ditutup dalam millisecond |

---

## Endpoint Summary

### Devices

```http
POST   /api/v1/devices
GET    /api/v1/devices?page=1&limit=10
GET    /api/v1/devices/{device_id}
PUT    /api/v1/devices/{device_id}
PATCH  /api/v1/devices/{device_id}
DELETE /api/v1/devices/{device_id}
```

### Telemetry

```http
POST /api/v1/devices/{device_id}/telemetry
GET  /api/v1/devices/{device_id}/telemetry?start_month=2026-01&end_month=2026-12
GET  /api/v1/devices/{device_id}/telemetry/latest
GET  /api/v1/telemetry/recent?limit=10
```

---

## Project Structure

```text
node-device/
|-- index.js
|-- src/
|   |-- index.js
|   |-- controller/
|   |   |-- device.controller.js
|   |   `-- telemetry.controller.js
|   |-- db/
|   |   |-- index.js
|   |   `-- models/
|   |       `-- Device.js
|   |-- middleware/
|   |   |-- deviceHttpProtection.middleware.js
|   |   `-- errorHandler.js
|   |-- mqtt/
|   |   `-- telemetrySubscriber.js
|   |-- repository/
|   |   |-- device.repository.js
|   |   `-- telemetry.cassandra.repository.js
|   |-- routes/
|   |   |-- device.routes.js
|   |   `-- telemetry.routes.js
|   |-- service/
|   |   `-- telegram.service.js
|   `-- websocket/
|       `-- websocketServer.js
|-- test/
|-- package.json
`-- README.md
```

---

## Testing

Run semua test:

```bash
npm test
```

Run unit test MQTT:

```bash
npm test -- test/unit/mqtt/telemetrySubscriber.test.js
```

Run WebSocket integration test:

```bash
npm test -- test/integration/websocket.test.js
```

Run Device API integration test:

```bash
npm test -- test/integration/device.api.test.js
```

Device API integration test memakai PostgreSQL test database. Pastikan `.env.test` mengarah ke database test, bukan database development.

Contoh:

```env
DATABASE_URL=postgresql://postgres:your_password@localhost:5432/devices_db_test
```

---

TERIMAKASIH
