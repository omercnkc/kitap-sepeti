#!/bin/sh
# catalog-service şeması ve kullanıcısı. MYSQL_DATABASE/MYSQL_USER yalnızca tek şema (user_db) kurar;
# ek servis şemaları bu klasördeki script'lerle eklenir.
# Entrypoint bu klasörü YALNIZCA boş veri volume'unda çalıştırır. Mevcut volume için elle:
#   docker compose exec mysql sh /docker-entrypoint-initdb.d/10-catalog-db.sh
# Parola SQL'e tek tırnak içinde gömülür: CATALOG_DB_PASSWORD yalnızca harf ve rakam içermeli.

# Entrypoint çalıştırılabilir olmayan .sh dosyalarını kendi kabuğunda source eder;
# alt kabuk, set -eu'nun entrypoint'in geri kalanına sızmasını önler.
(
set -eu

: "${CATALOG_DB_USER:?CATALOG_DB_USER tanımlı değil}"
: "${CATALOG_DB_PASSWORD:?CATALOG_DB_PASSWORD tanımlı değil}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD tanımlı değil}"

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<EOSQL
CREATE DATABASE IF NOT EXISTS catalog_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${CATALOG_DB_USER}'@'%' IDENTIFIED BY '${CATALOG_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON catalog_db.* TO '${CATALOG_DB_USER}'@'%';
EOSQL

echo "catalog_db ve ${CATALOG_DB_USER} hazır."
)
