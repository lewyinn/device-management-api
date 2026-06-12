# Device Management API with Java Spring Boot

REST API sederhana untuk data device IoT dan telemetry.

Arsitektur database:

```text
PostgreSQL / SQLite + JPA = data transactional devices
Cassandra               = data time-series device_telemetries
```

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

Default SQL database memakai SQLite in-memory dan ORM Hibernate/JPA:

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

Untuk learning/dev, `DDL_AUTO=update` masih boleh dipakai. Untuk production, gunakan:

```env
DDL_AUTO=validate
```

Telemetry disimpan di Cassandra. App akan membuat keyspace dan table jika belum ada:

```env
CASSANDRA_CONTACT_POINTS=127.0.0.1:9042
CASSANDRA_LOCAL_DATACENTER=datacenter1
CASSANDRA_KEYSPACE=device_management
```

Table Cassandra:

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

Connection pool HikariCP juga bisa diatur lewat `.env`:

```env
DB_POOL_MAX=10
DB_POOL_MIN=0
DB_POOL_CONNECTION_TIMEOUT=30000
DB_POOL_IDLE_TIMEOUT=600000
DB_POOL_MAX_LIFETIME=1800000
```

Artinya:

```text
DB_POOL_MAX                = maksimal koneksi database yang boleh dibuka aplikasi
DB_POOL_MIN                = minimal koneksi idle yang dijaga tetap hidup
DB_POOL_CONNECTION_TIMEOUT = batas waktu menunggu koneksi tersedia dalam millisecond
DB_POOL_IDLE_TIMEOUT       = waktu koneksi idle sebelum ditutup dalam millisecond
DB_POOL_MAX_LIFETIME       = umur maksimal koneksi sebelum dibuat ulang dalam millisecond
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
GET    /api/v1/devices/{device_id}/telemetry?start_month=2026-01&end_month=2026-12
GET    /api/v1/devices/{device_id}/telemetry/latest
```

`device_id` memakai UUID.
`ts` pada telemetry dibuat otomatis oleh backend dalam format Unix epoch milliseconds.
