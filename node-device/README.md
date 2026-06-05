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

Default database memakai SQLite in-memory:

```text
storage: ':memory:'
```

Artinya data selalu kosong lagi setiap server restart.

Kalau nanti mau ganti database, ubah lewat `.env`:

```env
DATABASE_URL=postgres://user:password@localhost:5432/device_db
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