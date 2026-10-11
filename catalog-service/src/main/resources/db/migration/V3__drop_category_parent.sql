-- Alt kategori (ağaç) kaldırıldı: düz kategori listesi.
UPDATE categories SET parent_id = NULL WHERE parent_id IS NOT NULL;
ALTER TABLE categories DROP FOREIGN KEY fk_categories_parent;
ALTER TABLE categories DROP COLUMN parent_id;
