#!/bin/sh
# payment-service şeması ve kullanıcısı (20-cart-db.sh ile aynı kalıp).
# Entrypoint bu klasörü YALNIZCA boş veri volume'unda çalıştırır. Mevcut volume için elle:
#   docker compose exec mysql sh /docker-entrypoint-initdb.d/30-payment-db.sh
# Parola SQL'e tek tırnak içinde gömülür: PAYMENT_DB_PASSWORD yalnızca harf ve rakam içermeli.

# Entrypoint çalıştırılabilir olmayan .sh dosyalarını kendi kabuğunda source eder;
# alt kabuk, set -eu'nun entrypoint'in geri kalanına sızmasını önler.
(
set -eu

: "${PAYMENT_DB_USER:?PAYMENT_DB_USER tanımlı değil}"
: "${PAYMENT_DB_PASSWORD:?PAYMENT_DB_PASSWORD tanımlı değil}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD tanımlı değil}"

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<EOSQL
CREATE DATABASE IF NOT EXISTS payment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${PAYMENT_DB_USER}'@'%' IDENTIFIED BY '${PAYMENT_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON payment_db.* TO '${PAYMENT_DB_USER}'@'%';
EOSQL

echo "payment_db ve ${PAYMENT_DB_USER} hazır."
)
