-- payment-service ilk şeması.
-- Ortak kurallar: UUID'ler BINARY(16) ve uygulama tarafından üretilir (DB default'u yok);
-- zamanlar DATETIME(6), UTC saklanır ve uygulama (Clock) yazar; utf8mb4_0900_ai_ci collation büyük/küçük harf duyarsızdır.
-- order_id ve user_id başka servislerin kayıtlarıdır; servisler arası FK yok.
-- Kart verisi (numara, CVV, son kullanma) ve sağlayıcının ham istek/yanıt gövdesi SAKLANMAZ.

-- Ödemeler. Sipariş başına tek ödeme.
CREATE TABLE payments (
    id                  BINARY(16)    NOT NULL,
    order_id            BINARY(16)    NOT NULL,              -- order-service'teki sipariş
    user_id             BINARY(16)    NOT NULL,              -- user-service'teki kullanıcı
    -- utf8mb4_bin: CHECK'ler büyük/küçük harfe duyarlı ('Mock', 'SUCCEEDED', 'try' geçersiz).
    provider            VARCHAR(16)   COLLATE utf8mb4_bin NOT NULL,                       -- 'mock' | 'iyzico' | 'paytr' | 'stripe'
    provider_payment_id VARCHAR(128)  NULL,                  -- sağlayıcının ödeme referansı; oluşturulana kadar NULL
    amount              DECIMAL(12,2) NOT NULL,
    currency            CHAR(3)       COLLATE utf8mb4_bin NOT NULL DEFAULT 'TRY',         -- ISO 4217, büyük harf
    status              VARCHAR(16)   COLLATE utf8mb4_bin NOT NULL DEFAULT 'initiated',   -- 'initiated' | 'succeeded' | 'failed'
    failure_code        VARCHAR(64)   NULL,                  -- yalnızca 'failed' ödemede dolu
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    -- UNIQUE indeks NULL'ları saymadığı için referansı henüz olmayan ödemeler çakışmaz.
    CONSTRAINT uk_payments_provider_ref UNIQUE (provider, provider_payment_id),
    CONSTRAINT ck_payments_provider CHECK (provider IN ('mock', 'iyzico', 'paytr', 'stripe')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$', 'c')),
    CONSTRAINT ck_payments_status CHECK (status IN ('initiated', 'succeeded', 'failed')),
    CONSTRAINT ck_payments_failure CHECK ((status = 'failed') = (failure_code IS NOT NULL)),
    -- Sonuçlanmamış (initiated) ödemeleri eskiden yeniye bulan işler için.
    INDEX ix_payments_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- İşlenmiş sağlayıcı olayları (webhook). Aynı olay ikinci kez gelirse uk_provider_events_provider_event ile reddedilir.
-- Olayı olan ödeme silinemez (RESTRICT).
CREATE TABLE provider_events (
    id                BINARY(16)   NOT NULL,
    provider          VARCHAR(16)  COLLATE utf8mb4_bin NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,                 -- sağlayıcının olay kimliği
    payment_id        BINARY(16)   NOT NULL,
    event_type        VARCHAR(32)  COLLATE utf8mb4_bin NOT NULL,                          -- 'payment.succeeded' | 'payment.failed'
    processed_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_provider_events PRIMARY KEY (id),
    CONSTRAINT uk_provider_events_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT fk_provider_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT ck_provider_events_provider CHECK (provider IN ('mock', 'iyzico', 'paytr', 'stripe')),
    CONSTRAINT ck_provider_events_event_type CHECK (event_type IN ('payment.succeeded', 'payment.failed')),
    -- fk_provider_events_payment'ın indeksi de budur.
    INDEX ix_provider_events_payment (payment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Transactional outbox (catalog-service ile aynı yapı): olaylar iş verisiyle aynı transaction'da yazılır,
-- ayrı bir yayıncı gönderip published_at'i doldurur.
CREATE TABLE outbox (
    id             BINARY(16)  NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,                    -- ör. 'Payment'
    aggregate_id   BINARY(16)  NOT NULL,                    -- ilgili kaydın id'si
    event_type     VARCHAR(64) NOT NULL,                    -- ör. 'PaymentSucceeded'
    payload        JSON        NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at   DATETIME(6) NULL,                        -- NULL = henüz yayınlanmadı
    CONSTRAINT pk_outbox PRIMARY KEY (id),
    -- Yayıncının "yayınlanmamışları eskiden yeniye getir" sorgusu için.
    INDEX ix_outbox_published_at_created_at (published_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
