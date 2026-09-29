CREATE TABLE users (
    id            BINARY(16)   NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    phone         VARCHAR(32)  NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'active',
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('active', 'suspended')),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE addresses (
    id             BINARY(16)   NOT NULL,
    user_id        BINARY(16)   NOT NULL,
    label          VARCHAR(40)  NULL,
    recipient_name VARCHAR(120) NOT NULL,
    phone          VARCHAR(32)  NOT NULL,
    line1          VARCHAR(200) NOT NULL,
    line2          VARCHAR(200) NULL,
    district       VARCHAR(80)  NULL,
    city           VARCHAR(80)  NOT NULL,
    postal_code    VARCHAR(16)  NULL,
    country        CHAR(2)      NOT NULL DEFAULT 'TR',
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- VIRTUAL, not STORED: MySQL rejects ON DELETE CASCADE on the base column of a STORED generated column.
    default_owner  BINARY(16) AS (CASE WHEN is_default THEN user_id ELSE NULL END) VIRTUAL,
    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_addresses_default_owner UNIQUE (default_owner)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id         BINARY(16)   NOT NULL,
    user_id    BINARY(16)   NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outbox (
    id             BINARY(16)  NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   BINARY(16)  NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    payload        JSON        NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at   DATETIME(6) NULL,
    CONSTRAINT pk_outbox PRIMARY KEY (id),
    INDEX ix_outbox_published_at_created_at (published_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
