# Device Management API with Java Spring Boot

Backend Spring Boot untuk mengelola device IoT, menerima telemetry lewat HTTP dan MQTT, menyimpan master data device ke SQL database, menyimpan telemetry ke Cassandra, lalu mengirim update realtime ke frontend lewat WebSocket.

---

## Quick Start

```bash
copy .env.example .env
.\mvnw.cmd spring-boot:run
```

Server:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/api-docs
```

OpenAPI JSON:

```text
http://localhost:8080/openapi.json
```

WebSocket:

```text
ws://localhost:8080/ws
```

Root URL:

```text
http://localhost:8080/
```

Root URL akan redirect ke Swagger UI.

---


### Main Responsibility

| Layer | Technology | Responsibility |
| --- | --- | --- |
| REST API | Spring Web MVC | CRUD device, telemetry history, telemetry latest |
| SQL Database | JPA/Hibernate | Transactional data seperti `devices` |
| NoSQL Database | Cassandra Java Driver | Time-series data `device_telemetries` |
| MQTT Subscriber | HiveMQ MQTT Client | Subscribe telemetry dari device simulator atau real device |
| WebSocket | Spring WebSocket | Push live telemetry dan device registration ke dashboard |
| Notification | Telegram Bot API | Kirim callback notification saat device berhasil dibuat |
| API Docs | springdoc OpenAPI | Dokumentasi endpoint REST |

---

## Project Structure

```text
src/main/java/com/device/management_api/
|-- config/
|   |-- DatabasePostgresConfig.java
|   `-- DatabaseCassandraConfig.java
|-- controller/
|   |-- DeviceController.java
|   |-- DeviceTelemetryController.java
|   `-- RootController.java
|-- model/
|   |-- postgres/
|   |   `-- Device.java
|   `-- cassandra/
|       `-- TelemetryReading.java
|-- mqtt/
|   |-- MqttProperties.java
|   |-- MqttTelemetryPayload.java
|   `-- MqttTelemetrySubscriber.java
|-- repository/
|   |-- postgres/
|   |   `-- DeviceRepository.java
|   `-- cassandra/
|       `-- CassandraTelemetryRepository.java
|-- service/
|   |-- DeviceService.java
|   |-- DeviceTelemetryService.java
|   |-- DeviceHttpProtectionService.java
|   |-- TelegramNotificationService.java
|   `-- impl/
|       |-- DeviceServiceImpl.java
|       |-- DeviceTelemetryServiceImpl.java
|       |-- DeviceHttpProtectionServiceImpl.java
|       `-- TelegramNotificationServiceImpl.java
|-- websocket/
|   |-- WebSocketConfig.java
|   `-- TelemetryWebSocketHandler.java
`-- Application.java
```

---

## Data Architecture

### SQL Database

SQL database adalah source of truth untuk master data device.

Untuk learning, default `.env.example` memakai SQLite in-memory:

```env
DATABASE_URL=jdbc:sqlite:file:management-api?mode=memory&cache=shared
DATABASE_DRIVER=org.sqlite.JDBC
DATABASE_DIALECT=org.hibernate.community.dialect.SQLiteDialect
DDL_AUTO=create-drop
```

Artinya data reset setiap server restart.

Untuk PostgreSQL:

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/device_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_password
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
DDL_AUTO=update
```

Untuk production, jangan pakai `DDL_AUTO=update`. Gunakan:

```env
DDL_AUTO=validate
```

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

App akan membuat keyspace dan table jika belum ada.

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

Clustering key:

```text
ts DESC
```

Saat startup, app juga validasi kolom `ts` harus bertipe `bigint`.

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
1. Connect ke MQTT broker mqtt://127.0.0.1:1883
2. Subscribe ke gedung-solu/monitoring/lantai-1/devices/+/telemetry
3. Ambil device_id dari topic
4. Parse JSON payload
5. Validasi device ada di SQL database
6. Ambil ts, temperature, humidity dari payload
7. Hitung record_month dari ts
8. Insert telemetry ke Cassandra
9. Broadcast telemetry ke WebSocket
```

File:

```text
src/main/java/com/device/management_api/mqtt/MqttTelemetrySubscriber.java
src/main/java/com/device/management_api/repository/cassandra/CassandraTelemetryRepository.java
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
ws://localhost:8080/ws
```

Subscribe:

```json
{
  "type": "subscribe",
  "channels": ["telemetry", "deviceRegistered"]
}
```

Channel yang tersedia:

| Channel | Fungsi |
| --- | --- |
| `telemetry` | Semua telemetry terbaru dari semua device |
| `device_telemetry:{device_uuid}` | Telemetry realtime untuk satu device |
| `deviceRegistered` | Event saat device baru berhasil didaftarkan |

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
  "details": "Device read endpoint only allows 100 requests every 60 seconds",
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
1. Simpan device ke SQL database
2. Broadcast device_registered ke WebSocket channel deviceRegistered
3. Kirim Telegram notification
4. Return response 201 ke client
```

Config:

```env
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

---

## Environment Variables

Contoh `.env` untuk SQLite in-memory:

```env
DATABASE_URL=jdbc:sqlite:file:management-api?mode=memory&cache=shared
DATABASE_USERNAME=
DATABASE_PASSWORD=
DATABASE_DRIVER=org.sqlite.JDBC
DATABASE_DIALECT=org.hibernate.community.dialect.SQLiteDialect
DDL_AUTO=create-drop

DB_POOL_MAX=1
DB_POOL_MIN=0
DB_POOL_CONNECTION_TIMEOUT=30000
DB_POOL_IDLE_TIMEOUT=600000
DB_POOL_MAX_LIFETIME=1800000

CASSANDRA_CONTACT_POINTS=127.0.0.1:9042
CASSANDRA_LOCAL_DATACENTER=datacenter1
CASSANDRA_KEYSPACE=device_management

MQTT_BROKER_URL=mqtt://127.0.0.1:1883
MQTT_TELEMETRY_TOPIC=gedung-solu/monitoring/lantai-1/devices/+/telemetry

TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

SQL pool:

| Variable | Fungsi |
| --- | --- |
| `DB_POOL_MAX` | Maksimal koneksi database yang boleh dibuka |
| `DB_POOL_MIN` | Minimal koneksi idle yang dijaga tetap hidup |
| `DB_POOL_CONNECTION_TIMEOUT` | Batas waktu menunggu koneksi tersedia dalam millisecond |
| `DB_POOL_IDLE_TIMEOUT` | Waktu koneksi idle sebelum ditutup dalam millisecond |
| `DB_POOL_MAX_LIFETIME` | Umur maksimal koneksi sebelum dibuat ulang dalam millisecond |

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

## Testing and Verification

Run tests:

```bash
.\mvnw.cmd test
```

Run server:

```bash
.\mvnw.cmd spring-boot:run
```

Open Swagger:

```text
http://localhost:8080/api-docs
```

---

## Design Notes

- SQL database adalah source of truth untuk device.
- Cassandra adalah source of truth untuk telemetry.
- MQTT adalah jalur utama ingestion telemetry dari device.
- HTTP telemetry tetap ada untuk testing manual.
- WebSocket dipakai untuk live dashboard.
- Swagger/OpenAPI dikonfigurasi langsung di `Application.java`.