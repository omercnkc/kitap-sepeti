# System Patterns

## Maven yapısı
- Kök `pom.xml` = `com.kitapsepeti:kitap-sepeti-parent` (packaging `pom`).
  - Parent'ı: `spring-boot-starter-parent` 4.1.1.
  - Görevleri: `<modules>` ile tüm servisleri birlikte build etmek; Java sürümü,
    BOM'lar (şu an boş), ortak bağımlılıklar (Lombok) ve
    `maven-compiler-plugin` Lombok annotation processor ayarını dağıtmak.
- Servis POM'ları parent olarak kök POM'u gösterir; groupId/version/java.version
  ve versiyon numaraları servis POM'unda YAZILMAZ.
- `spring-boot-maven-plugin` her çalıştırılabilir serviste ayrı tanımlanır
  (parent'ta değil; ileride eklenecek ortak kütüphane modülleri repackage edilmesin diye).
- Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`) kök dizindedir.

## Entity kuralları (user-service)
- `@Id UUID` + `@UuidGenerator(style = VERSION_7)` (BINARY(16)); zamanlar `Instant` +
  `@CreationTimestamp`/`@UpdateTimestamp`.
- `users.status` küçük harf → `UserStatusConverter` (`@Enumerated` değil); `role` → `@Enumerated(STRING)`.
- Default değerler Java'da da verilir (status, role, country, isDefault); DB default'una güvenilmez.
- `@ManyToOne(LAZY, optional=false)`; ters yönlü `@OneToMany` yok. `addresses.default_owner` entity'de yok.
- Lombok: `@Getter`, sadece değişebilir alanlarda `@Setter`, `@NoArgsConstructor(PROTECTED)` +
  zorunlu alanlar için public constructor. `@Data`/`@ToString`/`@EqualsAndHashCode` yok.
  Not: `boolean isDefault` → Lombok `isDefault()` / `setDefault(boolean)` üretir.

- equals/hashCode override edilmez (Object kimliği); Set'teki entity'lerin hashCode'u persist öncesi/sonrası sabit kalır.
  Koleksiyon ilişkileri (catalog Book) tek yönlü, LAZY, `Set` + alan tanımında `new HashSet<>()`; okuma için `@EntityGraph`.
- Aynı kurallar catalog-service entity'lerinde de geçerli (`@Version Long` yalnızca Book'ta).

## Güvenlik (user-service)
- `config/SecurityConfig`: BCrypt (strength 10) `PasswordEncoder`; stateless `SecurityFilterChain`,
  csrf/httpBasic/formLogin/logout kapalı, deny-by-default. permitAll: POST /api/auth/{register,login,refresh},
  GET /.well-known/jwks.json, /v3/api-docs/**, /swagger-ui/**, /swagger-ui.html, /error.
  Kimliksiz istek → `HttpStatusEntryPoint(401)` (Basic başlığı yok).
- AuthenticationManager / UserDetailsService YOK; giriş AuthService'te `passwordEncoder.matches()` ile elle.
- `UserDetailsServiceAutoConfiguration` exclude (Boot 4 paketi:
  `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`), `@SpringBootApplication(exclude=...)`.
- Boot 4 MockMvc: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
  (`spring-boot-starter-webmvc-test` içinde).

## Güvenlik (catalog-service = yalnızca Resource Server)
- Catalog token ÜRETMEZ; src/main'de JwtEncoder/özel anahtar yok. `config/JwtDecoderConfig` JWKS'ten (user-service
  `/.well-known/jwks.json`, `USER_SERVICE_JWKS_URI`) RS256 + `JwtValidators.createDefaultWithIssuer(app.jwt.issuer)`.
  Anahtar ilk doğrulamada çekilir, önbelleğe alınır; servis açılışı user-service'e bağımlı değil.
- Rol: JWT `role` claim → `ROLE_<ROLE>`; principal = `sub` (kullanıcı UUID'si).
- Token doğrulama hatası ayrımı: geçersiz token → 401 UNAUTHORIZED (entry point); doğrulama altyapısı (JWKS) erişilemez →
  503 AUTHENTICATION_UNAVAILABLE (`ProblemDetailAuthenticationFailureHandler`, `withObjectPostProcessor` ile filtreye bağlı).
  JWKS kesintisine dayanıklı önbellek yok (Gateway fazı backlog'u).
- URL kuralları sırası önemli: public GET (books, categories) → /error → /api/admin/** ADMIN → /internal/** denyAll →
  authenticated. Public GET'lerde BearerTokenResolver Authorization'ı okumaz (aynı PathPattern listesi, `PUBLIC_GET_PATHS`).
- Servisler arası kimlik (KALIP): `/internal/**` AYRI `SecurityFilterChain` (`config/InternalSecurityConfig`, `@Order(1)`,
  `securityMatcher("/internal/**")`, stateless, csrf/basic/form/logout/anonymous kapalı, resource server YOK → kullanıcı JWT'si
  ADMIN dahil 401). `security/internal/InternalApiKeyAuthenticationFilter` (BEAN DEĞİL — bean olursa Boot onu tüm isteklere
  servlet filtresi olarak da kaydeder) `X-Internal-Api-Key`'in UTF-8 SHA-256'sını tüm istemci özetleriyle
  `MessageDigest.isEqual` ile karşılaştırır (hep tüm liste gezilir); eşleşme → principal = istemci adı, `ROLE_INTERNAL_SERVICE`,
  INFO `Internal request METHOD path client=<ad>`. Yok/yanlış → `InternalApiKeyAuthenticationEntryPoint`: 401 UNAUTHORIZED,
  `WWW-Authenticate: ApiKey realm="internal"`, tek WARN satırı (method + path). Anahtar/özet ASLA loglanmaz.
  Yapılandırma `app.internal-auth.clients[{name, key-sha256}]` (yalnızca özet; boş = istemci kapalı). Format doğrulaması
  bağlamada DEĞİL `InternalApiKeys` ctor'unda (fail-fast `IllegalStateException`, mesajda özellik + istemci adı, değer YOK —
  Boot'un bind failure analyzer'ı değeri yansıtabilirdi; yanlışlıkla ham anahtar girilirse sızmasın). `Client.toString` özeti maskeler.
  Ana zincirdeki `/internal/** denyAll` ek savunma olarak kalır. Yeni istemci = listeye yeni `{name, key-sha256}` + `.env` özeti.

## Stok rezervasyonu (catalog-service internal)
- Dış servis (`StockReservationService`) BİLEREK transactional değil; iç `StockReservationTransactions` `@Transactional(READ_COMMITTED)`.
  Aynı siparişin eşzamanlı isteği `uk_stock_reservations_order_book` ile düşerse (veya stok yarışında StockUnavailable) iç transaction
  tamamen geri alınır, dış katman yeni transaction'da mevcut rezervasyonu okuyup idempotent yanıt verir (user-service refresh kalıbı).
- Stok yalnızca tek koşullu `@Modifying` UPDATE'lerle (`BookRepository.reserve/commitReserved/releaseReserved`); önce-oku-sonra-yaz YOK,
  versiyon artmaz. Ya hep ya hiç: tüm kalemler denenir, hatalılar toplanır, sonra exception → rollback. Hata nedeni ayrı okumayla
  (yok/yayında değil → BOOK_NOT_AVAILABLE öncelikli; diğerleri INSUFFICIENT_STOCK), `bookIds` yalnızca seçilen koddaki kitaplar.
- Kilit sırası: kitaplar HER ZAMAN `LOCK_ORDER` (UUID işaretsiz msb→lsb = DB BINARY(16) sırası; `UUID.compareTo` işaretli, FARKLI) ile.
  Rezervde önce kitap UPDATE'leri sonra rezervasyon INSERT'leri (FK kontrolünün S kilidi zaten tutulan X kilidiyle çakışmaz);
  onay/iptalde önce rezervasyon satırları `FOR UPDATE`, sonra kitaplar. READ COMMITTED → gap lock yok, FOR UPDATE yalnızca bulunan satırları kilitler.
- Onay/iptal: satırlar düz record'a kopyalanır (bulk UPDATE'lerin `clearAutomatically`'si entity'leri koparır), kitap UPDATE'i 1 satır
  değilse `IllegalStateException` (500 + ERROR), durum geçişi toplu JPQL `transition(orderId, from, to)` (updated_at DB ON UPDATE ile).
- `expiresAt = clock.instant() + app.stock.reservation-ttl` (`StockProperties`, 15m), MICROS'a kesilir (DATETIME(6) → 201 ve tekrar 200 aynı gövde).
- Olay: yayındaki kitabın `available > 0` değeri değiştiyse `BookUpserted` (rezerv: son kopyalar; iptal: 0 → >0). Onay olay üretmez.
- **DEĞİŞMEZ:** her kitapta `reserved_quantity = SUM(quantity WHERE status = 'held')`. Rezervi değiştiren her yol (reserve/commit/
  release/süre dolumu, seed) bunu korumalı. Test yardımcısı `support/StockInvariant` (`VIOLATIONS_SQL` yalnızca tutarsız kitapları
  döndürür; `assertHolds(jdbc)`); eşzamanlılık/süre dolumu testlerinin sonunda ve DevSeedMigrationTest'te çağrılır.
- Süre dolumu (`service/ReservationExpiryJob`, `app.stock.expiry.{enabled,interval,batch-size}` = true/30s/100; test profilinde kapalı,
  testler `releaseExpired()`'ı doğrudan çağırır): aday siparişler kilitsiz native sorgu (`status='held' AND expires_at < now`,
  GROUP BY order_id, en eski MIN(expires_at) önce, LIMIT; `ix_stock_reservations_status_expires` range). Her sipariş ayrı transaction:
  `lockByOrderIdAndStatusSkipLocked` (JPQL + PESSIMISTIC_WRITE + `jakarta.persistence.lock.timeout = -2` → `FOR UPDATE SKIP LOCKED`);
  boşsa ya da kilitlenen sayı `countByOrderIdAndStatus(HELD)`'den azsa (kısmi kilit = eşzamanlı onay/iptal) sipariş atlanır; kilitten
  sonra süre yeniden kontrol edilir. Bırakma iptal ucuyla ORTAK `StockReservationTransactions.releaseHeld` (aynı kilit sırası, koşullu
  UPDATE, olay kuralı). Hata: sipariş hatası → o sipariş geri alınır, tek satır WARN (orderId + sınıf adı), tur sürer;
  `DataAccessResourceFailureException`/`TransactionException` (veya aday okuması hatası) → tek satır WARN, tur biter. ≥1 sipariş
  bırakıldıysa tur sonunda tek INFO. Yeni olay türü YOK; commit süreye bakmaz (held ise onaylanır).
- `SchedulingConfig` `AnyNestedCondition`: outbox VEYA süre dolumu açıksa `@EnableScheduling`.
- Hata altyapısı user-service ile aynı yapıda KOPYA (ortak modül yok; Cart servisi gelince çıkarılacak). İki servisteki
  `ProblemDetails`/`GlobalExceptionHandler`/security handler değişiklikleri elle senkron tutulmalı.
- DB kısıt → ErrorCode eşlemesi tek yerde: `exception/DbConstraints.classify` (Hibernate kind + normalize ad + MySQL hata kodu).
  Yeni UNIQUE kısıt özel kod isterse `UNIQUE_CODES`'a eklenir; aksi halde CONFLICT.

## Okuma API'si (catalog-service)
- Katman: controller → `service/*QueryService` (`@Transactional(readOnly = true)`) → repository; elle mapper (`mapper/`), entity dönmez.
- Liste filtreleri `repository/BookSpecifications` (Spring Data 4 `Specification.allOf`). To-many filtre = EXISTS alt sorgusu
  (`subquery.correlate(root).join(...)`), JOIN değil. Sayfalı sorguda to-many JOIN FETCH YOK: to-one `@EntityGraph`,
  koleksiyonlar `default_batch_fetch_size` ile. Sorgu sayısı testte Hibernate Statistics ile sabitlenir (test profili).
- Query parametreleri record + `@ModelAttribute`: wrapper tip + compact ctor default'u (primitive eksikse 0 olur).
  Çapraz alan kuralı sınıf seviyesi constraint, ihlal ilgili alana yazılır. Enum parametreleri büyük/küçük harf duyarsız Converter.
- İsim sıralaması Türkçe Collator (`mapper/NameOrder.TURKISH`); DB `utf8mb4_0900_ai_ci` ORDER BY ile uyumlu.
- Kategori ağacı bellekte (`CategoryForest`); tek sorgu.

## Admin yazma API'si (catalog-service)
- `controller/admin/` + `service/*AdminService` (`@Transactional`); POST 201 + Location `/api/admin/<kaynak>/{id}`, DELETE 204.
- Request record'larında serbest metin `name` compact ctor'da `strip()`; doğrulama kırpılmış değere. PATCH: null = değiştirme
  (`@NullOrNotBlank`). Slug: `@Slug(max = kolon)`; verilmezse `SlugGenerator`; ad değişince slug DEĞİŞMEZ.
- Servis katmanındaki alan hatası (olmayan referans, üretilemeyen değer) = `InvalidFieldException(field, message)` → 400 VALIDATION_FAILED.
- Benzersizlik: ön kontrol (`existsBy...`) + DB kısıtı son savunma (DbConstraints aynı kodu verir). Otomatik "-2" eki YOK.
- Silme/güncellemede `flush()` servis içinde: kısıt ihlali transaction açıkken oluşur, handler eşler. "Kullanımda" kontrolü DB FK'sına bırakılır.
- Ağaç değişikliği (kategori taşıma) tüm kategori satırlarını `FOR UPDATE` ile okuyup bellekte (`CategoryForest`) kontrol eder.
- Kitap: stok/rezerv entity'den ASLA yazılmaz (`updatable = false`, setter yok); yalnızca koşullu `@Modifying` UPDATE
  (`flushAutomatically` + `clearAutomatically`). Sayaç tipi, eşzamanlı değişen kolonlar için aynı kalıp.
- Optimistic lock istemciden: PATCH gövdesinde `version` zorunlu, eşit değilse 409 ve yazma yok; ek olarak `@Version`.
  Değiştiren kitap işlemleri satırı `PESSIMISTIC_WRITE` ile okur (olay içeriği eşzamanlı stok değişikliğiyle tutarlı olsun).
- Olay yazımı: değişiklikler uygulanır → `outboxService.append` → `flush()`. Flush hatası (UNIQUE vb.) olayı da geri alır.
  Yalnızca yayındaki kitap olay üretir; durum geçişi olmayan tekrar (publish/archive) değişiklik ve olay üretmez.
- Benzersizlik ISBN'de yalnızca DB kısıtıyla (ön kontrol yok); slug'larda ön kontrol + DB.

## Örnek veri (local profil)
- `db/seed/R__*.sql` repeatable migration, yalnızca `application-local.yml` `spring.flyway.locations`'a ekler; varsayılan/test profili yüklemez.
- Idempotent: sabit UUID + `INSERT ... AS new ON DUPLICATE KEY UPDATE col = new.col` (`VALUES()` deprecated).
- Varsayılan profilde `ignore-migration-patterns: "*:future,repeatable:missing"` şart (seed'li DB'de validate düşmesin).
- Seed'de rezervli kitap varsa toplamı tutan `held` `stock_reservations` satırları da seed'de olmalı (sabit id/orderId,
  `expires_at` 2099 → süre dolumu bırakmaz); yeniden çalıştırma kitap ve rezervasyonu birlikte seed durumuna döndürür.

## Test
- catalog API testleri `ApiTestSupport`'u extend eder: gerçek HTTP JWKS (JDK HttpServer) + `TestJwt` ile imzalı token.
  `support/MutableClock` (`@Primary`, override değil) varsayılan sistem saati; test `fixAt`/`advance` ile sabitler, her test başında
  sıfırlanır → ek context açılmaz. Internal uçlar `support/InternalTestKeys` (test-only ham anahtar; özeti application-test.yml'de).
  Eşzamanlılık testleri: ExecutorService + iki CountDownLatch (hazır/başla), MockMvc gerçek thread'lerden.
  JWKS çağrı sayısını ölçen test ayrı context ister (kendi sunucusu).
- Repository testleri: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` +
  `@Import(TestcontainersConfiguration.class)`; Flyway test container'ında çalışır.
- Boot 4 paketleri: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`,
  `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`; Testcontainers 2.x:
  `org.testcontainers.mysql.MySQLContainer`, artifact `testcontainers-mysql`.

## Olaylar (transactional outbox)
- Yazma: `OutboxService.append` (`Propagation.MANDATORY`) iş verisiyle aynı transaction'da `outbox`'a yazar.
- Yayın: `OutboxRelay` turu tek transaction; `FOR UPDATE SKIP LOCKED` batch, sırayla yayın + confirm bekleme,
  ilk hatada dur (başarılılar commit). At-least-once; consumer `messageId` ile idempotent olmalı.
- Üretici kuyruk tanımlamaz; consumer kendi kuyruğunu declare/bind eder. Yeni olay tipi = `EventRoutingKeys`'e
  routing key + `docs/events/<olay>.md`.
- Worker testlerde varsayılan kapalı (`app.outbox.enabled: false`); açan test ayrı context kurar.
- Tüm servisler aynı `kitapsepeti.events` exchange'ine yayınlar; her serviste tanım BİREBİR aynı olmalı
  (`new TopicExchange(name, true, false)`, argümansız). Farklı durable/autoDelete/argüman → broker PRECONDITION_FAILED
  ile kanalı kapatır. Ortak modül yok: worker sınıfları servis başına kopya (user-service ↔ catalog-service).
- catalog testlerinde RabbitMQ konteyneri ayrı `RabbitTestcontainersConfiguration`; tam context açan testler import eder,
  dilim testleri (`@DataJpaTest`, `@JdbcTest`) etmez.
- JPQL/SQL bulk UPDATE `@UpdateTimestamp`/`@Version`'ı atlar; `updated_at` yine de kolonun `ON UPDATE CURRENT_TIMESTAMP(6)`
  tanımıyla DB saatine güncellenir (değer değiştiren her UPDATE'te).
- Adlandırma: `aggregate_type` küçük harf varlık adı (`user`, `book`); `event_type` PascalCase geçmiş zaman/olgu (`UserRegistered`,
  `BookUpserted`, `BookRemoved`); routing key `<varlık>.<olay>` küçük harf (`user.registered`, `book.upserted`, `book.removed`).
  Payload record'u `service/event/<Olay>Event` (`TYPE`, `VERSION`, ilk alan `eventVersion`). Payload'a iç sayaç/versiyon/durum konmaz.

## API dokümanı (OpenAPI)
- Yeni uç: `@Tag` (sınıf), `@Operation(operationId, summary)`, başarı kodu `@ApiResponse` ile; uca özel hata
  (`@ApiResponse` + `application/problem+json` + `Problem` ref). 401/400/404/500'ü `OpenApiConfig` ekler, elle yazma.
- Public uç = sınıf/metotta boş `@SecurityRequirements` (+ SecurityConfig permitAll). Yeni `ErrorCode` enum'a
  otomatik girer. DTO alanlarına `@Schema(description, example)`; parola/sır `accessMode = WRITE_ONLY`.
- Uç/DTO değişince `docs/api/<servis>.openapi.json` yeniden üretilir: `OpenApiContractTest` fark varsa kırılır;
  `-Dopenapi.contract.update=true` ile dosyayı yeniden yazar.

## Container (servis başına)
- `<servis>/Dockerfile`, build context = repo kökü (kök pom + mvnw gerekir). Çok aşamalı: pom'lar → `go-offline`
  (cache mount) → src → `package -DskipTests` → layered extract → JRE runtime, sabit UID/GID 10001 non-root.
- Sırlar image'a girmez (`.dockerignore`); compose'ta env yalnızca tek tek, anahtar dosyaları compose `secrets`.
- Readiness'a yalnızca isteği karşılamak için şart olan bağımlılık (DB) girer; mesaj broker'ı girmez (outbox tamponlar).

## Yeni servis eklerken (KULLANICI KURALI)
Kullanıcı, her yeni serviste kök `pom.xml`'in kontrol edilip gerekiyorsa
güncellenmesini ve her değişikliğin NE ve NEDEN olduğunun açıklanmasını istiyor.
Kök POM'un başındaki "YENİ SERVİS EKLERKEN KONTROL LİSTESİ" takip edilir:
1. `<modules>`'a ekle.
2. Servis POM'unun `<parent>`'ını kök POM yap, miras alınanları sil.
3. Yeni kütüphane ailesi varsa versiyonu `<properties>`'e, BOM'u `<dependencyManagement>`'a.
4. Tüm servislerde ortak olan bağımlılığı kök `<dependencies>`'e taşı.
