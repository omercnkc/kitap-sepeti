-- user-service ilk şeması.
-- Ortak kurallar: UUID'ler BINARY(16) ve uygulama tarafından üretilir (DB default'u yok);
-- zamanlar DATETIME(6), UTC saklanır; utf8mb4_0900_ai_ci collation büyük/küçük harf duyarsızdır.

-- Kayıtlı kullanıcılar. E-posta benzersiz (collation sayesinde 'A@x.com' = 'a@x.com').
CREATE TABLE users (
    id            BINARY(16)   NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,                    -- şifrenin kendisi değil hash'i
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    phone         VARCHAR(32)  NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'active',   -- küçük harf: 'active' | 'suspended'
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',     -- büyük harf: 'USER' | 'ADMIN'
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('active', 'suspended')),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kullanıcının teslimat adresleri. Kullanıcı silinince adresleri de silinir (CASCADE).
CREATE TABLE addresses (
    id             BINARY(16)   NOT NULL,
    user_id        BINARY(16)   NOT NULL,
    label          VARCHAR(40)  NULL,                       -- ör. 'Ev', 'İş'
    recipient_name VARCHAR(120) NOT NULL,
    phone          VARCHAR(32)  NOT NULL,
    line1          VARCHAR(200) NOT NULL,
    line2          VARCHAR(200) NULL,
    district       VARCHAR(80)  NULL,
    city           VARCHAR(80)  NOT NULL,
    postal_code    VARCHAR(16)  NULL,
    country        CHAR(2)      NOT NULL DEFAULT 'TR',      -- ISO 3166-1 alpha-2
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- Kullanıcı başına tek varsayılan adres: varsayılan adreste user_id, diğerlerinde NULL olur.
    -- UNIQUE indeks NULL'ları saymadığı için aynı user_id ikinci kez varsayılan olamaz.
    -- VIRTUAL, STORED değil: MySQL, STORED generated kolonun kaynağı olan user_id üzerindeki FK'da ON DELETE CASCADE'e izin vermiyor.
    default_owner  BINARY(16) AS (CASE WHEN is_default THEN user_id ELSE NULL END) VIRTUAL,
    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_addresses_default_owner UNIQUE (default_owner)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Oturum yenileme token'ları. Ham token değil hash'i saklanır; iptal edilince revoked_at dolar.
CREATE TABLE refresh_tokens (
    id         BINARY(16)   NOT NULL,
    user_id    BINARY(16)   NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6)  NULL,                           -- NULL = aktif
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Transactional outbox: diğer servislere gidecek olaylar iş verisiyle aynı transaction'da yazılır,
-- ayrı bir yayıncı gönderip published_at'i doldurur.
CREATE TABLE outbox (
    id             BINARY(16)  NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,                    -- ör. 'User'
    aggregate_id   BINARY(16)  NOT NULL,                    -- ilgili kaydın id'si
    event_type     VARCHAR(64) NOT NULL,                    -- ör. 'UserRegistered'
    payload        JSON        NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at   DATETIME(6) NULL,                        -- NULL = henüz yayınlanmadı
    CONSTRAINT pk_outbox PRIMARY KEY (id),
    -- Yayıncının "yayınlanmamışları eskiden yeniye getir" sorgusu için.
    INDEX ix_outbox_published_at_created_at (published_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
