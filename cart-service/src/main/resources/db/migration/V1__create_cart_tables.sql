-- cart-service ilk şeması.
-- Ortak kurallar: UUID'ler BINARY(16) ve uygulama tarafından üretilir (DB default'u yok);
-- zamanlar DATETIME(6), UTC saklanır; utf8mb4_0900_ai_ci collation büyük/küçük harf duyarsızdır.
-- user_id ve book_id başka servislerin kayıtlarıdır; servisler arası FK yok.
-- Cart olay yayınlamaz: outbox tablosu yok.

-- Sepetler. Kullanıcı başına en fazla bir 'active' sepet; checked_out / abandoned geçmiş olarak kalır.
CREATE TABLE carts (
    id             BINARY(16)  NOT NULL,
    user_id        BINARY(16)  NOT NULL,                     -- user-service'teki kullanıcı (JWT sub)
    -- utf8mb4_bin: ck_carts_status ve active_user_id ifadesi büyük/küçük harfe duyarlı ('ACTIVE' geçersiz).
    status         VARCHAR(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'active', -- küçük harf: 'active' | 'checked_out' | 'abandoned'
    -- Yalnızca aktif sepette user_id, diğerlerinde NULL. UNIQUE indeks NULL'ları saymadığı için
    -- kullanıcının ikinci aktif sepeti reddedilir, geçmiş sepetleri sınırsızdır.
    active_user_id BINARY(16) GENERATED ALWAYS AS (CASE WHEN status = 'active' THEN user_id END) VIRTUAL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uk_carts_active_user UNIQUE (active_user_id),
    CONSTRAINT ck_carts_status CHECK (status IN ('active', 'checked_out', 'abandoned')),
    -- Kullanıcının sepet geçmişi sorgusu için (uk_carts_active_user yalnızca aktif sepeti kapsar).
    INDEX ix_carts_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Sepet satırları. Sepet başına kitap başına tek satır (adet artırılır, satır çoğaltılmaz).
-- *_snapshot kolonları kitabın sepete eklendiği andaki Catalog bilgisidir; canlı fiyat/stok ayrıca Catalog'dan okunur.
-- Sepet silinince satırları da silinir.
CREATE TABLE cart_items (
    id                  BINARY(16)    NOT NULL,
    cart_id             BINARY(16)    NOT NULL,
    book_id             BINARY(16)    NOT NULL,              -- catalog-service'teki kitap
    quantity            INT           NOT NULL,
    unit_price_snapshot DECIMAL(12,2) NOT NULL,
    currency_snapshot   CHAR(3)       NOT NULL DEFAULT 'TRY', -- ISO 4217
    title_snapshot      VARCHAR(300)  NOT NULL,
    cover_url_snapshot  VARCHAR(500)  NULL,
    added_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    -- cart_id ile başladığı için fk_cart_items_cart'ın indeksi de budur.
    CONSTRAINT uk_cart_items_cart_book UNIQUE (cart_id, book_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_cart_items_price CHECK (unit_price_snapshot >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
