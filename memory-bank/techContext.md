# Tech Context

- Java 21 (Eclipse Adoptium), Maven 3.9.16 (wrapper ile)
- Spring Boot 4.1.1
- user-service: Spring Web MVC (port 8081), Spring Data JPA, Flyway (MySQL), Spring Security, Validation,
  Lombok. Spring Modulith tamamen kaldırıldı (bağımlılıklar + kök POM'daki BOM); olay yayını için
  kendi `outbox` tablosu kullanılacak.
- user-service şeması (V1__create_user_tables.sql): `users`, `addresses`, `refresh_tokens`, `outbox`.
  UUID = BINARY(16) (uygulama üretir), zaman = DATETIME(6) UTC, collation utf8mb4_0900_ai_ci
  (e-posta UNIQUE büyük/küçük harf duyarsız). Kullanıcı başına tek default adres:
  `addresses.default_owner` VIRTUAL generated kolon + UNIQUE (STORED olamaz: MySQL, STORED generated
  kolonun kaynak kolonundaki FK'da ON DELETE CASCADE'e izin vermiyor — ERROR 1215).
- user-service yapılandırması: `src/main/resources/application.yml`; DB bilgileri `spring.config.import`
  ile kökteki `.env`'den okunur (`USER_DB_USER`, `USER_DB_PASSWORD`, opsiyonel `USER_DB_HOST`/`USER_DB_PORT`).
  `ddl-auto: validate`, şema Flyway'de (`classpath:db/migration`).
- Çalıştırma: kökten `.\mvnw.cmd -pl user-service spring-boot:run` (MySQL container healthy olmalı).
- Windows PowerShell 5.1: `Invoke-WebRequest -SkipHttpErrorCheck` yok; 4xx için try/catch kullan.
- Veritabanı: MySQL 8.4, kökteki `docker-compose.yml` ile (`container_name: kitapsepeti-mysql`, port 3306,
  volume `kitapsepeti_mysql_data`). Şifreler kökteki `.env`'de (git'e girmez), şablon `.env.example`.
- DB mimarisi: tek MySQL sunucusu, her servise ayrı şema + ayrı kullanıcı; servis kullanıcısı sadece kendi
  şemasına yetkili, root ile bağlanılmaz. `user-service` → şema `user_db`, kullanıcı `user_svc`.
  Tabloları Flyway oluşturur; SQL init script'i / `infra/` klasörü kullanılmıyor.
- Mesajlaşma: RabbitMQ 4 (`rabbitmq:4-management`, container `kitapsepeti-rabbitmq`, AMQP 5672,
  Management UI/API yalnızca `127.0.0.1:15672`). Kullanıcı `.env`'deki `RABBITMQ_USER`/`RABBITMQ_PASSWORD`.
  user-service olayları `kitapsepeti.events` (topic) exchange'ine outbox worker ile yayınlar; sözleşmeler `docs/events/`.
- API dokümanı: springdoc-openapi 3.1.1 (Boot 4 hattı). Çalışırken `http://localhost:8081/swagger-ui.html` ve
  `/v3/api-docs` (OpenAPI 3.1.0); `SPRINGDOC_ENABLED=false` ile ikisi de kapanır. HTTP sözleşmeleri `docs/api/`
  (`user-service.openapi.json` = `/v3/api-docs` çıktısı; node ile `JSON.stringify(d, null, 2)` + BOM'suz yazıldı).
- OS: Windows, shell: PowerShell

## Komutlar (kök dizinden)
- Tüm servisleri build: `.\mvnw.cmd clean package`
- Tek servis: `.\mvnw.cmd -pl user-service -am clean package`
- Testler: `.\mvnw.cmd -pl user-service test` — Docker açık olmalı (Testcontainers kendi MySQL'ini açar,
  `user_db`'ye dokunmaz).
