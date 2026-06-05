# Device Management API with Java Spring Boot

REST API sederhana untuk data device IoT dan telemetry.

## Cara Menjalankan

```bash
mvnw.cmd spring-boot:run
```

Server berjalan di:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/openapi.json
```

## Database

Default database memakai SQLite in-memory:

```text
DATABASE_URL=jdbc:sqlite:file:management-api?mode=memory&cache=shared
```

Artinya data selalu kosong lagi setiap server restart.

Kalau nanti mau ganti database, ubah lewat environment variable:

```env
DATABASE_URL=jdbc:sqlite:./devices.db
```

Semua konfigurasi database ada di:

```text
src/main/resources/application.properties
src/main/resources/schema.sql
src/main/java/com/device/management_api/repository
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
`ts` pada telemetry memakai Format Unix Timestamp (Epoch Time).
