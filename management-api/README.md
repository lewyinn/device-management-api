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

Default database memakai SQLite in-memory dan ORM Hibernate/JPA:

```text
DATABASE_URL=jdbc:sqlite:file:management-api?mode=memory&cache=shared
```

Artinya data selalu kosong lagi setiap server restart.

Sebelum menjalankan server, buat file `.env` dari `.env.example`.

Di Windows:

```bash
copy .env.example .env
```

Kalau nanti mau ganti database, ubah isi `.env`:

```env
DATABASE_URL=jdbc:sqlite:./devices.db
```

Contoh kalau mau pakai PostgreSQL:

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/device_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_password
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
DDL_AUTO=update
```

Semua konfigurasi database ada di:

```text
src/main/resources/application.properties
src/main/java/com/device/management_api/entity
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
