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

Default database memakai SQLite in-memory:

```text
DATABASE_URL=sqlite://
```

Artinya data selalu kosong lagi setiap server restart.

Kalau nanti mau ganti database, ubah lewat `.env`:

```env
DATABASE_URL=postgresql://user:password@localhost:5432/device_db
```

Atau untuk SQLite file:

```env
DATABASE_URL=sqlite:///./devices.db
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
