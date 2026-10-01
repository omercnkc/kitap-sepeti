-- catalog-service ilk şeması.
-- Ortak kurallar: UUID'ler BINARY(16) ve uygulama tarafından üretilir (DB default'u yok);
-- zamanlar DATETIME(6), UTC saklanır; utf8mb4_0900_ai_ci collation büyük/küçük harf duyarsızdır.
-- Katalog kayıtları (yayınevi, yazar, kategori) kitap bağlıyken silinemez (RESTRICT).

-- Yayınevleri. slug URL'de kullanılır, benzersiz.
CREATE TABLE publishers (
    id         BINARY(16)   NOT NULL,
    name       VARCHAR(160) NOT NULL,
    slug       VARCHAR(160) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_publishers PRIMARY KEY (id),
    CONSTRAINT uk_publishers_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Yazarlar.
CREATE TABLE authors (
    id         BINARY(16)   NOT NULL,
    name       VARCHAR(160) NOT NULL,
    slug       VARCHAR(160) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_authors PRIMARY KEY (id),
    CONSTRAINT uk_authors_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kategoriler (ağaç). parent_id NULL = kök kategori; alt kategorisi olan kategori silinemez.
CREATE TABLE categories (
    id         BINARY(16)   NOT NULL,
    parent_id  BINARY(16)   NULL,
    name       VARCHAR(120) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kitaplar. Satılabilir stok = stock_quantity - reserved_quantity (her zaman >= 0).
CREATE TABLE books (
    id                BINARY(16)    NOT NULL,
    isbn              VARCHAR(20)   NULL,                   -- NULL olabilir; UNIQUE indeks NULL'ları saymaz
    title             VARCHAR(300)  NOT NULL,
    description       TEXT          NULL,
    publisher_id      BINARY(16)    NOT NULL,
    page_count        INT           NULL,
    cover_url         VARCHAR(500)  NULL,
    price_amount      DECIMAL(12,2) NOT NULL,
    currency          CHAR(3)       NOT NULL DEFAULT 'TRY', -- ISO 4217
    stock_quantity    INT           NOT NULL DEFAULT 0,
    reserved_quantity INT           NOT NULL DEFAULT 0,     -- aktif ('held') rezervasyonların toplamı
    status            VARCHAR(16)   NOT NULL DEFAULT 'draft', -- küçük harf: 'draft' | 'published' | 'archived'
    published_at      DATETIME(6)   NULL,
    version           BIGINT        NOT NULL DEFAULT 0,     -- JPA @Version (optimistic lock)
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_books PRIMARY KEY (id),
    CONSTRAINT uk_books_isbn UNIQUE (isbn),
    CONSTRAINT fk_books_publisher FOREIGN KEY (publisher_id) REFERENCES publishers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_books_status CHECK (status IN ('draft', 'published', 'archived')),
    CONSTRAINT ck_books_stock_non_negative CHECK (stock_quantity >= 0),
    CONSTRAINT ck_books_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_books_reserved_le_stock CHECK (reserved_quantity <= stock_quantity),
    CONSTRAINT ck_books_price_non_negative CHECK (price_amount >= 0),
    CONSTRAINT ck_books_page_count CHECK (page_count IS NULL OR page_count > 0),
    -- Yayındaki kitapları yeniden eskiye / fiyata göre listeleme sorguları için.
    INDEX ix_books_status_published_at (status, published_at),
    INDEX ix_books_status_price (status, price_amount)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kitap-yazar (çoka çok). Kitap silinince bağlar da silinir; kitabı olan yazar silinemez.
CREATE TABLE book_authors (
    book_id   BINARY(16) NOT NULL,
    author_id BINARY(16) NOT NULL,
    CONSTRAINT pk_book_authors PRIMARY KEY (book_id, author_id),
    CONSTRAINT fk_book_authors_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_authors_author FOREIGN KEY (author_id) REFERENCES authors (id) ON DELETE RESTRICT,
    -- "Yazarın kitapları" sorgusu için (PK book_id ile başladığı için bunu karşılamaz).
    INDEX ix_book_authors_author (author_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kitap-kategori (çoka çok). Kitap silinince bağlar da silinir; kitabı olan kategori silinemez.
CREATE TABLE book_categories (
    book_id     BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    CONSTRAINT pk_book_categories PRIMARY KEY (book_id, category_id),
    CONSTRAINT fk_book_categories_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_categories_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    -- "Kategorideki kitaplar" sorgusu için.
    INDEX ix_book_categories_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Sipariş için ayrılan stok. Sipariş başına kitap başına tek rezervasyon.
-- Rezervasyonu olan kitap silinemez (geçmiş korunur; kitap 'archived' yapılır).
CREATE TABLE stock_reservations (
    id         BINARY(16)  NOT NULL,
    book_id    BINARY(16)  NOT NULL,
    order_id   BINARY(16)  NOT NULL,                        -- order-service'teki sipariş; servisler arası FK yok
    quantity   INT         NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'held',         -- küçük harf: 'held' | 'committed' | 'released'
    expires_at DATETIME(6) NOT NULL,                        -- 'held' bu zamana kadar onaylanmazsa serbest bırakılır
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_stock_reservations PRIMARY KEY (id),
    CONSTRAINT uk_stock_reservations_order_book UNIQUE (order_id, book_id),
    CONSTRAINT fk_stock_reservations_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_reservations_status CHECK (status IN ('held', 'committed', 'released')),
    -- Süresi dolan 'held' rezervasyonları bulan temizlik işi için.
    INDEX ix_stock_reservations_status_expires (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Transactional outbox (user-service ile aynı yapı): olaylar iş verisiyle aynı transaction'da yazılır,
-- ayrı bir yayıncı gönderip published_at'i doldurur.
CREATE TABLE outbox (
    id             BINARY(16)  NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,                    -- ör. 'Book'
    aggregate_id   BINARY(16)  NOT NULL,                    -- ilgili kaydın id'si
    event_type     VARCHAR(64) NOT NULL,                    -- ör. 'BookPublished'
    payload        JSON        NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at   DATETIME(6) NULL,                        -- NULL = henüz yayınlanmadı
    CONSTRAINT pk_outbox PRIMARY KEY (id),
    -- Yayıncının "yayınlanmamışları eskiden yeniye getir" sorgusu için.
    INDEX ix_outbox_published_at_created_at (published_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
