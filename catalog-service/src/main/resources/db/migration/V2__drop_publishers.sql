-- Özellik 8: yayınevi kaldırılır. Önce FK/sütun, sonra publishers tablosu.
ALTER TABLE books DROP FOREIGN KEY fk_books_publisher;
ALTER TABLE books DROP COLUMN publisher_id;
DROP TABLE publishers;
