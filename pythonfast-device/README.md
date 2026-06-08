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

## Database

Default database memakai PostgreSQL:

```env
DATABASE_URL=postgresql://user:password@localhost:5432/device_db
```

Artinya data disimpan di PostgreSQL di port 5432 dengan nama database `device_db`.

Connection pool juga bisa diatur lewat `.env`:

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
DELETE /api/v1/telemetry/{telemetry_id}
```

`device_id` memakai UUID.
`ts` pada telemetry memakai Format Unix Timestamp (Epoch Time)
