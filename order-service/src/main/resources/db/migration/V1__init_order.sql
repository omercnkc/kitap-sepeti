-- order-service ilk şeması.
-- Ortak kurallar: UUID'ler BINARY(16) ve uygulama tarafından üretilir (DB default'u yok);
-- zamanlar DATETIME(6), UTC saklanır ve uygulama (Clock) yazar; utf8mb4_0900_ai_ci collation büyük/küçük harf duyarsızdır.
-- user_id, cart_id, book_id ve payment_id başka servislerin kayıtlarıdır; servisler arası FK yok.
-- Para DECIMAL(12,2). Durum, makine kodu ve para birimi kolonları utf8mb4_bin: CHECK'ler büyük/küçük harfe duyarlı.

-- Siparişler. Kullanıcı başına en fazla bir 'pending' sipariş; paid / failed geçmiş olarak kalır.
-- v1'de 'cancelled' yok; ödeme zaman aşımı failed + failure_code 'ORDER_EXPIRED' olarak yazılır.
CREATE TABLE orders (
    id                     BINARY(16)    NOT NULL,
    user_id                BINARY(16)    NOT NULL,           -- user-service'teki kullanıcı (JWT sub)
    cart_id                BINARY(16)    NOT NULL,           -- cart-service'teki sepet; yalnızca iz için, sorgulanmaz
    status                 VARCHAR(16)   COLLATE utf8mb4_bin NOT NULL DEFAULT 'pending',    -- 'pending' | 'paid' | 'failed'
    -- Stok rezervasyonunun Catalog'daki durumu. Kayıt önce yazılır, Catalog çağrısı sonra: 'requested' ile başlar.
    stock_state            VARCHAR(16)   COLLATE utf8mb4_bin NOT NULL DEFAULT 'requested',  -- 'requested' | 'held' | 'committed' | 'released'
    currency               CHAR(3)       COLLATE utf8mb4_bin NOT NULL DEFAULT 'TRY',        -- ISO 4217, büyük harf
    subtotal               DECIMAL(12,2) NOT NULL,           -- kalemlerin line_total toplamı
    discount_amount        DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount           DECIMAL(12,2) NOT NULL,           -- ödenecek tutar = Payment'a giden amount
    -- v1'de kupon yok (her zaman NULL); biçimi kupon özelliğiyle belirlenecek, bu yüzden CHECK yok.
    coupon_code            VARCHAR(40)   COLLATE utf8mb4_bin NULL,
    address_snapshot       JSON          NOT NULL,           -- checkout isteğindeki teslimat adresi (o anki kopya)
    payment_id             BINARY(16)    NULL,               -- payment-service'teki ödeme; ödeme başlatılana kadar NULL
    failure_code           VARCHAR(64)   COLLATE utf8mb4_bin NULL,  -- yalnızca 'failed' siparişte dolu; ör. 'ORDER_EXPIRED'
    -- Yalnızca bekleyen siparişte user_id, diğerlerinde NULL. UNIQUE indeks NULL'ları saymadığı için
    -- kullanıcının ikinci bekleyen siparişi reddedilir, geçmiş siparişleri sınırsızdır.
    active_pending_user_id BINARY(16)    GENERATED ALWAYS AS (CASE WHEN status = 'pending' THEN user_id END) VIRTUAL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_pending_user UNIQUE (active_pending_user_id),
    -- Bir ödeme tek siparişe ait; NULL'lar (ödemesi başlamamış siparişler) çakışmaz.
    CONSTRAINT uk_orders_payment UNIQUE (payment_id),
    CONSTRAINT ck_orders_status CHECK (status IN ('pending', 'paid', 'failed')),
    CONSTRAINT ck_orders_stock_state CHECK (stock_state IN ('requested', 'held', 'committed', 'released')),
    CONSTRAINT ck_orders_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$', 'c')),
    CONSTRAINT ck_orders_subtotal CHECK (subtotal > 0),
    CONSTRAINT ck_orders_discount CHECK (discount_amount >= 0 AND discount_amount <= subtotal),
    CONSTRAINT ck_orders_total CHECK (total_amount = subtotal - discount_amount AND total_amount > 0),
    CONSTRAINT ck_orders_address_snapshot CHECK (JSON_TYPE(address_snapshot) = 'OBJECT'),
    -- NULL'da REGEXP_LIKE NULL döner ve CHECK geçer; doluluk kuralı ck_orders_failure'da.
    CONSTRAINT ck_orders_failure_code CHECK (REGEXP_LIKE(failure_code, '^[A-Z][A-Z0-9_]*$', 'c')),
    CONSTRAINT ck_orders_failure CHECK ((status = 'failed') = (failure_code IS NOT NULL)),
    CONSTRAINT ck_orders_paid_payment CHECK (status <> 'paid' OR payment_id IS NOT NULL),
    -- Durum ile stok durumu tutarlılığı: bekleyen sipariş stoğu henüz kesinleştirmemiş/bırakmamıştır; ödenmiş
    -- siparişin stoğu tutulmuş ya da kesinleşmiştir; stoğu yalnızca ödenmiş sipariş kesinleştirir.
    CONSTRAINT ck_orders_pending_stock CHECK (status <> 'pending' OR stock_state IN ('requested', 'held')),
    CONSTRAINT ck_orders_paid_stock CHECK (status <> 'paid' OR stock_state IN ('held', 'committed')),
    CONSTRAINT ck_orders_committed_paid CHECK (stock_state <> 'committed' OR status = 'paid'),
    -- Kullanıcının sipariş geçmişi (yeniden eskiye, id ile kararlı sayfalama).
    INDEX ix_orders_user_created (user_id, created_at, id),
    -- Süresi dolan bekleyen siparişleri eskiden yeniye bulan iş için.
    INDEX ix_orders_status_created (status, created_at),
    -- Catalog'a iletilememiş stok işlemlerini (requested, ödenmiş ama held vb.) bulan iş için.
    INDEX ix_orders_stock_state_updated (stock_state, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Sipariş kalemleri: checkout anındaki sepet kaleminin kopyası. Kalemi olan sipariş silinemez (RESTRICT).
CREATE TABLE order_items (
    id             BINARY(16)    NOT NULL,
    order_id       BINARY(16)    NOT NULL,
    book_id        BINARY(16)    NOT NULL,                   -- catalog-service'teki kitap
    title_snapshot VARCHAR(300)  NOT NULL,                   -- catalog books.title ile aynı uzunluk
    quantity       INT           NOT NULL,
    unit_price     DECIMAL(12,2) NOT NULL,                   -- catalog price_amount kopyası
    line_total     DECIMAL(12,2) NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    -- order_id ile başladığı için fk_order_items_order'ın indeksi de budur.
    CONSTRAINT uk_order_items_order_book UNIQUE (order_id, book_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    -- Üst sınır cart_items ile aynı (ck_cart_items_quantity); iş kuralındaki daha düşük sınırı cart uygular.
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    -- Catalog ücretsiz kitaba (0) izin verdiği için >= 0.
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_line_total CHECK (line_total = unit_price * quantity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Durum geçmişi: her durum değişikliği bir satır; ilk satır oluşturma (NULL → 'pending').
CREATE TABLE order_status_history (
    id          BINARY(16)  NOT NULL,
    order_id    BINARY(16)  NOT NULL,
    from_status VARCHAR(16) COLLATE utf8mb4_bin NULL,        -- yalnızca ilk satırda NULL
    to_status   VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
    reason      VARCHAR(64) COLLATE utf8mb4_bin NULL,        -- makine kodu; ör. 'PAYMENT_FAILED', 'ORDER_EXPIRED'
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_status_history_from CHECK (from_status IN ('pending', 'paid', 'failed')),
    CONSTRAINT ck_order_status_history_to CHECK (to_status IN ('pending', 'paid', 'failed')),
    CONSTRAINT ck_order_status_history_reason CHECK (REGEXP_LIKE(reason, '^[A-Z][A-Z0-9_]*$', 'c')),
    -- 'pending'e yalnızca oluşturmayla girilir ve oluşturma yalnızca 'pending'e olur.
    CONSTRAINT ck_order_status_history_initial CHECK ((from_status IS NULL) = (to_status = 'pending')),
    CONSTRAINT ck_order_status_history_change CHECK (from_status <> to_status),
    -- Siparişin geçmişini sırayla okumak için; order_id ile başladığı için FK indeksi de budur.
    INDEX ix_order_status_history_order_created (order_id, created_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Transactional outbox (diğer servislerle aynı yapı): olaylar iş verisiyle aynı transaction'da yazılır,
-- ayrı bir yayıncı gönderip published_at'i doldurur.
CREATE TABLE outbox (
    id             BINARY(16)  NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,                    -- ör. 'Order'
    aggregate_id   BINARY(16)  NOT NULL,                    -- ilgili kaydın id'si
    event_type     VARCHAR(64) NOT NULL,                    -- ör. 'OrderPaid'
    payload        JSON        NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at   DATETIME(6) NULL,                        -- NULL = henüz yayınlanmadı
    CONSTRAINT pk_outbox PRIMARY KEY (id),
    -- Yayıncının "yayınlanmamışları eskiden yeniye getir" sorgusu için.
    INDEX ix_outbox_published_at_created_at (published_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
