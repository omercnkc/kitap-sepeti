-- Yerel geliştirme için örnek katalog verisi. YALNIZCA local profilinde yüklenir (application-local.yml).
-- Flyway repeatable migration: dosya değişince yeniden çalışır, bu yüzden idempotent yazılır:
-- sabit UUID değerleri + INSERT ... ON DUPLICATE KEY UPDATE (aynı satır güncellenir, kopya oluşmaz).
-- Not: Dosyadan çıkarılan bir bağ (kitap-yazar/kategori) yeniden çalıştırmada silinmez.
--
-- İçerik: 5 yazar, kategori ağacı (Edebiyat > Roman, Öykü; Bilim > Popüler Bilim; Çocuk),
-- 14 kitap (Open Library ISBN + kapak URL): 11 published (ikisi stoksuz/rezervli), 2 draft, 1 archived.
-- Kitaplardaki her rezerv, toplamı reserved_quantity'ye eşit 'held' rezervasyon satırlarıyla karşılanır
-- (değişmez: reserved_quantity = SUM(quantity WHERE status = 'held')). Bu satırlar 2099'a kadar geçerlidir;
-- süre dolumu görevi bunları bırakmaz. Yeniden çalıştırma kitapları ve seed rezervasyonlarını birlikte seed
-- durumuna döndürür; seed kitaplarında seed dışı 'held' rezervasyon varsa değişmez bozulur.

INSERT INTO authors (id, name, slug) VALUES
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000201'), 'Jane Austen', 'jane-austen'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000202'), 'George Orwell', 'george-orwell'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000203'), 'Harper Lee', 'harper-lee'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000204'), 'F. Scott Fitzgerald', 'f-scott-fitzgerald'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000205'), 'Herman Melville', 'herman-melville') AS new
ON DUPLICATE KEY UPDATE name = new.name, slug = new.slug;

-- Önce kökler, sonra alt kategoriler (parent FK).
INSERT INTO categories (id, parent_id, name, slug) VALUES
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000301'), NULL, 'Edebiyat', 'edebiyat'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000304'), NULL, 'Bilim', 'bilim'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000306'), NULL, 'Çocuk', 'cocuk') AS new
ON DUPLICATE KEY UPDATE parent_id = new.parent_id, name = new.name, slug = new.slug;

INSERT INTO categories (id, parent_id, name, slug) VALUES
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000302'), UUID_TO_BIN('01920000-0000-7000-8000-000000000301'), 'Roman', 'roman'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000303'), UUID_TO_BIN('01920000-0000-7000-8000-000000000301'), 'Öykü', 'oyku'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000305'), UUID_TO_BIN('01920000-0000-7000-8000-000000000304'), 'Popüler Bilim', 'populer-bilim') AS new
ON DUPLICATE KEY UPDATE parent_id = new.parent_id, name = new.name, slug = new.slug;

-- Kapaklar Open Library Books API / covers.openlibrary.org (ISBN lookup ile aynı kaynak).
INSERT INTO books (id, isbn, title, description, page_count, cover_url, price_amount, currency,
                   stock_quantity, reserved_quantity, status, published_at) VALUES
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000401'), '9780141439518', 'Pride and Prejudice',
     'Jane Austen''ın klasik aşk ve sınıf romanı.', 435,
     'https://covers.openlibrary.org/b/id/12645114-L.jpg',
     145.00, 'TRY', 10, 0, 'published', '2024-03-12 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000402'), '9780141187761', 'Nineteen Eighty-Four',
     'George Orwell''ın distopik başyapıtı.', 384,
     'https://covers.openlibrary.org/b/id/108160-L.jpg',
     120.50, 'TRY', 3, 3, 'published', '2024-06-01 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000403'), '9780141439600', 'A Tale of Two Cities',
     'Dickens''ın Fransız Devrimi romanı.', 489,
     'https://covers.openlibrary.org/b/id/8493695-L.jpg',
     89.90, 'TRY', 0, 0, 'published', '2023-11-20 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000404'), '9780743273565', 'The Great Gatsby',
     'Jazz çağı ve Amerikan rüyası.', 208,
     'https://covers.openlibrary.org/b/id/14314120-L.jpg',
     99.00, 'TRY', 7, 2, 'published', '2025-01-15 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000405'), '9780061120084', 'To Kill a Mockingbird',
     'Harper Lee''nin adalet ve çocukluk romanı.', 323,
     'https://covers.openlibrary.org/b/id/15162569-L.jpg',
     175.00, 'TRY', 20, 0, 'published', '2024-09-05 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000406'), '9780553213119', 'Moby-Dick',
     'Melville''in beyaz balina destanı.', 670,
     'https://covers.openlibrary.org/b/id/8742857-L.jpg',
     210.00, 'TRY', 4, 1, 'published', '2025-04-22 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000407'), '9780140449266', 'The Count of Monte Cristo',
     'İntikam ve adalet üzerine klasik macera.', 1276,
     'https://covers.openlibrary.org/b/id/14564134-L.jpg',
     65.00, 'TRY', 15, 0, 'published', '2024-04-23 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000408'), '9780142437230', 'Don Quixote',
     'Cervantes''in şövalye parodisi.', 1023,
     'https://covers.openlibrary.org/b/id/12137158-L.jpg',
     55.50, 'TRY', 8, 0, 'published', '2025-02-10 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000409'), '9780140449136', 'Crime and Punishment',
     'Dostoyevski''nin suç ve vicdan romanı.', 671,
     'https://covers.openlibrary.org/b/id/14935910-L.jpg',
     135.00, 'TRY', 6, 0, 'published', '2023-08-30 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000410'), '9780140449105', 'Utopia',
     'Thomas More''un ideal toplum metni.', 176,
     'https://covers.openlibrary.org/b/id/104340-L.jpg',
     160.00, 'TRY', 2, 0, 'published', '2025-06-18 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000411'), '9780316769488', 'The Catcher in the Rye',
     'Salinger''ın ergenlik klasiği.', 277,
     'https://covers.openlibrary.org/b/id/15172531-L.jpg',
     70.00, 'TRY', 12, 0, 'published', '2025-05-05 09:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000412'), '9781503290563', 'Pride and Prejudice (Taslak)',
     'Henüz yayımlanmadı.', 320,
     'https://covers.openlibrary.org/b/id/8097807-L.jpg',
     100.00, 'TRY', 0, 0, 'draft', NULL),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000413'), '9781503280786', 'Moby Dick (Taslak)',
     'Henüz yayımlanmadı.', 378,
     'https://covers.openlibrary.org/b/isbn/9781503280786-L.jpg',
     80.00, 'TRY', 5, 0, 'draft', NULL),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000414'), '9780142437247', 'Moby-Dick, or, The Whale',
     'Satıştan kaldırıldı.', 720,
     'https://covers.openlibrary.org/b/id/110556-L.jpg',
     95.00, 'TRY', 0, 0, 'archived', '2022-02-14 09:00:00.000000') AS new
ON DUPLICATE KEY UPDATE isbn = new.isbn, title = new.title, description = new.description,
    page_count = new.page_count, cover_url = new.cover_url,
    price_amount = new.price_amount, currency = new.currency, stock_quantity = new.stock_quantity,
    reserved_quantity = new.reserved_quantity, status = new.status, published_at = new.published_at;

-- Rezervli kitapların 'held' satırları: 402 = 2 + 1, 404 = 2, 406 = 1. Sipariş 601: 402 + 404; sipariş 602: 402 + 406.
INSERT INTO stock_reservations (id, book_id, order_id, quantity, status, expires_at) VALUES
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000501'), UUID_TO_BIN('01920000-0000-7000-8000-000000000402'),
     UUID_TO_BIN('01920000-0000-7000-8000-000000000601'), 2, 'held', '2099-12-31 00:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000502'), UUID_TO_BIN('01920000-0000-7000-8000-000000000404'),
     UUID_TO_BIN('01920000-0000-7000-8000-000000000601'), 2, 'held', '2099-12-31 00:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000503'), UUID_TO_BIN('01920000-0000-7000-8000-000000000402'),
     UUID_TO_BIN('01920000-0000-7000-8000-000000000602'), 1, 'held', '2099-12-31 00:00:00.000000'),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000504'), UUID_TO_BIN('01920000-0000-7000-8000-000000000406'),
     UUID_TO_BIN('01920000-0000-7000-8000-000000000602'), 1, 'held', '2099-12-31 00:00:00.000000') AS new
ON DUPLICATE KEY UPDATE book_id = new.book_id, order_id = new.order_id, quantity = new.quantity,
    status = new.status, expires_at = new.expires_at;

INSERT INTO book_authors (book_id, author_id) VALUES
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000401'), UUID_TO_BIN('01920000-0000-7000-8000-000000000201')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000402'), UUID_TO_BIN('01920000-0000-7000-8000-000000000202')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000403'), UUID_TO_BIN('01920000-0000-7000-8000-000000000204')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000404'), UUID_TO_BIN('01920000-0000-7000-8000-000000000204')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000405'), UUID_TO_BIN('01920000-0000-7000-8000-000000000203')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000406'), UUID_TO_BIN('01920000-0000-7000-8000-000000000205')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000407'), UUID_TO_BIN('01920000-0000-7000-8000-000000000202')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000408'), UUID_TO_BIN('01920000-0000-7000-8000-000000000205')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000409'), UUID_TO_BIN('01920000-0000-7000-8000-000000000202')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000410'), UUID_TO_BIN('01920000-0000-7000-8000-000000000204')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000411'), UUID_TO_BIN('01920000-0000-7000-8000-000000000203')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000412'), UUID_TO_BIN('01920000-0000-7000-8000-000000000201')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000413'), UUID_TO_BIN('01920000-0000-7000-8000-000000000205')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000414'), UUID_TO_BIN('01920000-0000-7000-8000-000000000205')) AS new
ON DUPLICATE KEY UPDATE author_id = new.author_id;

INSERT INTO book_categories (book_id, category_id) VALUES
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000401'), UUID_TO_BIN('01920000-0000-7000-8000-000000000302')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000402'), UUID_TO_BIN('01920000-0000-7000-8000-000000000302')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000403'), UUID_TO_BIN('01920000-0000-7000-8000-000000000303')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000404'), UUID_TO_BIN('01920000-0000-7000-8000-000000000302')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000404'), UUID_TO_BIN('01920000-0000-7000-8000-000000000303')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000405'), UUID_TO_BIN('01920000-0000-7000-8000-000000000302')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000406'), UUID_TO_BIN('01920000-0000-7000-8000-000000000305')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000406'), UUID_TO_BIN('01920000-0000-7000-8000-000000000304')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000407'), UUID_TO_BIN('01920000-0000-7000-8000-000000000306')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000408'), UUID_TO_BIN('01920000-0000-7000-8000-000000000306')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000408'), UUID_TO_BIN('01920000-0000-7000-8000-000000000303')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000409'), UUID_TO_BIN('01920000-0000-7000-8000-000000000302')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000410'), UUID_TO_BIN('01920000-0000-7000-8000-000000000305')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000411'), UUID_TO_BIN('01920000-0000-7000-8000-000000000306')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000412'), UUID_TO_BIN('01920000-0000-7000-8000-000000000302')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000413'), UUID_TO_BIN('01920000-0000-7000-8000-000000000303')),
    (UUID_TO_BIN('01920000-0000-7000-8000-000000000414'), UUID_TO_BIN('01920000-0000-7000-8000-000000000302')) AS new
ON DUPLICATE KEY UPDATE category_id = new.category_id;
