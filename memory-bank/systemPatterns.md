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
  (parent'ta değil; `common` kütüphane modülü repackage edilmesin diye).
- Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`) kök dizindedir.

## common modülü (`common/`, artifactId `kitap-sepeti-common`)
- Düz kütüphane jar'ı (spring-boot-maven-plugin yok). Versiyonu kök `<dependencyManagement>`'ta `${project.version}`;
  servis POM'unda versiyonsuz. Spring bağımlılıkları `<optional>true</optional>` (webmvc, validation, data-jpa,
  security-oauth2-resource-server) → servise starter taşımaz; servis zaten kendi starter'larını ekler. Yeni dış bağımlılık YOK.
- Auto-configuration YOK, component scan'e girmez (paket `com.kitapsepeti.common`, servislerin scan kökü dışında).
  Bean'ler serviste açıkça: `@Import(ProblemDetailSecurityHandlers.class)` (401 entry point + 403 handler) ve gerekiyorsa `@Bean`.
- `common.error`:
  - `ErrorCode` arayüzü (`name()`, `status()`, `logLevel()`, `defaultDetail()`); `CommonErrorCode` enum'u (12 genel kod:
    VALIDATION_FAILED, MALFORMED_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, RESOURCE_NOT_FOUND, METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE, CONFLICT, AUTHENTICATION_UNAVAILABLE, INTERNAL_ERROR).
  - Servise özel kodlar servisin enum'unda (`UserErrorCode`, `CatalogErrorCode`) + `public static final List<ErrorCode> API_CODES`:
    OpenAPI `Problem.code` enum'unun SIRASI buradan (sözleşme dosyasıyla birebir). Yeni kod = enum'a + API_CODES'ta istenen yere;
    `OpenApiDocsTest` liste = API_CODES ve küme = servis kodları ∪ ilgili ortak kodlar kontrolü yapar.
  - `ApiException` (abstract, ErrorCode alır), `ResourceNotFoundException`, `ProblemDetails` (create/apply/log), `DbConstraints`
    (genel kısım: `find`, `nameOf`, `normalize`, `isViolated`, `isRowReferenced` = FK + MySQL 1451). Kısıt adı → kod eşlemesi serviste
    (catalog `exception/DbConstraintCodes`).
  - `ProblemDetailExceptionHandler` (abstract, `ResponseEntityExceptionHandler`): tüm ortak handler'lar. Kanca: `addProperties(problem, ex)`
    (ApiException'a ek alan, catalog: `errors`, `bookIds`), `classify(DataIntegrityViolationException)` (varsayılan CONFLICT +
    `constraint=<ad>`). Logger `ClassUtils.getUserClass(getClass())` → servisteki `exception.GlobalExceptionHandler`
    (`@RestControllerAdvice` alt sınıf) adıyla loglar; log kategorisi taşımadan önceki ile aynı.
- `common.security`: `BearerChallenge`, `ProblemDetailResponses`, `ProblemDetailAuthenticationEntryPoint` /
  `ProblemDetailAccessDeniedHandler` (bean DEĞİL; `ProblemDetailSecurityHandlers` kaydeder), `ProblemDetailAuthenticationFailureHandler`
  (JWKS kesintisi → 503; `postProcessorFor(handler)` BearerTokenAuthenticationFilter'a bağlar), `JwtRoleConverters.roleClaim()`,
  `BearerTokenResolvers` (`ignoringGet(paths)`, `ignoringUriPrefix(prefix)`, `ignoring(matcher)`), `JwkSetJwtDecoders.rs256(uri, issuer)`
  (RS256 + iss/exp, RestTemplate 2 s / 3 s).
- `common.security.internal`: `InternalAuthProperties`, `InternalApiKeys`, `InternalApiKeyAuthenticationFilter` (BEAN DEĞİL),
  `InternalApiKeyAuthenticationEntryPoint`. Zincir (`InternalSecurityConfig`) serviste kalır.
- Bu paketlerdeki sınıfların logger'ı artık `com.kitapsepeti.common.security(.internal).*` (mesaj metni aynı).
- common'a GİRMEYEN: güvenlik kuralları/yollar (SecurityConfig), user-service'in kendi anahtarlı JwtDecoder'ı (RsaKeyConfig),
  catalog JwtProperties, InternalSecurityConfig, outbox (ayrı adım; hâlâ servis başına kopya).

### Yeni servis common'ı nasıl kullanır (tarif)
1. Servis POM'una `com.kitapsepeti:kitap-sepeti-common` (versiyonsuz) ekle.
2. `exception/<Servis>ErrorCode implements ErrorCode` (yalnızca servise özel kodlar) + `API_CODES` listesi (ortak + özel, doküman sırası:
   ortak genel kodlar → servis kodları → AUTHENTICATION_UNAVAILABLE → INTERNAL_ERROR). Küme kuralı: JWKS ile doğrulayan servis
   (catalog, cart) = servis kodları ∪ TÜM 12 ortak kod; user-service = AUTHENTICATION_UNAVAILABLE hariç. OpenAPI yoksa kontrol birim
   testinde (cart `CartErrorCodeTest`: tekrarsız + küme eşitliği → enum'a eklenip listeye yazılmayan kod testi kırar).
3. `exception/GlobalExceptionHandler extends ProblemDetailExceptionHandler` + `@RestControllerAdvice`; gerekirse `addProperties`/`classify` override.
   `classify` için `exception/DbConstraintCodes` (catalog kalıbı; `Violation(code, constraint, kind)` + `logNote()`).
   Not: catch-all (ISE/IAE dahil) 500 INTERNAL_ERROR + GENEL detail ("An unexpected error occurred."), mesaj yanıtta yok, logda ERROR + stack.
4. SecurityConfig (resource server): `@Import(ProblemDetailSecurityHandlers.class)`; entry point/access denied handler'ı enjekte et ve
   HEM `oauth2ResourceServer(...)` HEM `exceptionHandling(...)` içine ver; `JwtRoleConverters.roleClaim()`; stateless + csrf/basic/form/logout kapalı;
   kurallar: health GET permitAll → `/error` permitAll (YOKSA controller hataları /error yönlendirmesinde 401 olur) → servis kuralları →
   `anyRequest().authenticated()`. `BearerTokenResolvers.ignoringGet(...)` YALNIZCA public GET uçları varsa (cart'ta yok → özelleştirme yok;
   o zaman açık uca gönderilen bozuk token da 401 olur).
   JwtDecoder: servis içinde `security/JwtProperties` (`@ConfigurationProperties("app.jwt")`, `@NotBlank issuer`; common'da DEĞİL) +
   `config/JwtDecoderConfig` (`@EnableConfigurationProperties(JwtProperties)`, `JwkSetJwtDecoders.rs256(OAuth2ResourceServerProperties
   .getJwt().getJwkSetUri(), issuer)`). application.yml: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri:
   ${USER_SERVICE_JWKS_URI:http://localhost:8081/.well-known/jwks.json}` + `app.jwt.issuer: kitapsepeti-user-service`.
   Decoder sarmalanırsa ek kontrol `BadJwtException` fırlatmalı (diğer JwtException'lar 401 değil 503 olur).
   503 için `@Bean ProblemDetailAuthenticationFailureHandler` + `withObjectPostProcessor(ProblemDetailAuthenticationFailureHandler.postProcessorFor(h))`.
   Test altyapısı (servis başına kopya): `support/TestJwt` (anahtar test JVM'inde üretilir — cart; catalog/user pem dosyası + .gitignore
   istisnası kullanır), `support/JwksServer`, `ApiTestSupport` (statik JWKS + `@DynamicPropertySource`), `support/MutableClock(Configuration)`,
   ayrı context'li `security/JwksOutageTest`.
5. Internal uç sunuyorsa: `@EnableConfigurationProperties(InternalAuthProperties.class)`, `new InternalApiKeys(props)`, filtreyi zincirde
   `new` ile ekle (bean yapma), entry point'i `exceptionHandling`'e ver.
6. `OpenApiConfig`: `code` enum'u `<Servis>ErrorCode.API_CODES.stream().map(ErrorCode::name).toList()`.
7. Dockerfile: mevcut bir servisinkini kopyala, yalnızca servis adını ve `COPY --parents common/src <servis>/src ./` satırını uyarla.

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
  ADMIN dahil 401). `common.security.internal.InternalApiKeyAuthenticationFilter` (BEAN DEĞİL — bean olursa Boot onu tüm isteklere
  servlet filtresi olarak da kaydeder) `X-Internal-Api-Key`'in UTF-8 SHA-256'sını tüm istemci özetleriyle
  `MessageDigest.isEqual` ile karşılaştırır (hep tüm liste gezilir); eşleşme → principal = istemci adı, `ROLE_INTERNAL_SERVICE`,
  INFO `Internal request METHOD path client=<ad>`. Yok/yanlış → `InternalApiKeyAuthenticationEntryPoint`: 401 UNAUTHORIZED,
  `WWW-Authenticate: ApiKey realm="internal"`, tek WARN satırı (method + path). Anahtar/özet ASLA loglanmaz.
  Yapılandırma `app.internal-auth.clients[{name, key-sha256}]` (yalnızca özet; boş = istemci kapalı). Format doğrulaması
  bağlamada DEĞİL `InternalApiKeys` ctor'unda (fail-fast `IllegalStateException`, mesajda özellik + istemci adı, değer YOK —
  Boot'un bind failure analyzer'ı değeri yansıtabilirdi; yanlışlıkla ham anahtar girilirse sızmasın). `Client.toString` özeti maskeler.
  Ana zincirdeki `/internal/** denyAll` ek savunma olarak kalır. Yeni istemci = listeye yeni `{name, key-sha256}` + `.env` özeti.

## Güvenlik (cart-service = yalnızca Resource Server)
- catalog ile aynı yapı (tarif adım 4); farklar: public GET yok → BearerTokenResolver özelleştirmesi yok; `/api/cart/**` authenticated
  (USER ve ADMIN, rol şartı yok); anyRequest authenticated (kimliksiz 401, kimlikli olmayan yol / kapalı actuator ucu 404 NOT_FOUND).
- Kullanıcı kimliği YALNIZCA `@CurrentUserId UUID userId` (`security/CurrentUserIdArgumentResolver`, `config/WebConfig`'te kayıtlı);
  istekten (path/query/gövde) userId alınmaz. `sub` kuralı `security/JwtSubjects.userId`: yalnızca `UUID.toString()` biçimi (küçük harf,
  36 karakter; "1-1-1-1-1" ve büyük harf reddedilir). İki katman: (1) `JwtDecoderConfig` common decoder'ı sarar, geçersiz sub →
  `BadJwtException` → 401 `Bearer error="invalid_token"` (her yolda); (2) resolver yine de geçersiz/JWT olmayan kimlik görürse
  `InvalidSubjectException` (ApiException, UNAUTHORIZED) → common handler aynı 401 + invalid_token başlığı. 500 olmaz.
- Loglarda yalnızca `METHOD path -> CODE` (+ kısıt/kök neden sınıfı); token, sub, claim (e-posta) yazılmaz (catalog kuralı; testli).
- Hata kodları `CartErrorCode`: CART_LINE_LIMIT_EXCEEDED / CART_QUANTITY_LIMIT_EXCEEDED / BOOK_NOT_AVAILABLE (409 INFO), CATALOG_UNAVAILABLE
  (503 WARN). `CartLimitExceededException.lines(max)` / `.quantityPerItem(max)` → yanıta yalnızca `limit` (yapılandırılmış üst sınır)
  uzantısı; istenen değer ve bookId yanıtta YOK. `BookNotAvailableException` alan taşımaz. `CatalogUnavailableException(cause)` →
  GlobalExceptionHandler'da ayrı handler: tek WARN `... -> CATALOG_UNAVAILABLE (cause=<kök neden SimpleName>)`, stack/mesaj yok.
- `DbConstraintCodes`: uk_carts_active_user, uk_cart_items_cart_book (sızarsa) ve diğer tüm kısıtlar → CONFLICT (cart'ta RESOURCE_IN_USE yok).
- `config/ClockConfig` `Clock.systemUTC()`; testlerde `MutableClock` `@Primary`.
- Internal zincir (Adım 8, catalog ile birebir kurulum): `config/InternalSecurityConfig` `@Order(1)`, `securityMatcher("/internal/**")`,
  stateless, CSRF/httpBasic/form/logout/anonymous kapalı, resource server YOK, common `InternalApiKeyAuthenticationFilter` (bean değil,
  `addFilterBefore(AuthorizationFilter)`), `hasRole(INTERNAL_SERVICE)`, 401 = common `InternalApiKeyAuthenticationEntryPoint`
  (`ApiKey realm="internal"`). Kullanıcı zinciri (`SecurityConfig`) `@Order(2)`, davranışı aynı. Kullanıcı JWT'si /internal'da 401;
  internal anahtar /api/cart'ta 401 `Bearer`. Anahtar kontrolü yönlendirmeden önce → anahtarsız `GET /internal/cart/snapshot` 401
  (Allow yok), anahtarlı 405.
- `config/InternalAuthConfig`: `InternalAuthProperties` + `InternalApiKeys` bean'i (özet biçimi common'da: 64 hex, değer mesajda yok).
  CATALOG'DAN FARK: özeti boş/yok istemci kapalı sayılmaz → bağlam açılmaz ("... must be set; cart-service does not start with a
  disabled internal client"); istemci listesi boşsa da açılmaz. application.yml `app.internal-auth.clients[0]` = `order-service` +
  `key-sha256: ${CART_INTERNAL_KEY_ORDER_SHA256:}`.
- Internal yolda maskeleme gerekmez: kullanıcı/kitap id'si yolda değil gövdede taşınır (`instance` = `/internal/cart/snapshot`).

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
- Hata altyapısı ve security handler'ları `common` modülünde (bkz. "common modülü"); serviste yalnızca `CatalogErrorCode`,
  `GlobalExceptionHandler` alt sınıfı (optimistic lock handler'ı + `errors`/`bookIds` ek alanları) ve kısıt eşlemesi.
- DB kısıt → ErrorCode eşlemesi tek yerde: `exception/DbConstraintCodes.classify` (Hibernate kind + normalize ad + MySQL hata kodu;
  genel yardımcılar common `DbConstraints`'te).
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
  ile kanalı kapatır. Outbox henüz `common`'da DEĞİL: worker sınıfları servis başına kopya (user-service ↔ catalog-service; taşıma ayrı adım).
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
- catalog-service farkı: erişim türü anotasyonla DEĞİL yol önekinden (`OpenApiConfig.accessRulesAndErrorResponses`; SecurityConfig ile
  aynı kural: `/api/admin/` bearerAuth, `/internal/` internalApiKey, diğer `security: []`); global security yok. Yeni admin/internal uç
  için güvenlik anotasyonu YAZMA; yalnızca `@Tag` (OpenApiConfig.TAG_* sabitleri) + `@Operation` + başarı/uca özel `@ApiResponse`
  (yalnızca açıklama; customizer içeriği `Problem` yapar). Uca özel problem şeması gerekiyorsa `content = @Content(mediaType =
  PROBLEM_JSON, schema = @Schema(ref = ...))` → korunur. `@ModelAttribute` sorgu nesnesine `@ParameterObject`; aynı adlı nested
  record'lara `@Schema(name = ...)` (springdoc basit adla şema üretir, çakışanı sessizce ezer). Küçük harfli enum parametreleri
  (`sort`, `status`) için `@Schema(type = "string", allowableValues = ...)`. Test-only controller'lar `packages-to-scan` ile dışarıda.
  Catalog drift testi farkta değeri de gösterir ve üretilen dokümanı `target/openapi/` altına yazar.
- catalog response DTO'ları: yalnızca hiç null olmayan alan (DB'de NOT NULL ya da kodda her zaman atanan; listeler `toList()`/
  `List.of()`) `@Schema(requiredMode = REQUIRED)`; emin olunmayan/NULL olabilen alan işaretlenmez, nullable işareti de konmaz
  (user-service gibi: opsiyonel = `required` dışında). Yeni response alanında bu karar verilmeli; `OpenApiRequiredFieldsTest`
  gerçek yanıtları şemaya karşı kontrol eder (yeni 2xx şeması ekleyince o teste de örnek çağrı eklenmeli, yoksa kırılır).
- Para alanları JSON'da sayı: `BigDecimal`, scale 2 (`setScale(2)`), Jackson varsayılanı; metne çevirme. İstisna: outbox
  olay payload'larında para METİN (`"149.90"`), çünkü payload MySQL `JSON` kolonunda durur ve MySQL kesirli sayıyı DOUBLE'a çevirip
  sondaki sıfırları atar (`149.90` → `149.9`).

## Servisler arası HTTP istemcisi (cart → catalog ile doğrulandı)
- Spring Cloud OpenFeign (yalnızca ihtiyacı olan serviste starter; sürüm kök POM'daki `spring-cloud-dependencies` BOM'undan).
  `@EnableFeignClients` ana sınıfta. Alttaki HTTP istemcisi Feign varsayılanı (HttpURLConnection; hc5/okhttp yok).
- **İki katman:** `client/<Hedef>Client` (`@FeignClient(name = "<hedef>", url = "${app.<hedef>.base-url}")`, yalnızca Spring MVC
  anotasyonları) + `client/<Hedef>Gateway` (`@Component`, servis katmanının TEK temas noktası). Feign tipleri/hataları gateway dışına
  çıkmaz; client'ı başka sınıf kullanmaz. DTO'lar istemcinin kendi record'ları (`client/` paketinde), karşı servisin DTO'su kopyalanmaz.
- **DTO = tolerant reader:** `@JsonIgnoreProperties(ignoreUnknown = true)`; yalnızca kullanılan alanlar; karşı tarafta zorunlu olanlar
  `@JsonProperty(required = true)` (eksikse okuma hatası) + compact ctor `requireNonNull` (açık null). Opsiyonel alan (coverUrl) işaretsiz.
  Jackson 3 (`tools.jackson` 3.1.5) ile çalışıyor; anotasyonlar hâlâ `com.fasterxml.jackson.annotation`. Para `BigDecimal` (149.90 scale 2 korunur).
- **Hata eşlemesi gateway'de** (ErrorDecoder'da DEĞİL; aynı status metoda göre farklı anlam taşır): `FeignException` yakalanır →
  `RetryableException` (bağlantı reddi, connect/read timeout, Retry-After'lı 503) / status < 0 / 5xx / 2xx (DecodeException, okunamayan
  gövde) → hedefin "unavailable" exception'ı (503, neden olarak Feign hatası); metoda özel anlamlı 4xx (ör. kitap okumada 404) → iş
  exception'ı; diğer 4xx → `IllegalStateException` (500; mesajda URL/id YOK, Feign hatası cause olarak EKLENMEZ — mesajı URL içerir).
  2xx ama beklenen kaydı taşımayan yanıt (boş gövde, başka id, null eleman) da "unavailable" (`InvalidCatalogResponseException` nedeni).
- **Zaman aşımı/retry/log yml'de:** `spring.cloud.openfeign.client.config.<name>.{connect-timeout, read-timeout, logger-level: none}`
  (cart→catalog 1000/2000 ms). Retry yok: Spring Cloud varsayılanı `Retryer.NEVER_RETRY` (testte `FeignClientFactory.getInstance`
  ile doğrulanır; Retry-After'lı 503'te de tek istek). `logger-level` BASIC+ URL'yi (id), FULL header/gövdeyi loglar → NONE.
- **Token/header taşınmaz:** RequestInterceptor YOK (OpenFeign oauth2 desteği varsayılan kapalı). Karşı uç public değilse servisler arası
  anahtar (`X-Internal-Api-Key`) ayrı bir interceptor'la eklenir; kullanıcı token'ı ASLA. Testte stub gelen istekte Authorization/Cookie
  olmadığını, kimlikli gerçek istek içinden çağrılırken doğrular.
- **Log:** hedef kesintisi controller'a "unavailable" exception olarak çıkar → GlobalExceptionHandler tek WARN `... -> <KOD>
  (cause=<kök neden SimpleName>)`; URL/host/id/gövde loga girmez (OutputCapture testi).
- **Test:** `support/CatalogStub` (JDK HttpServer, ek bağımlılık yok; istekleri kaydeder, yanıt/gecikme/header programlanır, cached
  thread pool → geciken yanıt sonraki testi bekletmez). `ApiTestSupport`'ta statik, `app.catalog.base-url` DynamicPropertySource ile,
  her test başında `reset()`. Bağlantı reddi ayrı bağlamda (kapalı port; Windows'ta ConnectException ~0.1 sn). Gerçek hedefe karşı test
  `@EnabledIfSystemProperty(named = "catalog.live", matches = "true")` (varsayılan build'de skipped). Test-only probe controller
  (`/api/cart/_catalog/**`) gateway'i kimlikli istek içinden çağırır.
- **Tüketici sözleşme testi:** `client/CatalogContractTest` karşı servisin `docs/api/<hedef>.openapi.json`'ını (modülden `../docs/api/`,
  yalnızca okunur; yoksa anlamlı mesajla kırılır) okur: yollar + GET, 200 şemasından (`$ref` çözülerek) kullanılan alanların varlığı,
  tipi (OpenAPI 3.1 tip dizisi de kabul), formatı, zorunlularının `required`'da olması, toplu okuma `maxItems` ≥ istemci sınırı.
  Bozulmuş kopyalarla (yeniden adlandırma, required'dan çıkarma, tip değişimi, düşük maxItems) kontrolün kırıldığı testle gösterilir.

## Servis iskeleti (yeni servis, cart-service ile doğrulandı)
- catalog Adım 1–2 kalıbı: `NN-<servis>-db.sh` + compose mysql env + `.env.example`; application.yml (Hikari 5000 ms, validate, OSIV kapalı,
  jdbc UTC, Flyway, actuator yalnızca health); `TestcontainersConfiguration` (MySQL) + `application-test.yml` (datasource test/test);
  `@JdbcTest` + `@AutoConfigureTestDatabase(NONE)` ile şema/kısıt testi (CHECK → `UncategorizedSQLException` + 3819).
- Güvenlik yapılmadan önce geçici SecurityFilterChain: yalnızca health açık, diğer her şey common entry point'iyle 401;
  `UserDetailsServiceAutoConfiguration` exclude (üretilmiş parola logu yok). Kalıcı güvenlik adımında tarif adım 4 ile değiştirilir;
  tam context testleri `ApiTestSupport`'a taşınır (tek context, tek konteyner).
- "Kullanıcı başına tek aktif kayıt" = VIRTUAL generated kolon (`CASE WHEN status = 'active' THEN user_id END`) + UNIQUE
  (user-service `default_owner` ile aynı fikir; NULL'lar UNIQUE'e takılmaz).
- KURAL: yeni servislerde durum kolonları `utf8mb4_bin` (ör. `status VARCHAR(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'active'`).
  Tablo varsayılanı `utf8mb4_0900_ai_ci` olduğundan aksi halde `CHECK (status IN (...))` ve `status = 'active'` içeren generated
  ifadeler büyük/küçük harf duyarsız olur ('ACTIVE' geçer ve aktif sayılır). Şema testi kolonun collation'ını ve 'ACTIVE'/'Active'
  INSERT'inin 3819 verdiğini doğrular (cart `CartSchemaConstraintsTest`). Metin kolonları (başlık vb.) `_ai_ci` kalır.

## Aggregate entity'leri (cart-service)
- Aggregate root (`Cart`) satırları `@OneToMany(mappedBy, cascade = ALL, orphanRemoval = true)` + `@OrderBy` ile `List`'te tutar;
  getter salt okunur görünüm, değişiklik yalnızca root'un metotlarıyla. Satırın ayrı repository'si yok. Satır constructor'ı package-private.
- Entity'de yalnızca bütünlük (DB kısıtlarıyla aynı sınırlar + durum geçişleri → IAE/ISE); iş limitleri serviste.
- TÜM zamanlar `Clock` parametresiyle, MICROS'a kesilir (`Cart.now(clock)`): oluşturma (fabrika/ekleme) VE güncelleme. Değiştiren
  her metot (`addItem`, `removeItem`, `clear`, `changeQuantity`, `refreshSnapshot`, `checkout`, `abandon`) Clock alır; satır değişince
  satırın ve sepetin `updatedAt`'i aynı anla yenilenir (`Cart.touch(clock)`; geçmiş sepette ISE). Reddedilen değişiklik (IAE/ISE)
  zamana dokunmaz; hiçbir şey silinmeyen `removeItem` de. `@PreUpdate`/`Instant.now()` YOK (testte MutableClock ile DB değerine kadar doğrulanır).
  `@CurrentTimestamp(event = UPDATE)` KULLANMA: Hibernate 7.4.5 kolonu INSERT'ten çıkarır, DB default'u yazılır.
- Durum enum'u converter'ı okumada birebir eşleşme ister (bilinmeyen değer → anlamlı IAE).
- Okuma: `@EntityGraph(attributePaths = "items")` (tek sorgu). Değiştirme: root satırı `PESSIMISTIC_WRITE` (JPQL, satırlar yüklenmeden);
  bekleme üst sınırı bağlantı seviyesinde (Hikari `connection-init-sql: SET SESSION innodb_lock_wait_timeout = N`), çünkü
  `jakarta.persistence.lock.timeout` ipucu MySQL'de pozitif değerlerde etkisiz (yalnızca -2 SKIP LOCKED / 0 NOWAIT çalışır).
- Flush sırası INSERT → UPDATE → DELETE (orphan silme dahil): aynı UNIQUE anahtarı boşaltıp dolduran işlemler (satır sil + aynı kitabı
  ekle, checkout + yeni aktif sepet) arasında `flush()` gerekir.

## Sepet servisi (cart-service)
- Üç katman: `CartService` (BİLEREK TX'siz) → `CartTransactions` (`@Component`, kısa DB TX'leri, entity yerine `CartContents` döner)
  → `CartViewAssembler` (TX kapandıktan sonra Catalog'la birleştirir). Catalog çağrısı ASLA DB TX'i/satır kilidi tutulurken yapılmaz.
- Yazma TX'leri `READ_COMMITTED` (catalog rezervasyonuyla aynı gerekçe): REPEATABLE READ'de olmayan satıra `FOR UPDATE` gap lock alır,
  eşzamanlı iki "ilk sepet" INSERT'i deadlock olur. RC'de ikinci INSERT `uk_carts_active_user`'da birincinin commit'ini bekler ve ihlalle
  düşer → `CartService` `DbConstraints.isViolated(ex, "uk_carts_active_user")` ise TX'i BİR KEZ yeniden çağırır (yeni TX, artık var olan
  sepeti kilitler); ikinci ihlal ve diğer kısıtlar handler'a (409 CONFLICT, log `constraint=…`). Yeni sepet `saveAndFlush` ile hemen
  yazılır (ihlal satırlar eklenmeden görülsün). Aynı kullanıcının diğer yazımları sepet satırının `FOR UPDATE`'inde sıraya girer → limit
  kontrolleri (satır sayısı, adet) kilit altında, yarışsız.
- Ekleme sırası: (1) `CatalogGateway.requireAvailableBook` (TX dışı; 409/503'te sepet açılmaz/değişmez) → (2) TX: kilitle-ya-da-aç;
  kitap sepetteyse `yeni adet = mevcut + istenen` > `max-quantity-per-item` → 409 QUANTITY, değilse `changeQuantity` + `refreshSnapshot`
  (güncel Catalog bilgisi); yoksa satır sayısı ≥ `max-lines` → 409 LINE, adet > limit → 409 QUANTITY, değilse `addItem`; `flush()` →
  (3) assembler. İstek DTO'su DB sınırını (1–99, 400) doğrular; iş limiti (10) serviste (409 + `limit`). Yanıtta bookId/istenen değer yok.
- Okuma: `findByUserIdAndStatus` (EntityGraph, tek SQL, kilitsiz, readOnly TX) → içerik → assembler. Aktif sepet yoksa boş yanıt; sepet
  OLUŞTURULMAZ, Catalog çağrılmaz.
- Görünüm (`CartResponse`): satırlar added_at sırasıyla; `currentUnitPrice` yalnızca Catalog'da var + stokta ise; `available` true/false
  (lookup'ta yok ya da stokta değil)/null (Catalog'a ulaşılamadı); `priceChanged = current != null && current ≠ snapshot (compareTo)`;
  `lineTotal = qty × (current ?: snapshot)`; `subtotal` = `available != false` satırların toplamı; para birimleri farklıysa `subtotal` ve
  `currency` null. Para scale 2 (HALF_UP), JSON sayı. Sepet/satır id'si ve userId yanıtta YOK. `catalogStatus` VERIFIED | UNAVAILABLE.
- Catalog kesintisinde GET hata vermez: assembler `CatalogUnavailableException`'ı yakalar, tek WARN `GET /api/cart -> CATALOG_UNAVAILABLE
  (cause=<kök neden>, served from snapshot)` (ProblemDetails.log biçimi, istek RequestContextHolder'dan), UNAVAILABLE döner.
  `IllegalStateException` (Catalog'un beklenmedik 4xx'i) yukarı çıkar → 500.
- Uçlar ve API tablosu (satır yolda bookId ile adreslenir; tüm değiştiren uçlar GET ile aynı `CartResponse`'u döner):
  `GET /api/cart`, `POST /api/cart/items` {bookId, quantity?}, `PATCH /api/cart/items/{bookId}` {quantity}, `DELETE /api/cart/items/{bookId}`,
  `DELETE /api/cart/items`. Hepsi yalnızca AKTİF sepete dokunur; checked_out/abandoned sepet hiç değişmez.
- PATCH adet: Catalog çağrısı yok (yalnızca assembler'ın lookup'ı), snapshot DEĞİŞMEZ. TX: sepet `FOR UPDATE` → sepet ya da satır yoksa
  404 RESOURCE_NOT_FOUND (detail "Book is not in the cart.", bookId yok) → adet > `max-quantity-per-item` 409 QUANTITY + `limit` (satır
  değişmez) → `changeQuantity(q, clock)`. DTO 1–99 (400). Yayından kalkmış kitabın adedi de değişir (available false); Catalog
  kesintisinde değişiklik uygulanır, 200 UNAVAILABLE.
- DELETE satır: idempotent 200; sepet yoksa boş yanıt (sepet AÇILMAZ, Catalog çağrılmaz); satır varsa `removeItem(id, clock)`. Son satır
  silinse de sepet aktif kalır, sonraki POST aynı sepeti kullanır.
- DELETE tümü: `clear(clock)`; sepet silinmez, aktif ve boş kalır. Yanıt her zaman boş (Catalog çağrılmaz). Sepet yoksa açılmaz.
- Damgalama kararı: DEĞİŞİKLİK YOKSA DAMGA YOK — aynı adetle PATCH (UPDATE SQL'i bile yok), olmayan satırı silmek, zaten boş sepeti
  boşaltmak `updated_at`'e dokunmaz. Değişiklikte satır + sepet `updated_at` = Clock; satır silmede sepet damgalanır.
- Yol maskeleme: `exception/MaskedRequestPaths` hata yazan her noktada (cart GlobalExceptionHandler override'ları, assembler WARN'ı,
  SecurityConfig'te sarılmış common security handler'ları) `/api/cart/items/<x>` → `/api/cart/items/:bookId` (ProblemDetail `instance` +
  log). `{}` kullanılmaz (common `URI.create` → geçersiz → instance null). Path'te bozuk UUID → 400 MALFORMED_REQUEST (TypeMismatch,
  common'ın varsayılan dalı; mevcut davranış).
- Internal snapshot (`POST /internal/cart/snapshot` {userId}, `controller/internal/InternalCartController`, DTO'lar `dto/internal/`):
  `service/CartSnapshotService` `@Transactional(readOnly = true)` → `findByUserIdAndStatus(userId, ACTIVE)` = TEK SQL (EntityGraph
  join, `for update` yok). Kilit yok, sepet açılmaz, damga yok, Catalog yok. Yanıt `{cartId, updatedAt, items[{bookId, quantity,
  unitPriceSnapshot (scale 2, sayı), currency, title}]}`; aktif sepet yoksa (kapanmış sepetler dahil) cartId/updatedAt null + items [];
  aktif sepet boşsa cartId dolu + items []. userId ve coverUrl yanıtta yok. Gövde okunamazsa (boş, bozuk JSON, UUID değil) 400
  MALFORMED_REQUEST, userId yok/null 400 VALIDATION_FAILED; gönderilen değer yanıtta yok.
- Test: `support/FakeCatalog` (CatalogStub yanıtlayıcısı; harita = yayındaki kitaplar, `failWith` ile kesinti); yarış senaryoları
  `ApiTestSupport.carts` spy'ı (`@MockitoSpyBean`) ile deterministik; gerçek eşzamanlılık `runConcurrently` (MockMvc, gerçek thread'ler).

## Container (servis başına)
- `<servis>/Dockerfile`, build context = repo kökü (kök pom + mvnw gerekir). Çok aşamalı: pom'lar → `go-offline`
  (cache mount) → src → `package -DskipTests` → layered extract → JRE runtime, sabit UID/GID 10001 non-root.
- Modül pom'ları `COPY --parents */pom.xml ./` (Dockerfile frontend ≥ 1.20, `# syntax=docker/dockerfile:1` yeterli): yeni modül
  eklenince mevcut Dockerfile'lar DEĞİŞMEZ. Kaynak yalnızca servisin ve derlediği modüllerin: `COPY --parents common/src <servis>/src ./`
  (diğer servislerin kaynağı imaja girmez). go-offline reaktör modülünü (common) uzaktan aramaz; src değişince go-offline katmanı cache'te kalır.
- Sırlar image'a girmez (`.dockerignore`); compose'ta env yalnızca tek tek, anahtar dosyaları compose `secrets`.
- Readiness'a yalnızca isteği karşılamak için şart olan bağımlılık (DB) girer; mesaj broker'ı girmez (outbox tamponlar).
  Diğer servisler de girmez ve compose'ta onlara `depends_on` konmaz (catalog → user-service JWKS tembel; kapalıyken 503).
- Servis içi adresler compose servis adıyla (`mysql`, `rabbitmq`, `http://user-service:8081`); profil verilmez (local seed yalnızca
  `spring-boot:run` ile). Her servis: Actuator health (yalnızca health expose) + compose healthcheck `curl` readiness, `mem_limit 768m`.

## Yeni servis eklerken (KULLANICI KURALI)
Kullanıcı, her yeni serviste kök `pom.xml`'in kontrol edilip gerekiyorsa
güncellenmesini ve her değişikliğin NE ve NEDEN olduğunun açıklanmasını istiyor.
Kök POM'un başındaki "YENİ SERVİS EKLERKEN KONTROL LİSTESİ" takip edilir:
1. `<modules>`'a ekle.
2. Servis POM'unun `<parent>`'ını kök POM yap, miras alınanları sil.
3. Yeni kütüphane ailesi varsa versiyonu `<properties>`'e, BOM'u `<dependencyManagement>`'a.
4. Tüm servislerde ortak olan bağımlılığı kök `<dependencies>`'e taşı.
5. Servis POM'una `kitap-sepeti-common`'ı (versiyonsuz) ekle; ortak bileşenleri `@Import`/`@Bean` ile kaydet
   (tarif: "Yeni servis common'ı nasıl kullanır").
6. (Docker) Servisin Dockerfile'ını mevcutlardan kopyala; modül pom'ları `COPY --parents */pom.xml` ile otomatik gelir, diğer
   Dockerfile'lara satır eklenmez. Servise özel olan yalnızca src satırı (`common/src` + kendi `src`'si) ve servis adı.
   Ardından tüm imajları build ederek doğrula.
