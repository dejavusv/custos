-- V3: สร้างตารางจัดเก็บความลับและข้อมูลการเชื่อมต่อ (Credentials Vault)

CREATE TABLE IF NOT EXISTS credentials_vault (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    credential_type VARCHAR(50) NOT NULL,
    encrypted_data TEXT NOT NULL,
    host VARCHAR(255),
    port INT,
    username VARCHAR(100),
    database_name VARCHAR(100),
    extra_metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_credentials_vault_name ON credentials_vault(name);
CREATE INDEX IF NOT EXISTS idx_credentials_vault_type ON credentials_vault(credential_type);
