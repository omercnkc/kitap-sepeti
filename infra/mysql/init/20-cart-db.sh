#!/bin/sh
# cart-service şeması ve kullanıcısı (10-catalog-db.sh ile aynı kalıp).
# Entrypoint bu klasörü YALNIZCA boş veri volume'unda çalıştırır. Mevcut volume için elle:
#   docker compose exec mysql sh /docker-entrypoint-initdb.d/20-cart-db.sh
# Parola SQL'e tek tırnak içinde gömülür: CART_DB_PASSWORD yalnızca harf ve rakam içermeli.

# Entrypoint çalıştırılabilir olmayan .sh dosyalarını kendi kabuğunda source eder;
# alt kabuk, set -eu'nun entrypoint'in geri kalanına sızmasını önler.
(
set -eu

: "${CART_DB_USER:?CART_DB_USER tanımlı değil}"
: "${CART_DB_PASSWORD:?CART_DB_PASSWORD tanımlı değil}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD tanımlı değil}"

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<EOSQL
CREATE DATABASE IF NOT EXISTS cart_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${CART_DB_USER}'@'%' IDENTIFIED BY '${CART_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON cart_db.* TO '${CART_DB_USER}'@'%';
EOSQL

echo "cart_db ve ${CART_DB_USER} hazır."
)
