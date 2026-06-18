# Device Management API with Python FastApi

REST API sederhana untuk data device IoT dan telemetry.

## Cara Menjalankan

```bash
pip install -r requirements.txt
python run.py
```

Server berjalan di:

```text
http://localhost:8000
```

Swagger:

```text
http://localhost:8000/api-docs
```

WebSocket:

```text
ws://localhost:8000/ws
```

Subscribe ke channel:

```json
{
  "type": "subscribe",
  "channels": ["telemetry", "deviceRegister"]
}
```

Channel yang tersedia:

```text
telemetry
deviceRegister
device_telemetry:{device_uuid}
```

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

Event device registration:

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

## Database

Backend memakai dua database:

```text
PostgreSQL = menyimpan master data device
Cassandra  = menyimpan telemetry/time-series data
```

Default PostgreSQL dipakai untuk data device:

```env
DATABASE_URL=postgresql://user:password@localhost:5432/device_db
```

Connection pool PostgreSQL juga bisa diatur lewat `.env`:

```env
DB_POOL_SIZE=10
DB_MAX_OVERFLOW=5
DB_POOL_TIMEOUT=30
DB_POOL_RECYCLE=1800
```

Artinya:

```text
DB_POOL_SIZE    = jumlah koneksi utama yang dijaga oleh aplikasi
DB_MAX_OVERFLOW = koneksi tambahan sementara saat traffic naik
DB_POOL_TIMEOUT = batas waktu menunggu koneksi tersedia dalam detik
DB_POOL_RECYCLE = umur koneksi sebelum dibuat ulang dalam detik
```

Kalau nanti mau ganti database, ubah lewat `.env`:

```env
DATABASE_URL=
```

Semua konfigurasi database ada di:

```text
app/core/database.py
```

Telemetry disimpan di Cassandra memakai table:

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

Config Cassandra bisa diatur lewat `.env`:

```env
CASSANDRA_CONTACT_POINTS=127.0.0.1
CASSANDRA_LOCAL_DATACENTER=datacenter1
CASSANDRA_KEYSPACE=device_management
```

Telemetry juga bisa masuk lewat MQTT subscriber:

```env
MQTT_BROKER_URL=mqtt://127.0.0.1:1883
MQTT_TELEMETRY_TOPIC=gedung-solu/monitoring/lantai-1/devices/+/telemetry
```

Format topic MQTT:

```text
gedung-solu/monitoring/lantai-1/devices/{device_uuid}/telemetry
```

Format payload MQTT:

```json
{
  "ts": 1781490918553,
  "temperature": 27.47,
  "humidity": 61.1
}
```

Semua konfigurasi Cassandra ada di:

```text
app/core/cassandra.py
```

## Endpoint Utama

```text
GET    /api/v1/devices
POST   /api/v1/devices
GET    /api/v1/devices/{device_id}
PUT    /api/v1/devices/{device_id}
PATCH  /api/v1/devices/{device_id}
DELETE /api/v1/devices/{device_id}

POST   /api/v1/devices/{device_id}/telemetry
GET    /api/v1/devices/{device_id}/telemetry?start_month=2026-01&end_month=2026-12
GET    /api/v1/devices/{device_id}/telemetry/latest
```

`device_id` memakai UUID.
`ts` dibuat otomatis oleh API saat request diterima, lalu disimpan di Cassandra dan dikembalikan sebagai Unix epoch milliseconds.
`record_month` dihitung dari nilai `ts`.
Default query history telemetry adalah `start_month=2026-01` dan `end_month=2026-12`.

Contoh payload telemetry:

```json
{
  "values": {
    "temperature": 28.5,
    "humidity": 75.2
  }
}
```
