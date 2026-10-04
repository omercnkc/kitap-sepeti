#!/bin/sh
# order-service şeması ve kullanıcısı (30-payment-db.sh ile aynı kalıp).
# Entrypoint bu klasörü YALNIZCA boş veri volume'unda çalıştırır. Mevcut volume için elle:
#   docker compose exec mysql sh /docker-entrypoint-initdb.d/40-order-db.sh
# Parola SQL'e tek tırnak içinde gömülür: ORDER_DB_PASSWORD yalnızca harf ve rakam içermeli.

# Entrypoint çalıştırılabilir olmayan .sh dosyalarını kendi kabuğunda source eder;
# alt kabuk, set -eu'nun entrypoint'in geri kalanına sızmasını önler.
(
set -eu

: "${ORDER_DB_USER:?ORDER_DB_USER tanımlı değil}"
: "${ORDER_DB_PASSWORD:?ORDER_DB_PASSWORD tanımlı değil}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD tanımlı değil}"

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<EOSQL
CREATE DATABASE IF NOT EXISTS order_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${ORDER_DB_USER}'@'%' IDENTIFIED BY '${ORDER_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON order_db.* TO '${ORDER_DB_USER}'@'%';
EOSQL

echo "order_db ve ${ORDER_DB_USER} hazır."
)
