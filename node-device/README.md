# Device Management API with Node JS Express

REST API sederhana untuk data device IoT dan telemetry.

## Cara Menjalankan

```bash
npm install
npm start
```

Server berjalan di:

```text
http://localhost:3000
```

Swagger:

```text
http://localhost:3000/api-docs
```

## Database

Default database memakai PostgreSQL untuk master data device di port 5432 dengan nama database `device_db`:

```env
DATABASE_URL=postgres://user:password@localhost:5432/device_db
```

Telemetry disimpan di Cassandra. Table yang digunakan:

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

Format topic:

```text
gedung-solu/monitoring/lantai-1/devices/{device_uuid}/telemetry
```

Format payload:

```json
{
  "ts": 1781490918553,
  "temperature": 27.47,
  "humidity": 61.1
}
```

Arsitektur database:

```text
PostgreSQL = menyimpan master data device
Cassandra  = menyimpan telemetry/time-series data
```

Write telemetry dibuat ringan untuk pola IoT production:

```text
1. API cek device langsung ke PostgreSQL.
2. Data telemetry langsung ditulis ke Cassandra.
3. Query history telemetry dibaca dari Cassandra berdasarkan device_id dan record_month.
```

Folder `src/repository` berisi kode akses database:

```text
device.repository.js              = lookup device dari PostgreSQL
telemetry.cassandra.repository.js = query insert/read telemetry ke Cassandra
```

Connection pool juga bisa diatur lewat `.env`:

```env
DB_POOL_MAX=10
DB_POOL_MIN=0
DB_POOL_ACQUIRE=30000
DB_POOL_IDLE=10000
```

Artinya:

```text
DB_POOL_MAX     = maksimal koneksi database yang boleh dibuka aplikasi
DB_POOL_MIN     = minimal koneksi yang dijaga tetap hidup
DB_POOL_ACQUIRE = batas waktu menunggu koneksi tersedia dalam millisecond
DB_POOL_IDLE    = waktu koneksi idle sebelum ditutup dalam millisecond
```

Kalau nanti mau ganti database, ubah lewat `.env`:

```env
DATABASE_URL=
```

Jangan Lupa Install PG jika menggunakan postgresql

```bash
npm install pg
```

Atau untuk SQLite file:

```env
DB_DIALECT=sqlite
DB_STORAGE=database.sqlite
```

Semua konfigurasi database ada di:

```text
src/db/index.js
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

`id` pada device memakai UUID.
`ts` dibuat otomatis oleh API saat request diterima, lalu disimpan di Cassandra dan dikembalikan sebagai Unix epoch milliseconds.
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
