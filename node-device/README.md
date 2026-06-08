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

Default database memakai PostgreSQL di port 5432 dengan nama database `device_db`:

```env
DATABASE_URL=postgres://user:password@localhost:5432/device_db
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
GET    /api/v1/devices/{device_id}/telemetry
GET    /api/v1/devices/{device_id}/telemetry/latest
DELETE /api/v1/telemetry/{id}
```

`id` pada device memakai UUID.
`ts` pada telemetry memakai Format Unix Timestamp (Epoch Time)
