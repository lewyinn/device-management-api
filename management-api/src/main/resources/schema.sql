CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('active', 'inactive'))
);

CREATE TABLE IF NOT EXISTS device_telemetries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    ts BIGINT NOT NULL,
    temperature REAL NOT NULL,
    humidity REAL NOT NULL,
    UNIQUE (device_id, ts),
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);
