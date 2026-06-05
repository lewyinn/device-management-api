# Smart Device Registry API Documentation

## 1. Overview

API untuk registrasi dan manajemen perangkat IoT beserta pencatatan data telemetry (temperature & humidity). Mendukung operasi CRUD untuk Device dan Telemetry dengan format timestamp ala ThingsBoard.

---

## 2. Base Information

| Property          | Value |
|---                |---|
| Base URL          | `/api/v1` |
| Content-Type      | `application/json` |
| Authentication    | Tidak diperlukan |
| Swagger UI        | `http://localhost:3000/api-docs` |
| Database          | SQLite (in-memory) |

---

## 3. Authentication

Tidak diperlukan header authentication. Semua endpoint dapat dipanggil tanpa Authorization, Bearer Token, atau x-api-key.

---

## 4. Entity Schemas

### Device Schema

| Attribute | Type | Required | Description |
|---|---|---|---|
| `id` | string (uuid) | Auto | Identitas unik perangkat |
| `name` | string | Ya | Nama perangkat |
| `type` | string | Ya | Tipe/model perangkat |
| `status` | string | Tidak | Status perangkat (`active`, `inactive`). Default: `active` |

### Device Example Object

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Sensor-Suhu",
  "type": "PM2120",
  "status": "active"
}
```

### Telemetry Schema

| Attribute | Type | Description |
|---|---|---|
| `id` | integer | Identitas unik data telemetry |
| `device_id` | string (uuid) | ID device pemilik data |
| `ts` | bigint | Timestamp Unix epoch dalam milliseconds (auto-generated oleh server) |
| `temperature` | float | Nilai suhu |
| `humidity` | float | Nilai kelembaban |

> **Note:** Field `ts` di-generate otomatis oleh server menggunakan format timestamp ThingsBoard (Unix epoch milliseconds, contoh: `1717488000000`). Kombinasi `device_id` + `ts` bersifat unique.

### Telemetry Example Object

```json
{
  "device_id": "550e8400-e29b-41d4-a716-446655440000",
  "deviceName": "Sensor-Suhu",
  "deviceType": "PM2120",
  "data": {
    "ts": 1717488000000,
    "temperature": 28.5,
    "humidity": 75.2
  }
}
```

---

## 5. API Endpoints

### Daftar Endpoint

| Method | Endpoint | Deskripsi |
|---|---|---|
| `POST` | `/api/v1/devices` | Registrasi device baru |
| `GET` | `/api/v1/devices` | Ambil semua device (pagination) |
| `GET` | `/api/v1/devices/{device_id}` | Ambil detail device |
| `PUT` | `/api/v1/devices/{device_id}` | Update seluruh data device |
| `PATCH` | `/api/v1/devices/{device_id}` | Update sebagian data device |
| `DELETE` | `/api/v1/devices/{device_id}` | Hapus device |
| `POST` | `/api/v1/devices/{device_id}/telemetry` | Catat data telemetry |
| `GET` | `/api/v1/devices/{device_id}/telemetry` | Ambil semua telemetry device |
| `GET` | `/api/v1/devices/{device_id}/telemetry/latest` | Ambil telemetry terbaru device |
| `DELETE` | `/api/v1/telemetry/{id}` | Hapus data telemetry |

---

## A. Create Device

Mendaftarkan perangkat baru ke dalam sistem.

### Endpoint

```bash
POST /api/v1/devices
```

### Request Header

```bash
Content-Type: application/json
```

### Request Body

```json
{
  "name": "Sensor-Suhu",
  "type": "PM2120",
  "status": "active"
}
```

> **Note:** Field `status` bersifat opsional. Jika tidak dikirim, default-nya adalah `active`.

### Success Response

#### 201 Created

```json
{
  "message": "Device successfully registered",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Sensor-Suhu",
    "type": "PM2120",
    "status": "active"
  }
}
```

### Error Responses

#### 400 Bad Request
```json
{
  "error": "Validation failed",
  "details": "Attribute 'name' is required"
}
```

---

## B. Read All Devices

Mengambil daftar seluruh perangkat dengan pagination.

### Endpoint

```bash
GET /api/v1/devices?page=1&limit=10
```

### Query Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | integer | 1 | Nomor halaman |
| `limit` | integer | 10 | Jumlah data per halaman (maks. 50) |

### Request Header

```bash
```

### Request Body

Tidak diperlukan.

### Success Response

#### 200 OK

```json
{
  "message": "Success retrieving devices",
  "meta": {
    "page": 1,
    "limit": 10,
    "total_data": 50,
    "total_pages": 5
  },
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Sensor-Suhu",
      "type": "PM2120",
      "status": "active"
    }
  ]
}
```

---

## C. Read Device Detail

Mengambil detail satu perangkat berdasarkan ID.

### Endpoint

```bash
GET /api/v1/devices/{device_id}
```

### Example

```bash
GET /api/v1/devices/550e8400-e29b-41d4-a716-446655440000
```

### Request Header

```bash
```

### Request Body

Tidak diperlukan.

### Success Response

#### 200 OK

```json
{
  "message": "Device found",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Sensor-Suhu",
    "type": "PM2120",
    "status": "active"
  }
}
```

### Error Responses

#### 404 Not Found
```json
{
  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

---

## D. Update Device All (PUT)

Mengubah seluruh data atribut perangkat sekaligus. Semua data (`name`, `type`, `status`) **wajib** dikirimkan di dalam *Request Body*.

### Endpoint

```bash
PUT /api/v1/devices/{device_id}
```

### Example

```bash
PUT /api/v1/devices/550e8400-e29b-41d4-a716-446655440000
```

### Request Header

```bash
Content-Type: application/json
```

### Request Body

```json
{
  "name": "Sensor-Suhu-Updated",
  "type": "PM2120",
  "status": "inactive"
}
```

### Success Response

#### 200 OK

```json
{
  "message": "Device data fully updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Sensor-Suhu-Updated",
    "type": "PM2120",
    "status": "inactive"
  }
}
```

### Error Responses

#### 400 Bad Request
```json
{
  "error": "Validation failed",
  "details": "All attributes (name, type, status) are required for PUT method"
}
```

#### 404 Not Found
```json
{
  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

---

## E. Update Device Status (PATCH)

Mengubah status atau sebagian data perangkat.

### Endpoint

```bash
PATCH /api/v1/devices/{device_id}
```

### Example

```bash
PATCH /api/v1/devices/550e8400-e29b-41d4-a716-446655440000
```

### Request Header

```bash
Content-Type: application/json
```

### Request Body

```json
{
  "status": "inactive"
}
```

> **Note:** Minimal satu field harus dikirimkan. Field yang tersedia: `name`, `type`, `status`.

### Success Response

#### 200 OK

```json
{
  "message": "Device status updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Sensor-Suhu",
    "type": "PM2120",
    "status": "inactive"
  }
}
```

### Error Responses

#### 400 Bad Request
```json
{
  "error": "Validation failed",
  "details": "At least one field must be provided"
}
```

#### 404 Not Found
```json
{
  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

---

## F. Delete Device

Menghapus perangkat dari sistem. Data telemetry terkait juga akan terhapus (cascade delete).

### Endpoint

```bash
DELETE /api/v1/devices/{device_id}
```

### Example

```bash
DELETE /api/v1/devices/550e8400-e29b-41d4-a716-446655440000
```

### Request Header

```bash
```

### Request Body

Tidak diperlukan.

### Success Response

#### 204 No Content
Server tidak mengembalikan response body.

```bash
(No Content)
```

### Error Responses

#### 404 Not Found
```json
{
  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

---

## G. Create Device Telemetry

Mencatat data telemetry device. Timestamp (`ts`) di-generate otomatis oleh server dalam format Unix epoch milliseconds (sama seperti ThingsBoard).

### Endpoint

```bash
POST /api/v1/devices/{device_id}/telemetry
```

### Example

```bash
POST /api/v1/devices/550e8400-e29b-41d4-a716-446655440000/telemetry
```

### Request Header

```bash
Content-Type: application/json
```

### Request Body

```json
{
  "values": {
    "temperature": 28.5,
    "humidity": 75.2
  }
}
```

> **Note:** Field `values.temperature` dan `values.humidity` wajib berupa angka (number).

### Success Response

#### 201 Created

```json
{
  "message": "Telemetry successfully recorded",
  "data": {
    "device_id": "550e8400-e29b-41d4-a716-446655440000",
    "deviceName": "Sensor-Suhu",
    "deviceType": "PM2120",
    "data": {
      "ts": 1717488000000,
      "temperature": 28.5,
      "humidity": 75.2
    }
  }
}
```

### Error Responses

#### 400 Bad Request
```json
{
  "error": "Validation failed",
  "details": "Attributes 'values.temperature' and 'values.humidity' must be numbers"
}
```

#### 404 Not Found
```json
{
  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

#### 409 Conflict (Duplicate Timestamp)
```json
{
  "error": "Duplicate telemetry timestamp",
  "details": "Telemetry for device ID 550e8400-e29b-41d4-a716-446655440000 at ts 1717488000000 already exists"
}
```

---

## H. Get Device Telemetry

Mengambil seluruh data telemetry milik satu device, diurutkan dari `ts` terbaru.

### Endpoint

```bash
GET /api/v1/devices/{device_id}/telemetry
```

### Example

```bash
GET /api/v1/devices/550e8400-e29b-41d4-a716-446655440000/telemetry
```

### Request Header

```bash
```

### Request Body

Tidak diperlukan.

### Success Response

#### 200 OK

```json
{
  "message": "Success retrieving telemetry",
  "device_id": "550e8400-e29b-41d4-a716-446655440000",
  "deviceName": "Sensor-Suhu",
  "deviceType": "PM2120",
  "data": [
    {
      "ts": 1717488000000,
      "temperature": 28.5,
      "humidity": 75.2
    },
    {
      "ts": 1717484400000,
      "temperature": 27.3,
      "humidity": 80.1
    }
  ]
}
```

### Error Responses

#### 404 Not Found
```json
{
  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

---

## I. Get Latest Device Telemetry

Mengambil data telemetry terbaru milik satu device berdasarkan nilai `ts` terbesar.

### Endpoint

```bash
GET /api/v1/devices/{device_id}/telemetry/latest
```

### Example

```bash
GET /api/v1/devices/550e8400-e29b-41d4-a716-446655440000/telemetry/latest
```

### Request Header

```bash
```

### Request Body

Tidak diperlukan.

### Success Response

#### 200 OK

```json
{
  "message": "Latest telemetry found",
  "data": {
    "device_id": "550e8400-e29b-41d4-a716-446655440000",
    "deviceName": "Sensor-Suhu",
    "deviceType": "PM2120",
    "data": {
      "ts": 1717488000000,
      "temperature": 28.5,
      "humidity": 75.2
    }
  }
}
```

### Error Responses

#### 404 Not Found — Device tidak ditemukan
```json
{
  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

#### 404 Not Found — Telemetry tidak ditemukan
```json
{
  "error": "Telemetry for device ID 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

---

## J. Delete Telemetry

Menghapus data telemetry berdasarkan ID transaksi.

### Endpoint

```bash
DELETE /api/v1/telemetry/{id}
```

### Example

```bash
DELETE /api/v1/telemetry/1
```

### Request Header

```bash
```

### Request Body

Tidak diperlukan.

### Success Response

#### 204 No Content
Server tidak mengembalikan response body.

```bash
(No Content)
```

### Error Responses

#### 404 Not Found
```json
{
  "error": "Telemetry ID 1 not found"
}
```

---

## 6. Global Error Responses

Error berikut dapat terjadi pada **semua endpoint**:

### 500 Internal Server Error
Jika terjadi kesalahan di sisi server.
```json
{
  "error": "Internal server error"
}
```

---

## 7. HTTP Status Codes

| Code | Meaning |
|---|---|
| `200` | Request berhasil |
| `201` | Data berhasil dibuat |
| `204` | Data berhasil dihapus tanpa response body |
| `400` | Request tidak valid / validasi gagal |
| `404` | Data tidak ditemukan |
| `409` | Data duplikat (telemetry timestamp conflict) |
| `500` | Internal server error |
---

## 8. Notes

- Endpoint tidak membutuhkan header authentication.
- `id` device menggunakan UUID. `id` telemetry dibuat otomatis oleh sistem.
- `ts` (timestamp) pada telemetry dibuat otomatis oleh server dalam format Unix epoch milliseconds.
- Pagination digunakan pada endpoint daftar perangkat (`GET /devices`).
- Gunakan method HTTP sesuai standar REST API.
- Response menggunakan format JSON yang konsisten.