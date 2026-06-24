# Device Management API with Python FastAPI

Backend FastAPI untuk mengelola device IoT, menerima telemetry lewat HTTP dan MQTT, menyimpan master data device ke PostgreSQL, menyimpan telemetry ke Cassandra, lalu mengirim update realtime ke frontend lewat WebSocket.

---

## Quick Start

```bash
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
python run.py
```

Server:

```text
http://localhost:8000
```

Swagger:

```text
http://localhost:8000/api-docs
```

OpenAPI JSON:

```text
http://localhost:8000/openapi.json
```

WebSocket:

```text
ws://localhost:8000/ws
```

---

### Main Responsibility

| Layer | Technology | Responsibility |
| --- | --- | --- |
| REST API | FastAPI | CRUD device, telemetry history, telemetry latest |
| SQL Database | PostgreSQL + SQLAlchemy | Transactional data seperti `devices` |
| NoSQL Database | Cassandra | Time-series data `device_telemetries` |
| MQTT Subscriber | `fastapi-mqtt` | Subscribe telemetry dari device simulator atau real device |
| WebSocket | FastAPI WebSocket | Push live telemetry dan device registration ke dashboard |
| Notification | Telegram Bot API | Kirim callback notification saat device berhasil dibuat |
| API Docs | FastAPI Swagger UI | Dokumentasi endpoint REST |

---

## Project Structure

```text
app/
|-- api/
|   |-- dependencies/
|   |   `-- device_http_protection.py
|   |-- v1/
|   |   |-- endpoints/
|   |   |   |-- devices.py
|   |   |   `-- telemetry.py
|   |   `-- router.py
|   `-- swagger.py
|-- core/
|   |-- database_pg.py
|   `-- database_cassandra.py
|-- db/
|   |-- models_pg.py
|   `-- models_cassandra.py
|-- mqtt/
|   `-- mqtt.py
|-- services/
|   |-- device_service.py
|   |-- telemetry_service.py
|   `-- notification_service.py
|-- websocket/
|   `-- websocket.py
`-- main.py
```

---

## Data Architecture

### PostgreSQL

PostgreSQL adalah source of truth untuk master data device.

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

Cassandra adalah source of truth untuk telemetry/time-series.

Table:

```sql
CREATE TABLE IF NOT EXISTS device_telemetries (
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

Artinya telemetry dipisah per device dan bulan. Query history membaca data berdasarkan device dan range bulan.

Clustering key:

```text
ts DESC
```

Artinya telemetry terbaru keluar lebih dulu.

---

## Telemetry Write Flow

### 1. MQTT Telemetry

Topic:

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
4. Validasi device ada di PostgreSQL
5. Ambil ts, temperature, humidity dari payload
6. Hitung record_month dari ts
7. Insert telemetry ke Cassandra
8. Broadcast telemetry ke WebSocket
```

File:

```text
app/mqtt/mqtt.py
app/services/telemetry_service.py
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

Backend membaca Cassandra per bulan, menggabungkan hasil, lalu sort newest first.

### Latest by Device

```http
GET /api/v1/devices/{device_id}/telemetry/latest
```

Backend mencari telemetry terbaru dari range default `2026-01` sampai `2026-12`, dimulai dari bulan terbaru.

---

## WebSocket Realtime Flow

Frontend connect ke:

```text
ws://localhost:8000/ws
```

Subscribe:

```json
{
  "type": "subscribe",
  "channels": ["telemetry", "deviceRegister"]
}
```

Channel yang tersedia:

| Channel | Fungsi |
| --- | --- |
| `telemetry` | Semua telemetry terbaru dari semua device |
| `device_telemetry:{device_uuid}` | Telemetry realtime untuk satu device |
| `deviceRegister` | Event saat device baru berhasil didaftarkan |

Event telemetry:

```json
{
  "type": "telemetry_received",
  "data": {
    "device_id": "550e8400-e29b-41d4-a716-446655440000",
    "device_name": "Sensor-Suhu",
    "device_type": "PM2120",
    "ts": 1781490918553,
    "temperature": 27.47,
    "humidity": 61.1
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
| `GET /api/v1/devices` | Rate limit | Membatasi request list device dalam window tertentu |

Telemetry tidak diberi rate limit/throttling karena telemetry realtime masuk lewat MQTT.

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
  "details": "Device read endpoint only allows 3 requests every 60 seconds",
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
2. Broadcast device_registered ke WebSocket channel deviceRegister
3. Return response 201 ke client
4. Kirim Telegram notification lewat background task
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
PORT=8000
DATABASE_URL=postgresql://user:password@localhost:5432/device_db

DB_POOL_SIZE=10
DB_MAX_OVERFLOW=5
DB_POOL_TIMEOUT=30
DB_POOL_RECYCLE=1800

CASSANDRA_CONTACT_POINTS=127.0.0.1
CASSANDRA_LOCAL_DATACENTER=datacenter1
CASSANDRA_KEYSPACE=device_management

MQTT_BROKER_URL=mqtt://127.0.0.1:1883
MQTT_TELEMETRY_TOPIC=gedung-solu/monitoring/lantai-1/devices/+/telemetry

TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

PostgreSQL pool:

| Variable | Fungsi |
| --- | --- |
| `DB_POOL_SIZE` | Jumlah koneksi utama yang dijaga |
| `DB_MAX_OVERFLOW` | Koneksi tambahan sementara saat traffic naik |
| `DB_POOL_TIMEOUT` | Batas waktu menunggu koneksi tersedia dalam detik |
| `DB_POOL_RECYCLE` | Umur koneksi sebelum dibuat ulang dalam detik |

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
```

---

TERIMAKASIH