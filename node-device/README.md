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
    ts timestamp,
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
DEVICE_CACHE_TTL_MS=300000
```

Arsitektur database:

```text
PostgreSQL = menyimpan master data device
Cassandra  = menyimpan telemetry/time-series data
```

Write telemetry dibuat ringan untuk pola IoT production:

```text
1. API cek device dari cache terlebih dahulu.
2. Kalau belum ada di cache, API ambil device dari PostgreSQL.
3. Data telemetry langsung ditulis ke Cassandra.
4. Query history telemetry dibaca dari Cassandra berdasarkan device_id dan record_month.
```

Folder `src/repository` berisi kode akses database:

```text
device.repository.js              = lookup device dari PostgreSQL + cache sederhana
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
GET    /api/v1/devices/{device_id}/telemetry?start_month=2026-06&end_month=2026-06
GET    /api/v1/devices/{device_id}/telemetry/latest
```

`id` pada device memakai UUID.
`ts` pada telemetry memakai Format Unix Timestamp (Epoch Time)
