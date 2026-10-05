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
  security-oauth2-resource-server, amqp, resilience4j-circuitbreaker) → servise starter taşımaz; servis zaten kendi starter'larını ekler.
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
- İNTERNAL ÖZET POLİTİKASI (Order Adım 0a, TEK KURAL, `InternalApiKeys` ctor'u): istemci listesi boş → açılmaz ("must configure at least
  one internal client"); ad boş/tekrar → açılmaz; her istemcinin `key-sha256`'sı yok/boş/yalnızca boşluk → açılmaz ("... must be set to
  the 64-character hex SHA-256 digest of the API key"); trim sonrası `[0-9a-fA-F]{64}` değil → açılmaz ("... (configured value not
  shown)"). Mesajda özellik yolu + istemci adı, DEĞER YOK. Büyük/küçük harf hex kabul. "Boş özet = istemci kapalı" kavramı ve
  `Client.enabled()` KALDIRILDI. Servis tarafı: `config/InternalAuthConfig` (yalnızca `@EnableConfigurationProperties(InternalAuthProperties)`
  + `new InternalApiKeys(props)` bean'i; cart, catalog, payment aynı). Compose'da her `*_INTERNAL_KEY_*_SHA256` `${VAR:?}`.
- `common.web.RequestPathMasker` (Order Adım 0a): hata yanıtının `instance`'ı ve tüm hata/internal log satırlarındaki yol buradan geçer.
  - API: `RequestPathMasker.of("/api/x/{id}", ...)` (servis desenleri), `uuidOnly()` (desen yok), `mask(HttpServletRequest)`,
    `mask(String path)`, `ID_SEGMENT = ":id"`.
  - Desen kuralı: `/` ile başlar, `/` ile bitmez; segment ya literal `[A-Za-z0-9._~-]+` ya tam `{ad}` (`[A-Za-z][A-Za-z0-9]*`);
    değişken adı tekrarı ve aynı desen iki kez → `IllegalArgumentException`.
  - Eşleme SEGMENT YAPISINA bakar (değer UUID olmasa da `abc` → `:bookId`); dispatch'e bağlı değil → security filtrelerinde de çalışır.
    Birden fazla desen eşleşirse en az değişkenli kazanır (eşitlikte ilk kayıtlı) → `/api/books/lookup` `/api/books/{bookId}`'den önce.
  - Hiçbir desene uymayan yolda güvenlik ağı: tam segment UUID (büyük/küçük harf) → `:id`; diğer segmentler aynen.
  - Sorgu dizesi/fragment ASLA çıkmaz; context path korunur; sondaki `/` korunur; kalan segmentler RFC 3986 pchar'a yüzde-kodlanır →
    sonuç her zaman geçerli URI (eski davranış: ayrıştırılamayan yol → `instance` yok; artık `/a%20b%7Cc`).
  - Bağlama: common auto-config yok. Servis `SecurityConfig`'te `@Bean RequestPathMasker requestPathMasker()` tanımlar;
    `ProblemDetailExceptionHandler` `@Autowired(required = false)` setter ile alır, `ProblemDetailSecurityHandlers`
    `ObjectProvider.getIfAvailable(uuidOnly)`; internal entry point/filtre ve failure handler'a ctor parametresi olarak verilir.
    Bean yoksa her yerde `uuidOnly()`. `ProblemDetails.create/apply/log` maskeleyicisiz overload'ları da `uuidOnly()` kullanır.
  - Alt sınıf handler'lar `logProblem(code, request, ex, note)` + `respond(code, detail, request)` (instance metotları) kullanır.
  - Kayıtlı desenler: user `/api/me/addresses/{addressId}`; catalog `/api/books/lookup`, `/api/books/{bookId}`,
    `/api/admin/books/{bookId}` (+ `/publish`, `/archive`, `/stock-adjustments`), `/api/admin/authors/{authorId}`,
    `/api/admin/publishers/{publisherId}`, `/api/admin/categories/{categoryId}` (+ `/parent`), `/internal/stock/reservations/{orderId}`
    (+ `/commit`, `/release`); cart `/api/cart/items/{bookId}`; payment `/internal/payments/{paymentId}`.
    YENİ UÇ YOLDA ID TAŞIYORSA DESEN EKLE.
  - `patterns()` (kayıt sırasıyla desenler) ve `covers(mappingPattern)` (Order Adım 0b): controller eşleme kalıbıyla aynı BİÇİMDE
    (segment sayısı, değişken konumları, literal'ler; değişken adı/regex önemsiz) bir desen var mı; `*` içeren kalıp → false.
  - KAPSAMA TESTİ (servis başına `config/RequestPathMaskerCoverageTest`, user/catalog/cart/payment): `RequestMappingHandlerMapping`
    ("requestMappingHandlerMapping") içindeki `{` içeren her kalıp `covers` olmalı; eksikse test kalıbı adıyla kırılır. Kapsam: handler
    sınıfının code source'u uygulama sınıfınınkiyle aynı (`target/classes`) → test probe controller'ları (`test-classes`) ve framework
    uçları (jar) dışarıda. payment istisnası: `/webhooks/{provider}` (sağlayıcı adı id değil; 404 `instance`'ı ham yolu gösterir).
- Bu paketlerdeki sınıfların logger'ı artık `com.kitapsepeti.common.security(.internal).*` (mesaj metni aynı).
- common'a GİRMEYEN: güvenlik kuralları/yollar (SecurityConfig), user-service'in kendi anahtarlı JwtDecoder'ı (RsaKeyConfig),
  catalog JwtProperties, InternalSecurityConfig, servisin olay sınıfları/routing key eşlemesi/`SchedulingConfig`.
- `common.outbox` (Order Adım 0b): bkz. "Olaylar (transactional outbox)". spring-boot-starter-amqp de optional.
- `common.resilience` (Order Adım 3b; `resilience4j-circuitbreaker` optional, sürüm Spring Cloud BOM'dan 2.3.0; auto-config YOK):
  `CircuitBreakerProperties` (`@Validated @ConfigurationProperties("app.circuit-breaker")`: slidingWindowSize, minimumCalls,
  failureRateThreshold 1–100, openDuration, halfOpenCalls; servis `@EnableConfigurationProperties` ile kaydeder) ve `CircuitBreakers`
  (`config(props, clock)`: COUNT_BASED, otomatik OPEN→HALF_OPEN kapalı, stack trace yazılmaz, uygulama `Clock`'u; `create(name, config|props+clock)`
  → instance + WARN `Circuit breaker <ad> <eski> -> <yeni>` (logger `c.k.common.resilience.CircuitBreakers`)). Neyin hata sayılacağı
  ÇAĞIRANDA kalır (`tryAcquirePermission` / `onSuccess` / `onError` / `releasePermission`): Order sonuç tabanlı (`RemoteCalls`,
  NotPerformed/Unknown), Cart istisna tabanlı (`CatalogGateway.guarded`). Health/readiness'a girmez (resilience4j Spring Boot
  modülü/Spring Cloud CB starter'ı olmayan serviste health göstergesi zaten yok). Testli: `CircuitBreakersTest` 5.

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
5. Internal uç sunuyorsa: `config/InternalAuthConfig` (cart kopyası: `@EnableConfigurationProperties(InternalAuthProperties.class)`,
   `new InternalApiKeys(props)`), filtreyi zincirde `new InternalApiKeyAuthenticationFilter(keys, entryPoint, pathMasker)` ile ekle
   (bean yapma), entry point'i (`new InternalApiKeyAuthenticationEntryPoint(jsonMapper, pathMasker)`) `exceptionHandling`'e ver.
5b. Yolda id taşıyan her uç için `SecurityConfig`'te `@Bean RequestPathMasker requestPathMasker()` desenleri; failure handler'a
   (`new ProblemDetailAuthenticationFailureHandler(entryPoint, jsonMapper, pathMasker)`) da ver.
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
  Yapılandırma `app.internal-auth.clients[{name, key-sha256}]` (yalnızca özet; yok/boş/bozuk → açılmaz, Order Adım 0a). Format doğrulaması
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
- `config/InternalAuthConfig`: `InternalAuthProperties` + `InternalApiKeys` bean'i; politika common'da (yukarıda "İNTERNAL ÖZET
  POLİTİKASI", tüm servislerde aynı). application.yml `app.internal-auth.clients[0]` = `order-service` +
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
- Tüm servisler aynı `kitapsepeti.events` exchange'ine yayınlar; TEK tanım `common.outbox.OutboxConfiguration.eventsExchange`
  (`new TopicExchange(name, true, false)`, argümansız). Farklı durable/autoDelete/argüman → broker PRECONDITION_FAILED
  ile kanalı kapatır.
- payment payload'ında `eventId` (= outbox id = `message_id`) var; user/catalog payload'ında yok (servisin seçimi).
  Sonuç olayları `PaymentResults`'ta durum geçişiyle aynı TX'te, yalnızca APPLIED'da.

### Ortak outbox (`common.outbox`, Order Adım 0b) — Order/Notifications için KULLANIM KALIBI
- common'da: `OutboxEvent` (entity, `outbox` tablosu; id `OutboxEvent.newId()` = UUIDv7, `@UuidGenerator(VERSION_7)` ile aynı strateji),
  `OutboxRepository` (`lockUnpublishedBatch`, FOR UPDATE SKIP LOCKED), `OutboxService` (bean değil; `OutboxConfiguration` kaydeder),
  `OutboxPublisher` (routing key'i ctor'daki `OutboxRoutingKeys`'ten alır), `OutboxRelay`, `OutboxProperties` (`app.outbox.*`),
  `OutboxPublishException`, `OutboxRoutingKeys` (`of(Map)`; bilinmeyen tip → `IllegalStateException("No routing key for event type X")`),
  `OutboxConfiguration` (exchange + `OutboxService` + `app.outbox.enabled=true` ise `OutboxRelay` + açılışta `AmqpAdmin.initialize()`).
- Yazma API'si: `outboxService.append(aggregateType, aggregateId, eventType, eventId -> payload)` — id payload'dan ÖNCE üretilir, satır
  id'si = fabrikaya verilen id; `append(..., Object payload)` eventId'siz kısayol. İkisi de `Propagation.MANDATORY`; `EntityManager.persist`.
- Yeni servis (Order/Notifications) yapacakları:
  1. Migration'a `outbox` tablosunu user/catalog/payment ile BİREBİR aynı DDL ile ekle (payment `PaymentSchemaConstraintsTest`
     catalog ve user DDL'ini karşılaştırır; yeni servis de benzer test koymalı). Entity `ddl-auto: validate` ile doğrulanır.
  2. Ana sınıfa `@AutoConfigurationPackage(basePackageClasses = { <Uygulama>.class, OutboxEvent.class })` (entity + repository taraması;
     `@DataJpaTest` de okur, `@JdbcTest` etkilenmez). UYGULAMA SINIFI DA YAZILMALI: doğrudan konan anotasyon `@SpringBootApplication`'ın
     varsayılan paketinin yerine geçer (yalnızca OutboxEvent yazılınca servisin repository'leri bulunmaz → context açılmaz). `@EnableJpaRepositories`/`@EntityScan` KULLANMA (servis kendi paketlerini de kaybeder / `@JdbcTest` kırılır).
  3. `outbox/EventRoutingKeys` (`OutboxRoutingKeys.of(Map.of(Olay.TYPE, "<varlık>.<olay>"))` + statik `forEventType`) ve
     `outbox/OutboxPublisher extends common OutboxPublisher` (ctor `(RabbitTemplate, OutboxProperties)` → `super(..., EventRoutingKeys::forEventType)`;
     `@Component` DEĞİL).
  4. `config/OutboxConfig`: `@Import(OutboxConfiguration.class)` + `@Bean OutboxPublisher outboxPublisher(RabbitTemplate, OutboxProperties)`.
  5. `config/SchedulingConfig` (`@EnableScheduling`, `app.outbox.enabled` koşullu; başka iş varsa AnyJobEnabled kalıbı) + `Clock` bean'i.
  6. yml: `spring.rabbitmq` (connection-timeout 5s, publisher-confirm-type correlated), `app.outbox` (enabled true, kitapsepeti.events,
     2s, 50, 5s), `logging.level.org.springframework.amqp.rabbit.connection.CachingConnectionFactory: WARN` (INFO satırı kullanıcı adı yazar);
     test profili `app.outbox.enabled: false`.
- Log satırları (kategori `com.kitapsepeti.common.outbox.*`): WARN "Outbox poll skipped, database unavailable: {Sınıf}", DEBUG "Published {}
  outbox event(s)", WARN "Outbox publish failed, will retry on next poll: id={}, eventType={}, error={}", WARN "RabbitMQ unavailable at
  startup; exchange will be declared on first connection ({})".
- BİLİNEN SORUNLAR (common outbox; Adım 0b'de bilerek DEĞİŞTİRİLMEDİ):
  - Routing key'i olmayan `event_type` satırı kuyruğu tıkar: `IllegalStateException` gönderimden önce fırlar, relay her turda aynı satırda
    WARN yazıp durur; arkasındaki satırlar hiç yayınlanmaz.
  - Yayın hatası WARN'ı `id=<eventId>` yazar (olay id'si logda görünür).
- catalog testlerinde RabbitMQ konteyneri ayrı `RabbitTestcontainersConfiguration`; tam context açan testler import eder,
  dilim testleri (`@DataJpaTest`, `@JdbcTest`) etmez.
- JPQL/SQL bulk UPDATE `@UpdateTimestamp`/`@Version`'ı atlar; `updated_at` yine de kolonun `ON UPDATE CURRENT_TIMESTAMP(6)`
  tanımıyla DB saatine güncellenir (değer değiştiren her UPDATE'te).
- Adlandırma: `aggregate_type` küçük harf varlık adı (`user`, `book`); `event_type` PascalCase geçmiş zaman/olgu (`UserRegistered`,
  `BookUpserted`, `BookRemoved`, `PaymentSucceeded`, `PaymentFailed`); routing key `<varlık>.<olay>` küçük harf
  (`user.registered`, `book.upserted`, `book.removed`, `payment.succeeded`, `payment.failed`).
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
- cart-service OpenAPI (Adım 9, catalog kalıbı): erişim yol önekinden (`/internal/` → internalApiKey, diğer HER yol → bearerAuth;
  public operasyon yok). Customizer gövdeliye 400 (VALIDATION_FAILED/MALFORMED_REQUEST), yalnızca path değişkenliye 400
  (MALFORMED_REQUEST), kullanıcı uçlarına 401 (`WWW-Authenticate: Bearer`) + 503 AUTHENTICATION_UNAVAILABLE, internal'a 401
  (`ApiKey realm="internal"`), hepsine 500 ekler. Catalog'dan farkı: `{`'li yola otomatik 404 YOK (DELETE /items/{bookId} idempotent
  200); 404/409/uca özel 503 controller'da `@ApiResponse`. Limit 409'ları `CartLimitProblem` (allOf Problem + `limit`).
  `@CurrentUserId` `SpringDocUtils.addAnnotationsToIgnore` ile dokümandan gizli (yoksa sorgu parametresi görünür).
  `CatalogStatus` `@Schema(enumAsRef = true)`. Tag'ler `Cart`, `Internal` (sıralı).
- cart response DTO'ları catalog'dan FARKLI: Jackson null'ları yazdığı için TÜM alanlar `requiredMode = REQUIRED`; null olabilenler
  ayrıca `types = { "<tip>", "null" }` (OpenAPI 3.1; uuid/date-time'da `format` elle). Nullable: CartResponse currency/subtotal,
  CartLineResponse coverUrl/currentUnitPrice/available, CartSnapshotResponse cartId/updatedAt. `OpenApiRequiredFieldsTest` (cart)
  required → var, null → şemada nullable, şemada olmayan alan yok, JSON tipi/enum uyumu ve her nullable alanın en az bir örnekte null
  görülmesini denetler; yeni nullable alan ya da 2xx şeması eklenirse oraya örnek çağrı eklenmeli.
- payment-service OpenAPI (Adım 7, cart kalıbı): `packages-to-scan` controller.internal + controller.webhook; erişim yol önekinden
  (`/internal/` → `internalApiKey`, `/webhooks/` → `mockWebhookSignature` apiKey `X-Mock-Signature`; başka önek customizer'da
  IllegalStateException). Customizer gövdeliye/path değişkenliye 400, internal'a 401 `ApiKey realm="internal"`, webhook'a 401
  `Signature realm="webhook"` (WEBHOOK_SIGNATURE_INVALID), hepsine 500 ekler; `$ref`'li özelliğin yanındaki `type`'ı siler
  (`@Schema(ref)` String alanda springdoc tipi de yazar). Webhook metodu `@RequestBody` almadığı için gövde `@Operation(requestBody =
  WebhookEvent)` ile, `X-Mock-Timestamp` metotta `@Parameter(in = HEADER, required)`, `provider` `allowableValues = "mock"`;
  `WebhookEvent.isFailureCodeMatchesType` `@Schema(hidden = true)`. `PaymentStatus` component şeması OpenApiConfig'te
  (`PaymentStatus.dbValue()`'dan), `PaymentResponse.status` `@Schema(ref)`. PaymentResponse cart gibi tüm alanlar REQUIRED, nullable
  yalnızca failureCode + redirectUrl. Para örneği (`example`) yazılmaz (sayı `149.9` görünür). `@Size(max)` `@Schema(minLength)`'i ezer → alt sınır
  `@Size(min = 1, max = N)` ile verilir (Adım 8, `WebhookEvent.providerPaymentId`). Yan etki: `@NotBlank` + `@Size(min = 1)` boş
  metinde iki `errors` girdisi üretir (400 VALIDATION_FAILED aynı).
  Belgelenmeyen (bilinçli): internal uçlarda genel 406/415, webhook'ta pratikte ulaşılamayan 409 CONFLICT.
- Kart verisi kontrolü (payment `CardDataAbsenceTest`): main sınıfların static olmayan alanları + OpenAPI özellik/parametre/başlık
  adları kelimelere bölünüp (camelCase/`_`/`-`) card, pan, cvv, cvc, expiry, cardholder, cardnumber ile karşılaştırılır (`company`
  yakalanmaz; `CARD_DECLINED` static sabit hariç). DB tarafı `PaymentSchemaConstraintsTest.noCardDataColumnsInAnyTable`.
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
- **Circuit breaker (cart→catalog, Order Adım 3b):** `config/CatalogCircuitBreakerConfig` tek `catalog` instance'ı (`common.resilience`,
  `app.circuit-breaker.*` 20/10/%50/10 sn/3; Spring Cloud CB starter'ı YOK, yalnızca `resilience4j-circuitbreaker`).
  `CatalogGateway.guarded(call)`: izin yoksa Feign çağrılmaz, `CatalogUnavailableException(CallNotPermittedException)` → mevcut
  "Catalog yok" yolu AYNEN (ekleme 503 CATALOG_UNAVAILABLE sepet değişmez; görünüm 200 `catalogStatus=UNAVAILABLE`, available/
  currentUnitPrice null, toplamlar snapshot'tan). Hata = `CatalogUnavailableException`'a dönüşen her şey (bağlantı, zaman aşımı, 5xx,
  bozuk/eksik 2xx); BAŞARI = 2xx, `BookNotAvailableException` (404 / inStock=false), `IllegalStateException` (diğer 4xx); beklenmeyen
  istisna → `releasePermission`. Tek fark logda: açıkken `cause=CallNotPermittedException`. Başarılı ekleme Catalog'u İKİ kez çağırır
  (kitap + yanıttaki görünüm için lookup) → ikisi de pencereye girer. Test bağlamı paylaşıldığı için `ApiTestSupport` her test SONUNDA
  `catalogCircuitBreaker.reset()` (kapatma WARN'ı açan testin çıktısında kalır; başta yapılırsa sonraki testin WARN sayımını bozar).
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
- payment-service güvenliği (Adım 3; Adım 1'deki security'siz iskeletin yerine): kullanıcıya açık uç ve JWT YOK. İki zincir:
  `InternalSecurityConfig` @Order(1) `/internal/**` (cart kalıbı, istemci `order-service`, 401 `ApiKey realm="internal"`) ve
  `SecurityConfig` @Order(2) (GET health(/**) + `/error` permitAll, gerisi denyAll). Varsayılan zincirde anonim istek 403 FORBIDDEN,
  WWW-Authenticate YOK (entry point `ProblemDetailAccessDeniedHandler`'a delege eder; common'daki `ProblemDetailAuthenticationEntryPoint`
  Bearer challenge yazdığı için kullanılmaz). `/internal` (eki yok) da `/internal/**` eşleşir → 401. Adım 5'ten beri ÜÇ zincir:
  internal @Order(1), `WebhookSecurityConfig` @Order(2) (`/webhooks/**`; yalnızca `POST /webhooks/*` permitAll, diğer metot/yol
  denyAll → 403 challenge'sız; CSRF/session/request cache yok; kimlik imzadır, controller'da doğrulanır), `SecurityConfig` @Order(3).
  `SecurityRulesTest.chainsAreOrderedInternalWebhookDefault` sırayı kilitler. `PaymentServiceApplicationTests` openfeign/springdoc'un
  classpath'te olmadığını kilitler.
- payment webhook (Adım 5): `POST /webhooks/{provider}` (`controller/webhook/WebhookController`). Kontrol sırası: etkin sağlayıcı
  değil → 404 NOT_FOUND (gövde okunmaz, tür bakılmaz) → Content-Type `application/json` (tür+alt tür; charset serbest) değil → 415 →
  Content-Length > `max-body-bytes` ya da okunan > sınır (en fazla sınır+1 bayt okunur) → 413 PAYLOAD_TOO_LARGE → imza (ham bayt,
  JSON'dan ÖNCE) → 401 WEBHOOK_SIGNATURE_INVALID + `WWW-Authenticate: Signature realm="webhook"` → JSON (katı: metin alanına
  sayı/boolean yok, sonda içerik yok, tekrar anahtar yok, bilinmeyen alan yok sayılır; okunamazsa 400 MALFORMED_REQUEST, Jackson
  exception'ı zincirlenmez) → Bean Validation (400 VALIDATION_FAILED, `ConstraintViolationException` yolu) → `WebhookService` → 204.
- İmza (`provider/mock/MockWebhookSigner` / `MockWebhookVerifier`): başlıklar `X-Mock-Timestamp` (epoch saniye, `^[0-9]{1,18}$`) ve
  `X-Mock-Signature` = `sha256=` + küçük harf hex(HMAC-SHA256(key = secret'ın UTF-8 baytları, mesaj = "<ts>.<ham gövde>")). Damga
  Clock'a göre ±`tolerance` (sınır dahil). İmza baytları `MessageDigest.isEqual`. Her ret aynı `WebhookSignatureException`; log tek
  WARN `Rejected webhook: invalid signature (provider=mock)` (yol/damga/imza/neden yok). Secret `app.payment.mock.webhook-secret` ←
  `PAYMENT_MOCK_WEBHOOK_SECRET`; yok/boş/<32 → açılmaz (kontrol signer ctor'unda; Bean Validation değil çünkü o hata reddedilen
  değeri yazar), `PaymentProperties.Mock.toString` maskeli.
- `WebhookService.handle` (@Transactional READ_COMMITTED): `findByProviderReferenceForUpdate` (yoksa 400 UNKNOWN_PAYMENT) →
  `existsByProviderTypeAndProviderEventId` (varsa DUPLICATE) → tutar `compareTo` + para birimi (uymazsa 400 AMOUNT_MISMATCH) →
  `ProviderEvent.record` + saveAndFlush → `PaymentResults` (aynı TX). Reddedilen olay kaydedilmez. Controller TX dışında
  `uk_provider_events_provider_event` ihlalini DUPLICATE sayar (204). Log: INFO `Webhook handled (provider=…, type=…, outcome=…)`.
- Test: `support/WebhookTestSecrets` (çalıştırma başına rastgele secret; ApiTestSupport ve OutboxRelayBrokerOutageIT register eder),
  `support/MutableClock` (cart kopyası), `controller/webhook/WebhookTestSupport` (referanslı ödeme, `signed(...)`). DİKKAT: MockMvc
  `.header()` değer EKLER (değiştirmez) — bozuk imza testinde istek sıfırdan kurulmalı. MockMvc print-on-failure dökümü (istek gövdesi)
  sonraki testin CapturedOutput'una düşebilir: bir log testi kırılırsa önce önceki testin hatasına bak.
- payment mock webhook gönderimi (Adım 6, `provider/mock/`): `PaymentTransactions.attachReference` referansı GERÇEKTEN yazınca
  (`attachProviderReference` true) ve sağlayıcı mock ise `MockPaymentReadyEvent(paymentId)` yayınlar → `MockWebhookDispatcher`
  `@TransactionalEventListener(AFTER_COMMIT)` gönderimi `app.payment.mock.delay` (500ms) sonrasına KENDİ zamanlayıcısına planlar
  (bean OLMAYAN `ThreadPoolTaskScheduler`, 2 thread, daemon; bean olsaydı `@Scheduled` görevleri ve Boot'un varsayılan scheduler'ı ona
  geçerdi). Sınır: `AtomicInteger` bekleyen sayacı ≥ `dispatch.queue-capacity` (100) → düşür + WARN (değersiz), kurtarma toparlar.
  `send(paymentId)`: `findById` (kilitsiz; Spring Data readOnly) → yok/final/referanssız/mock değil → SKIPPED; gövde
  `MockWebhookPayload` (eventId `mock_evt_<paymentId>` SABİT, amount scale 2 metin, failureCode yalnızca failed — `@JsonInclude
  NON_NULL`) bir kez `writeValueAsBytes` → aynı baytlar `MockWebhookSigner.sign(clock epoch sn)` → RestClient
  (`JdkClientHttpRequestFactory`, HTTP/1.1, connect 1000 ms, read 2000 ms, redirect yok, tekrar yok). URL `app.payment.mock.webhook-url`
  yoksa `WebServerInitializedEvent` portu (`http://localhost:<port>/webhooks/mock`; management namespace atlanır). Sonuç enum
  `Delivery`: 2xx DELIVERED (DEBUG), 4xx REJECTED (WARN `Mock webhook rejected (status=…)`), 5xx/IO/timeout/beklenmeyen FAILED (WARN
  `Mock webhook delivery failed (status=…|cause=<SınıfAdı>)`); exception dışarı ÇIKMAZ. `SimpleClientHttpRequestFactory` KULLANMA:
  HttpURLConnection streaming modda 401'de HttpRetryException atar (4xx sınıflandırması bozulur).
- payment `MockRecoveryJob`: `@Scheduled(fixedDelay = initialDelay = recovery.interval)` (ilk tur da bir aralık sonra: port açılışta
  bilinmeyebilir) → `PaymentRepository.findStaleWithReference(INITIATED, MOCK, now - min-age, Limit batch-size)` (JPQL, `order by
  createdAt, id`; EXPLAIN: `ix_payments_status_created` range, key_len 74 = iki kolon, filesort yok — InnoDB ikincil indeksi PK'yı
  taşır) → her id için senkron `dispatcher.send` (exception atmaz, biri diğerini durdurmaz). Log yalnızca `Mock recovery resent N
  webhook(s)` (INFO, N>0; N = DELIVERED). Kilit YOK: çok instance aynı ödemeyi gönderebilir, sabit eventId → webhook 204 tekrar.
  Referanssız initiated ödemeye dokunmaz (Order'ın tekrar isteği tamamlar).
- `MockWebhookConfig` (`app.payment.provider=mock`, matchIfMissing): dispatcher her zaman (kurtarma kullanır; `dispatch.enabled=false`
  yalnızca otomatik planlamayı kapatır, zamanlayıcı da oluşmaz), job `recovery.enabled`. `SchedulingConfig` catalog gibi
  `AnyJobEnabled` (outbox VEYA provider=mock + recovery; Boot 4'te `@ConditionalOnProperty` tekrarlanabilir → üye sınıfta iki koşul AND).
- Test profili: `dispatch.enabled=false` + `recovery.enabled=false` (MockMvc testlerinde port yok, mevcut testler initiated'ı kendisi
  yönetir; `OutboxDisabledTest` zamanlayıcı yokluğunu kilitler). Akış testleri `provider/mock/MockFlowTestSupport`: RANDOM_PORT, gerçek
  HTTP, `@Primary MutableClock` (created_at, imza ve doğrulama aynı saat), recovery interval 1h (görev elle `resendStale()`),
  `@MockitoSpyBean WebhookService` (TX proxy içi → `AopTestUtils.getUltimateTargetObject`) + `PaymentRepository`; `@BeforeEach`
  `dispatcher.pendingCount()==0` bekler + `clearInvocations` (önceki testin planlı gönderimi sayıma karışmasın).
- payment yol maskeleme: `SecurityConfig.requestPathMasker()` = `/internal/payments/{paymentId}` (common `RequestPathMasker`, Order
  Adım 0a; eski `MaskedRequestPaths` + `MaskedPathFilter` silindi). Hata handler'ları VE common internal filtre/entry point logları maskeli.
- payment ödeme oluşturma (Adım 3): sağlayıcı çağrısı ASLA DB transaction'ı içinde değil. `PaymentService` (TX'siz) →
  `PaymentTransactions.findOrCreate` (READ_COMMITTED, saveAndFlush; `uk_payments_order` ihlali → yeni TX'te bir kez tekrar) →
  sağlayıcı (hata/geçersiz referans → 503, ödeme referanssız kalır, tekrar istek yeniden dener) → `attachReference`
  (`findByIdForUpdate` kilidi; farklı referans zaten varsa mevcut korunur + değer içermeyen WARN, exception yok). Final ödeme için
  sağlayıcı çağrılmaz. TX metotları ilişkisiz (lazy alansız) entity döndürür; yanıt TX dışında `PaymentResponse.of`.
- payment testleri: `ApiTestSupport` (tam bağlam + MockMvc + Testcontainers, `@MockitoSpyBean` PaymentProvider ve PaymentRepository,
  her testten önce tablolar silinir). Repository spy'ında `callRealMethod` yerine yarış testi aynı sorguyu `EntityManager.find(...,
  PESSIMISTIC_WRITE)` ile yapar.
- payment şeması (V1 + V2): enum benzeri her kolon `utf8mb4_bin` (provider, status, currency, event_type); V2 ile sağlayıcı
  kimlikleri (`provider_payment_id`, `provider_event_id`) de `utf8mb4_bin` (harf duyarlı sağlayıcı kimlikleri çakışmasın). Para birimi
  `CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$', 'c'))`; CHAR(3)'e sığmayan değer (ör. 'TRYY') CHECK'e gelmeden 1406 "Data too long" →
  `DataIntegrityViolationException`. "failed ⇔ failure_code dolu" tek CHECK: `(status = 'failed') = (failure_code IS NOT NULL)`.
  payments/provider_events zaman kolonlarında DB default'u YOK (uygulama Clock ile yazar; cart Adım 6 kararı); outbox catalog'unkiyle
  birebir (`created_at` DEFAULT dahil; `PaymentSchemaConstraintsTest.outboxDdlIsIdenticalToCatalogs` catalog V1 dosyasıyla
  yorumsuz/boşluk-normalize metin karşılaştırır → catalog outbox'ı değişirse payment testi kırılır). Kart verisi kolonu yok
  (information_schema testi: card/pan/cvv/cvc/expiry geçen kolon yok).

## Ödeme alanı (payment-service, Adım 2)
- Enum kolonları: `entity/DbEnum` (`dbValue()`) + ortak `StrictDbEnumConverter<E>` taban sınıfı (her enum için üç satırlık
  `@Converter` alt sınıfı; `autoApply` yok, alanda `@Convert`). Okuma BİREBİR: 'Initiated', 'SUCCEEDED', 'Mock', 'PAYMENT.SUCCEEDED',
  '' → `IllegalArgumentException("Unknown <label> in database: '<v>'; expected one of [...]")`. null ↔ null.
  PaymentStatus initiated/succeeded/failed (`isFinal()`), PaymentProviderType mock/iyzico/paytr/stripe, ProviderEventType
  "payment.succeeded"/"payment.failed".
- ID AKIŞI (kullanıcı kararı "attach"): `Payment.initiate(...)` sağlayıcı referansı ALMAZ → save (id Hibernate `@UuidGenerator(VERSION_7)`,
  cart ile aynı) → `provider.create(new ProviderPaymentRequest(payment.getId(), amount, currency))` → `attachProviderReference(ref, clock)`.
  attach: ilk kez → true + updatedAt; aynı ref → false, damga yok (idempotent); başka ref → ISE; final durumda referanssız → ISE;
  boş / >128 → IAE.
- Durum geçişleri `TransitionResult` döner: APPLIED (durum + updatedAt değişir), ALREADY_IN_STATE (aynı final durum tekrar; zaman
  DEĞİŞMEZ), CONFLICTING_FINAL (succeeded ↔ failed; HİÇBİR şey değişmez). Webhook tekrarları/çelişen olaylar exception değil sonuçla
  ayrılır (Adım 6'da çağıran karar verir). `fail` failureCode'u (`^[A-Z][A-Z0-9_]{0,63}$`) durumdan ÖNCE doğrular (IAE).
- Tutar: null → NPE, ≤ 0 / 2'den fazla ondalık (10.001 yuvarlanmaz) / > 9999999999.99 (DECIMAL(12,2)) → IAE; `setScale(2, UNNECESSARY)`.
  `matches(userId, amount, currency)` tutarı `compareTo` ile (10.5 = 10.50), null-safe false.
- Setter YOK, protected no-arg ctor; değişmeyen kolonlar `updatable = false`. Zamanlar Clock'tan, MICROS (cart kalıbı).
- `ProviderEvent`: `@Immutable`, `record(provider, providerEventId, paymentId, eventType, clock)`; `paymentId` düz UUID (ilişki yok).
  Tekrar olay → `uk_provider_events_provider_event` → `DataIntegrityViolationException` (`DbConstraints.isViolated` ile ayırt edilir).
- Repository'ler: `PaymentRepository.findByOrderId`, `findByProviderTypeAndProviderPaymentId` (kilitsiz), `findByIdForUpdate` ve
  `findByProviderReferenceForUpdate` (PESSIMISTIC_WRITE); `ProviderEventRepository.existsByProviderTypeAndProviderEventId`.
- Sağlayıcı: `provider/PaymentProvider` (`type()`, `create(ProviderPaymentRequest) → ProviderPayment(providerPaymentId, redirectUrl?)`).
  TEK bean, `config/PaymentConfig` `app.payment.provider`'a göre switch ile kurar; mock dışı değer (iyzico/paytr/stripe) → ISE
  "Payment provider '<v>' is not supported yet; use 'mock'" → uygulama açılmaz; tanınmayan değer bağlamada düşer.
  `MockPaymentProvider` @Component DEĞİL; referans `"mock_" + UUID`, redirect null.
- `MockOutcomeRule(failCents)` saf sınıf (bean olarak da kayıtlı): tutarın kuruşu = failCents → `MockOutcome.failed("CARD_DECLINED")`,
  aksi `succeeded()`. `MockOutcome(failureCode)` record'u (`isSucceeded()`; static `succeeded()` record accessor'ıyla çakıştığı için
  boolean bileşen yok).

## order-service (Order Adım 1 iskeleti + Adım 2 domain + Adım 3a istemciler + Adım 4 checkout; port 8088, `order_db`)
- İskelet = payment Adım 1 kalıbı + cart'ın Resource Server güvenliği (JwtDecoderConfig + `JwtSubjects`: sub katı UUID, değilse
  `BadJwtException` → 401). Zincir TEK: `/api/**` authenticated, health (GET) + `/v3/api-docs/**` + Swagger + `/error` permitAll,
  `anyRequest().denyAll()` (kimliksiz 401 + Bearer challenge, token'lı 403). Internal API zinciri YOK (order internal uç sunmaz;
  istemci olarak `ORDER_INTERNAL_API_KEY` gönderir, Adım 3a → "Order istemcileri").
- Outbox: common kalıbı (`OutboxConfig` + `EventRoutingKeys` şimdilik BOŞ `OutboxRoutingKeys.of(Map.of())` → her olay tipi ISE;
  olaylar eklenince eşleme + sözleşme dosyası birlikte). `SchedulingConfig` yalnızca `app.outbox.enabled=true` iken.
- Readiness = readinessState + db (rabbit ve diğer servisler yok); yalnızca health expose.
- V1 şema kararları (`V1__init_order.sql`):
  - `orders.status` 'pending' | 'paid' | 'failed' — v1'de 'cancelled' YOK; ödeme zaman aşımı = `failed` + `failure_code 'ORDER_EXPIRED'`.
  - `orders.stock_state` 'requested' | 'held' | 'committed' | 'released'; 'requested' ile BAŞLAR (önce kayıt, sonra Catalog çağrısı).
    Tutarlılık: pending → requested|held; paid → held|committed; committed → yalnızca paid (failed: requested|held|released serbest).
  - Kullanıcı başına tek pending: VIRTUAL `active_pending_user_id` + `uk_orders_pending_user` (cart'ın active_user_id kalıbı).
    `uk_orders_payment` (payment_id NULL'ları çakışmaz); paid → payment_id zorunlu; `(status='failed') = (failure_code IS NOT NULL)`.
  - Tutarlar DECIMAL(12,2): subtotal > 0, 0 ≤ discount ≤ subtotal, total = subtotal − discount ve > 0. `coupon_code` VARCHAR(40) bin,
    CHECK YOK (v1 kuponsuz, biçim kupon özelliğinde).
  - `address_snapshot` JSON NOT NULL + `JSON_TYPE = 'OBJECT'` (alanlar Adım 4'te DTO'da doğrulanır). `currency` CHAR(3) bin + regex.
  - `failure_code` ve history `reason`: bin + `REGEXP_LIKE(x, '^[A-Z][A-Z0-9_]*$', 'c')`.
  - `order_items`: title_snapshot VARCHAR(300) (= catalog books.title), quantity 1–99 (= `ck_cart_items_quantity`), unit_price ≥ 0
    (= catalog), `line_total = unit_price * quantity`, UNIQUE (order_id, book_id) (FK indeksi de bu), FK RESTRICT.
  - `order_status_history`: from_status NULL yalnızca ilk satırda ve ilk satır yalnızca → 'pending'
    (`(from_status IS NULL) = (to_status = 'pending')`), `from_status <> to_status`; ix (order_id, created_at, id); FK RESTRICT.
  - İndeksler: orders (user_id, created_at, id), (status, created_at), (stock_state, updated_at); cart_id indeksi YOK.
  - Zamanlarda DB default yok (outbox.created_at hariç); status/stock_state/currency/discount_amount'ta default var (payment gibi).
- BİLİNEN ÇELİŞKİ: Catalog fiyat 0'a izin verir, `orders.subtotal/total_amount > 0` → tamamı ücretsiz sepet siparişi DB'de reddedilir
  (bilerek). KARAR (Adım 2): checkout sipariş yazılmadan ÖNCE `422 ORDER_TOTAL_ZERO` döner; domain kuralı `Order.place`'te
  (`OrderRuleViolation(ORDER_TOTAL_ZERO)`), HTTP eşlemesi Adım 4/5'te.

### Order domain'i (Order Adım 2; `entity/` + `repository/OrderRepository`)
- Payment kalıbı: `DbEnum` + package-private `StrictDbEnumConverter` (DB'de küçük harf, bilinmeyen değer IAE), `TransitionResult`
  (APPLIED / ALREADY_IN_STATE / CONFLICTING_FINAL; denenmemesi gereken durum = `IllegalStateException`), setter yok, protected
  no-arg ctor, `now(clock)` MICROS'a kırpılır, insert-only `OrderItem`/`OrderStatusHistory` `@Immutable`.
- `Order` aggregate root: `items`/`history` `@OneToMany(mappedBy, cascade = {PERSIST, MERGE})`, orphanRemoval YOK (FK RESTRICT);
  items `@OrderBy("id")` (UUID v7 = ekleme sırası), history `@OrderBy("createdAt, id")`; getter'lar değiştirilemez liste.
  `active_pending_user_id` EŞLENMEZ. status/stockState/currency/discount Java'da açıkça yazılır (INSERT'te gönderildiği SQL
  yakalamayla test edildi; DB default'una güvenilmez).
- `Order.place(userId, cartId, currency, List<OrderLine>, AddressSnapshot, Clock)` → `OrderRuleViolation(code)` sırası:
  INVALID_CURRENCY (`^[A-Z]{3}$`) → EMPTY_ORDER → DUPLICATE_BOOK → satır başına INVALID_QUANTITY (1–99) / INVALID_PRICE (negatif,
  > DECIMAL(12,2), 2'den fazla anlamlı ondalık; 10.000 kabul → 10.00, yuvarlama yok) → ORDER_TOTAL_ZERO. Boş/300'den uzun başlık =
  IAE (programlama hatası); toplam > 9999999999.99 = `OrderTotalTooLargeException` (IAE alt tipi, Adım 4; checkout 422
  ORDER_TOTAL_TOO_LARGE). lineTotal = unitPrice × quantity, subtotal =
  Σ, discount 0.00, total = subtotal; PENDING + REQUESTED; ilk history `null → pending` `ORDER_PLACED`.
- Geçiş tablosu (Clock parametre; APPLIED → updatedAt = clock; yalnızca markPaid/markFailed history yazar; APPLIED olmayan sonuç
  hiçbir alanı değiştirmez):
  - `markStockHeld`: REQUESTED → HELD APPLIED (status'tan bağımsız); HELD → ALREADY; COMMITTED/RELEASED → CONFLICTING.
  - `attachPayment(id)`: null → APPLIED; aynı id → ALREADY; başka id → CONFLICTING (değer değişmez).
  - `markPaid(id)`: PENDING+HELD → PAID APPLIED (paymentId null ise yazılır; başka id bağlıysa CONFLICTING); PENDING+REQUESTED →
    ISE; PAID aynı id → ALREADY, başka id → CONFLICTING; FAILED → CONFLICTING. History `pending → paid` `PAYMENT_SUCCEEDED`.
  - `markFailed(code)`: kod `^[A-Z][A-Z0-9_]*$` ve ≤ 64 değilse IAE (durumdan bağımsız); PENDING → FAILED APPLIED (history reason =
    kod, stok durumu korunur); FAILED → ALREADY (ilk kod kalır); PAID → CONFLICTING.
  - `markStockCommitted`: status ≠ PAID → CONFLICTING; HELD → COMMITTED APPLIED; COMMITTED → ALREADY; RELEASED → CONFLICTING;
    PAID+REQUESTED (DB CHECK'i dışlar) → ISE.
  - `markStockReleased`: status ≠ FAILED (PENDING dahil) → CONFLICTING; REQUESTED/HELD → RELEASED APPLIED; RELEASED → ALREADY;
    COMMITTED → CONFLICTING.
- `OrderReasons` (yalnızca sabit): ORDER_PLACED, PAYMENT_SUCCEEDED; failure kodları OUT_OF_STOCK, BOOK_NOT_AVAILABLE (Adım 4),
  CATALOG_UNAVAILABLE, PAYMENT_UNAVAILABLE, PAYMENT_REJECTED (Adım 4), CARD_DECLINED, PAYMENT_FAILED, ORDER_EXPIRED.
- `AddressSnapshot` record (user `addresses` alanları/uzunlukları: recipientName 120, phone 32, line1 200, line2? 200, district? 80,
  city 80, postalCode? 16, country `^[A-Z]{2}$`; label/id/userId/isDefault YOK). `toString()` = `AddressSnapshot[redacted]`, hata
  mesajları yalnızca alan adı. `OrderLine.toString()` = `OrderLine[redacted]`. Entity'lerde Lombok @ToString/@Data YOK (Object.toString).
- JSON eşlemesi = açık `AddressSnapshotConverter` (`@Convert` + `@JdbcTypeCode(SqlTypes.JSON)` String üzerinde; JSON_TYPE OBJECT
  doğrulandı, çift kodlama yok). Neden `@JdbcTypeCode` doğrudan değil: Hibernate format mapper'ı classpath'ten örtük seçer — şu an
  springdoc'un getirdiği Jackson 2 (`JacksonJsonFormatMapper`); Jackson 3'e geçerse bilinmeyen alan varsayılanı sessizce yok sayar;
  her iki durumda eksik alan sessizce null. Converter: Jackson 3 ağaç API'si, yazarken 8 alanın hepsi (null'lar dahil), okurken TAM
  8 alan (bilinmeyen/eksik/tekrar/metin olmayan → IAE, mesajda değer yok), değer kuralları record kurucusunda.
  KURAL (ileride): `AddressSnapshot`'a alan EKLENİRSE converter okurken yeni alanın YOKLUĞUNU kabul etmeli (eski satırlarda yok →
  null/varsayılan); "tam 8 alan" katılığı yalnızca bugünkü alanlar için. Eski alan kaldırılırsa da eski satırlardaki fazla alan tolere edilmeli.
- `OrderRepository`: `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)` + `@Query`, `innodb_lock_wait_timeout=5` → 5 sn sonra
  `PessimisticLockingFailureException`), `findByIdAndUserId` (`@EntityGraph("items")`, sahiplik kontrolü), `findPendingByUserId`
  (default metot → `findByUserIdAndStatus(userId, PENDING)`). Kalem/geçmiş için ayrı repository yok (aggregate üzerinden).
- Testler: `OrderSchemaConstraintsTest` (`@JdbcTest`, birden fazla kısıtı ihlal eden satırda `assertCheckViolation(..., a, b)` →
  MySQL'in bildirdiği ilk kısıt bunlardan biri), `StartupLogHygieneTest` (ayrı MySQL + üretilmiş kimlikler; `SpringApplicationBuilder`
  `.main(App.class)` olmadan "Started ForkedBooter" yazar).

### Order istemcileri (Order Adım 3a; `gateway/` + `client/`)
- İki katman (cart→catalog kalıbının genişletilmişi): `gateway/` domain'e bakan arayüzler + sealed sonuçlar (yalnızca java.* ve
  gateway tipleri; `GatewayTypesTest` kilitler), `client/` Feign arayüzleri + DTO'lar + `Feign*Gateway` implementasyonları.
  Gateway exception ATMAZ (yalnızca yerel programlama hatasında IAE: >50 kitap, tekrar kitap, boş rezervasyon, adet 1–100 dışı).
  - `CartGateway.snapshot(userId)` → `Snapshot(cartId, List<StockLine>)` | `Empty` (aktif sepet yok ya da boş) | `Unavailable`.
  - `CatalogGateway.lookup(ids)` → `Found(Map<UUID, CatalogBook>, Set<UUID> notFound)` (`CatalogBook.inStock` = satılabilir adet > 0,
    yeterlilik garantisi DEĞİL; `sellable(id)`) | `Unavailable`. Boş girdide çağrı yok. Lookup PUBLIC uç → anahtar GÖNDERİLMEZ.
  - `reserve(orderId, lines)` → `Reserved(expiresAt)` (201 ya da 200+held) | `NotHeld(COMMITTED|RELEASED)` (200 tekrar, stok tutulmuyor
    olabilir) | `Insufficient(bookIds)` (409 INSUFFICIENT_STOCK) | `NotSellable(bookIds)` (409 BOOK_NOT_AVAILABLE) | `Rejected(status,
    code)` (409 RESERVATION_MISMATCH, 400, 401) | `NotPerformed` | `Unknown`.
  - `commit(orderId)` → `Committed` (200; zaten committed da) | `AlreadyReleased` (409 RESERVATION_RELEASED → ödenmişse İADE telafisi)
    | `Rejected` (404 RESOURCE_NOT_FOUND, 400, 401) | `NotPerformed` | `Unknown`.
  - `release(orderId)` → `Released` (200; zaten released; 404 RESOURCE_NOT_FOUND = rezervasyon hiç yok, Catalog kodunda doğrulandı)
    | `AlreadyCommitted` (409 RESERVATION_COMMITTED) | `Rejected` (kodsuz/başka kodlu 404 = yanlış adres, 400, 401) | `NotPerformed` | `Unknown`.
  - `PaymentGateway.initiate(orderId, userId, amount, currency)` → `Initiated(paymentId, PaymentState)` (201 ya da 200 tekrar; durum
    succeeded/failed olabilir) | `Rejected` (409 PAYMENT_ORDER_MISMATCH/CONFLICT, 400, 401) | `NotPerformed` | `Unknown`
    (503 PAYMENT_PROVIDER_UNAVAILABLE dahil: ödeme satırı oluşmuş olabilir, aynı istek tamamlar).
- KURAL — NotPerformed / Unknown (Adım 5 telafisinin temeli):
  - `NotPerformed` = istek karşıya ULAŞMADI: bağlantı reddedildi, bağlantı zaman aşımı (1 sn), adres çözülemedi, circuit breaker açık.
    Yan etki YOK → telafi gerekmez, güvenle tekrar edilebilir.
  - `Unknown` = gönderildi, sonuç BİLİNMİYOR: okuma zaman aşımı (3 sn), yanıttan önce kopma, 5xx, 3xx, okunamayan gövde, zorunlu alan
    eksik, 2xx ama başka sipariş/beklenmeyen durum. İşlem karşıda UYGULANMIŞ OLABİLİR → aynı idempotent istekle tekrar ya da sorgu
    (Catalog `GET /internal/stock/reservations/{orderId}`, Payment `GET /internal/payments/{id}`; ikisi de 3a'da istemcide YOK).
  - Okumalar (snapshot, lookup) yan etkisiz → tek sonuç `Unavailable` (4xx dahil).
  - Ayrım `RemoteCalls.notConnected`: neden zincirinde `ConnectException`/`HttpConnectTimeoutException`/`UnknownHostException`/
    `NoRouteToHostException` → NotPerformed; diğer `RetryableException`/IO → Unknown.
- `RemoteCalls.execute(downstream, operation, call, validator, mapper)`: CB izni (`tryAcquirePermission`; yoksa ağ çağrısı yok →
  NotSent) → Feign çağrısı (`ResponseEntity<T>`) → boş gövde/`validator` ihlali `InvalidResponseException` → `CallOutcome`
  (Success | Problem(status, code, bookIds) | NotSent | Failed) → gateway'in `mapper`'ı. CB kaydı: 2xx geçerli ve 4xx = BAŞARI (karşı
  taraf ayakta; iş hatası devreyi açmaz), bağlantı/zaman aşımı/5xx/3xx/geçersiz 2xx = HATA.
- Circuit breaker: `DownstreamCircuitBreakers` — Resilience4j API'si DOĞRUDAN, instance başına (cart, catalog, payment), kurulum
  Adım 3b'den beri `common.resilience.CircuitBreakers` ile, ayarlar `app.circuit-breaker.*` (`common.resilience.CircuitBreakerProperties`,
  validated): COUNT_BASED pencere 20, en az 10 çağrı, %50 hata, 10 sn açık, yarı
  açıkta 3 çağrı, otomatik OPEN→HALF_OPEN kapalı, uygulama `Clock`'u (`CircuitBreakerConfig.Builder.clock`; test `MutableClock` ile ileri
  sarar). Durum değişimi WARN `Circuit breaker <ad> <eski> -> <yeni>`. Adım 4'ten beri pom'da yalnızca `resilience4j-circuitbreaker`
  (+ core; cart ile aynı): Spring Cloud CB starter'ı ve onu kapatan üç ayar kaldırıldı; CircuitBreakerFactory bean'i yok, Feign CB
  entegrasyonu kapalı (varsayılan), health/readiness'a girmez (stub'lar kapalı + devreler açıkken readiness UP testli).
- Feign: JDK HttpClient (`feign-java11`; `spring.cloud.openfeign.http2client.enabled=true`, `httpclient.http2.version=HTTP_1_1`,
  `connection-timeout 1000`, `follow-redirects false`). NEDEN: varsayılan HttpURLConnection gövdeli POST'a gelen 401'de yanıt yerine
  `HttpRetryException` verir (anahtar reddi teknik hata gibi görünürdü). `client.config.{default,cart,catalog,payment}`: connect 1000,
  read 3000, logger-level none. İstemci başına yapılandırma `InternalClientConfiguration` (BİLEREK `@Configuration` DEĞİL, yoksa global
  olurdu): `InternalApiKeyInterceptor` (yalnızca `/internal/` ile başlayan yola `X-Internal-Api-Key`; gelen isteğin başlıkları/token
  KOPYALANMAZ), `ProblemErrorDecoder` (gövdeden en fazla 16 KB, yalnızca `code` + `bookIds`; gövde loglanmaz, exception mesajı yalnızca
  durum + kod), `Logger.Level.NONE`, `Retryer.NEVER_RETRY`. Global `RequestInterceptor` bean'i YOK (testli). Accept başlığı
  `application/json, application/problem+json` (`ClientHeaders.ACCEPT_JSON`; `produces` yalnızca ilk değeri gönderir).
- DTO'lar: yalnızca okunan alanlar, `@JsonIgnoreProperties(ignoreUnknown = true)`, okunan her alan `@JsonProperty(required = true)` +
  kompakt kurucuda `requireNonNull` (nullable `cartId` hariç), kutulu `Integer`/`Boolean`; `toString()` maskeli (id/tutar/başlık yok).
  Sonuç record'larının da `toString()`'i id/tutar içermez.
- Anahtar: `InternalApiKey` (`ClientConfig` bean'i, `@Value("${app.clients.internal-api-key:}")` ← `ORDER_INTERNAL_API_KEY`); yok/boş/
  boşluk-kontrol-ASCII dışı → `IllegalStateException` (mesajda değer yok) → uygulama açılmaz. Adresler `app.clients.{cart,catalog,
  payment}.base-url` ← `ORDER_CART_URL`/`ORDER_CATALOG_URL`/`ORDER_PAYMENT_URL` (varsayılan localhost:8083/8082/8087).
- Log (gateway çağrısı başına TEK satır, `RemoteCalls`): `Remote call <istemci> <işlem> -> <SonuçTipi> (status=<HTTP|->,
  durationMs=<n>[, cause=<ExceptionSınıfı|CircuitOpen>])`. INFO başarı/iş sonucu; WARN teknik hata, Unavailable ve Rejected. Exception
  MESAJI yazılmaz (Feign mesajı URL'yi = sipariş id'sini içerir); id, tutar, kitap, gövde, anahtar, yol yok (testli).
- Testler: `support/StubServer` (WireMock standalone 3.13.1, sabit port, durdurup aynı portta yeniden açılabilir, bildirimleri sessiz),
  `ApiTestSupport` üç stub'ı `@DynamicPropertySource` ile bağlar (alt sınıf kendi DynamicPropertySource'unu eklemesin → bağlam
  bölünür) + `@Primary MutableClock` (`TestClockConfiguration`). `client/ClientTestSupport` her testten önce stub'ları açar/sıfırlar,
  CB'leri `reset()`, saati sıfırlar. Sözleşme: `ClientContractTest` (OpenAPI'den okunan DTO alanları var + required + tip/format,
  iç içe kayıtlar; stub'ın ALDIĞI gövdeler mini şema doğrulayıcıyla; durum enum'ları; Problem kodları; lookup public; mutasyon testleri).

### Checkout + sipariş okuma (Order Adım 4; `controller/OrderController`, `service/`)
- Uçlar (Bearer, USER sub = userId; `@CurrentUserId` cart kopyası): `POST /api/orders/checkout` → 201 + `Location: /api/orders/{id}` +
  `OrderResponse`; `GET /api/orders/{orderId}` → 200 yalnızca sahibine; başkasının/olmayan → 404 ORDER_NOT_FOUND (403 değil, varlık
  sızmaz); geçersiz UUID → 400 MALFORMED_REQUEST (common). Masker: `RequestPathMasker.of("/api/orders/checkout", "/api/orders/{orderId}")`
  (literal desen değişkenliyi yener; checkout `:orderId` olmaz).
- `CheckoutRequest(@NotNull @Valid AddressRequest address)`; `AddressRequest` = user-service `AddressRequest` kuralları (recipientName
  @NotBlank 120, phone @NotBlank 32 — biçim kuralı YOK, line1 @NotBlank 200, line2 200, district 80, city @NotBlank 80, postalCode 16,
  country `^[A-Z]{2}$`) + country `@NotNull` (user'da null → TR varsayılanı; burada zorunlu). Boş opsiyonel alan → null. Bilinmeyen
  alan YOK SAYILIR (tüm servislerin Jackson politikası; testli). `toString` maskeli.
- `OrderResponse`: id, status (küçük harf dbValue), failureCode?, currency, subtotal, discountAmount, totalAmount (BigDecimal, scale 2 →
  JSON sayı `149.90`, Cart ile aynı), items[bookId, title, quantity, unitPrice, lineTotal], address (8 alan), createdAt, updatedAt.
  stockState, paymentId, userId, cartId YOK. `OrderResponse.of(Order)` TX içinde (kalemler lazy).
- `CheckoutService` TX AÇMAZ; DB işleri `OrderTransactions`'ta (`@Transactional(isolation = READ_COMMITTED)`: insert = saveAndFlush,
  markStockHeld / markFailed / markStockReleased / attachPayment = `findByIdForUpdate` + geçiş + flush → `Transition(result, OrderResponse)`;
  salt okunur `findPendingOrderId`, `findOwned`). Dış çağrılar hiçbir TX içinde değil. Sıra:
  1. pending var → 409 ORDER_PENDING_EXISTS + `orderId` (Cart çağrılmaz).
  2. Cart snapshot: Empty → 422 CART_EMPTY; Unavailable → 503 CART_UNAVAILABLE.
  3. Catalog lookup: Unavailable → 503 CATALOG_UNAVAILABLE; bulunamadı/inStock=false → 409 BOOK_NOT_AVAILABLE (kitap id'si yanıtta
     yok); birden fazla para birimi → 422 MIXED_CURRENCY.
  4. `Order.place` (fiyat + başlık Catalog'dan, adet Cart'tan): OrderRuleViolation → 422 kod adıyla (ORDER_TOTAL_ZERO, INVALID_PRICE,
     INVALID_CURRENCY, INVALID_QUANTITY, DUPLICATE_BOOK, EMPTY_ORDER); `OrderTotalTooLargeException` → 422 ORDER_TOTAL_TOO_LARGE.
  5. TX1 insert (pending + requested). `uk_orders_pending_user` yarışı → kazananın id'si okunur → 409 + orderId. DB hatası (non-uk) →
     503 ORDER_UNAVAILABLE (Adım 5, 2.e).
  6. Catalog reserve: Reserved → TX2 markStockHeld; Insufficient → failed(OUT_OF_STOCK) 409 INSUFFICIENT_STOCK; NotSellable →
     failed(BOOK_NOT_AVAILABLE) 409 BOOK_NOT_AVAILABLE; NotHeld/Rejected (ERROR log) ve NotPerformed/Unknown → failed(CATALOG_UNAVAILABLE)
     503 CATALOG_UNAVAILABLE. Her durumda satır içi stok release çağrılır (Adım 5).
  7. Payment initiate(orderId, userId, total, currency): Initiated → TX3 attachPayment (dönen ödeme durumu yok sayılır, sonuç olaydan
     gelir); Unknown → pending + held, paymentId null, 201 + WARN; NotPerformed → failed(PAYMENT_UNAVAILABLE) 503; Rejected →
     failed(PAYMENT_REJECTED) 503 PAYMENT_UNAVAILABLE + ERROR. NotPerformed/Rejected durumunda satır içi stok release çağrılır (Adım 5).
  Kayıt sonrası hatalar `OrderProblemException(code, orderId)` → ProblemDetail'de `orderId`. Kayıt sonrası geçiş APPLIED değilse
  exception yok: WARN `Checkout <adım> was not applied (result=…, status=…)` + güncel sipariş döner.

### Telafi ve Yarıda Kesilme (Order Adım 5; `CheckoutService`, `OrderTransactions`)
- **Temel Kural:** Para işin içindeyse sipariş failed YAPILMAZ; stok işin içindeyse hemen release denenir.
- **Satır İçi Stok Release:**
  - Ne zaman: Kayıt sonrası siparişin `failed` yapıldığı her durumda (reserve: Insufficient, NotSellable, NotHeld, Rejected,
    NotPerformed, Unknown; payment: NotPerformed, Rejected).
  - Çağrı TX DIŞINDA: Catalog `release(orderId)` (tek tip çağrı; Insufficient'ta Catalog 404 RESOURCE_NOT_FOUND → Released döner).
  - Sonuca göre:
    - `Released` → TX (FOR UPDATE): `markStockReleased(orderId)` (stock_state = `released`). APPLIED değilse WARN, exception yok.
    - `AlreadyCommitted` → ERROR logu (olmaması gereken durum), stok durumu değişmez.
    - `NotPerformed` / `Unknown` / `Rejected` → stok requested/held kalır, WARN (Adım 6 StockSyncJob / Adım 8 toplar).
  - HTTP yanıtı release sonucundan ETKİLENMEZ (Adım 4'teki kodlar aynen döner).
- **Yarıda Kesilme Senaryoları (OrderTransactions beklenmeyen DB hatası / lock timeout vb.):**
  - **a. Rezervasyon başarılı ama `markStockHeld` TX'i başarısız:**
    - best-effort `markFailed(CHECKOUT_INTERRUPTED)` (o da başarısız olabilir).
    - best-effort Catalog `release(orderId)`; `markStockReleased` yalnızca markFailed başarılıysa çağrılır.
    - 503 `CHECKOUT_INTERRUPTED` + `orderId`.
    - DB'de kalan: markFailed başarılıysa `failed + released`; markFailed da çökerse `pending + requested` (Adım 8 toplar).
  - **b. Payment Initiated ama `attachPayment` TX'i başarısız (`uk_orders_payment` ihlali dahil):**
    - Sipariş failed YAPILMAZ (para işin içinde).
    - ERROR logu (`uk_orders_payment` ihlali) / WARN (diğer DB hataları), id'siz/tutarsız.
    - 201 Created + siparişin bilinen durumu (`pending`).
    - Sipariş DB'den okunamıyorsa (DB tamamen göçtüyse): 503 `CHECKOUT_INTERRUPTED` + `orderId` (raporla).
  - **c. Reserve başarısızlığından sonra `markFailed` TX'i başarısız:**
    - Catalog release yine denenir (stok durumu DB'ye yazılamaz).
    - Yanıt Adım 4'teki koddur (409/503 + `orderId`).
    - DB'de sipariş `pending + requested` kalır (Adım 8 toplar).
  - **d. Payment NotPerformed/Rejected sonrası `markFailed` başarısız:**
    - Release DENENMEZ (sipariş pending göründüğü için Adım 8 uzlaştırması Payment'a sorar).
    - Yanıt Adım 4'teki kod (503 PAYMENT_UNAVAILABLE + `orderId`).
    - DB'de sipariş `pending + held` kalır (Adım 8 toplar).
  - **e. TX1 (insert) uk dışı bir DB hatası:**
    - Hiçbir dış çağrı yapılmadan 503 `ORDER_UNAVAILABLE` (sipariş yok, orderId yok).
- **Log Hijyeni:** Tüm telafi yollarında tek satır log: `Stock compensation -> <ResultName> (durationMs=n)`. id, tutar, kitap id'si, adres ASLA yer almaz.
- **Hata Kodları ve Neden Sabitleri:**
  - `OrderReasons.CHECKOUT_INTERRUPTED = "CHECKOUT_INTERRUPTED"` (failure_code).
  - `OrderErrorCode.ORDER_UNAVAILABLE` (503 SERVICE_UNAVAILABLE, WARN).
  - `OrderErrorCode.CHECKOUT_INTERRUPTED` (503 SERVICE_UNAVAILABLE, WARN).

### Bekleyen sipariş uzlaştırma (`PendingReconciliationJob`, Order Adım 8 — UYGULANDI)
- Ayarlar `app.pending-reconcile.*`: enabled true, interval 30s, initial-delay 15s, min-age 60s, expire-after 10m, batch 50.
- Seçim (kilitsiz): `status = 'pending' AND created_at < now − min-age`, `created_at ASC`, `LIMIT batch`; `ix_orders_status_created`
  (EXPLAIN testi, filesort yok). Payment çağrısı TX dışında; her geçiş `findByIdForUpdate` ile yeniden okur, `TransitionResult` ile
  idempotent (tüketiciyle aynı siparişe eşzamanlı dokunabilir; testle kanıtlı: tek paid, tek OrderPaid, tek commit).
- Payment isteği checkout'takiyle aynı (orderId, userId, totalAmount, currency); Payment orderId'ye idempotent → mevcut ödemenin durumu döner.
- Karar tablosu ("eski" = yaş ≥ expire-after):

| Stok | Payment sonucu | Yaş | Aksiyon |
|---|---|---|---|
| requested | (çağrı yok) | ≥ min-age | failed `CHECKOUT_INTERRUPTED` + OrderFailed + release dispatch |
| held | Initiated(succeeded) | farketmez | tüketiciyle AYNI geçiş (`OrderTransactions.succeed`): paid + OrderPaid + CartCheckedOut + commit dispatch |
| held | Initiated(failed) | farketmez | failed (Payment failureCode, geçersiz/yoksa `PAYMENT_FAILED`) + OrderFailed + release dispatch |
| held | Initiated(initiated) | genç | paymentId null ise bağla (ATTACHED), bekle |
| held | Initiated(initiated) | eski | (gerekirse bağla) + failed `ORDER_EXPIRED` + OrderFailed + release dispatch |
| held | NotPerformed / Unknown / Rejected (ERROR log) | genç | değişiklik yok (WAITING) |
| held | NotPerformed / Unknown / Rejected | eski | failed `ORDER_EXPIRED` + OrderFailed + release dispatch |
| held | (circuit açık görüldü, çağrı yok) | genç / eski | WAITING / `ORDER_EXPIRED` |

- İlk NotPerformed'dan sonra o tur Payment çağrılmaz; yalnız requested ve süresi dolanlar işlenir. Kilit altında sipariş artık pending
  değilse veya stok seçimdekinden farklıysa dokunulmaz (SKIPPED). Hata (ör. kilit zaman aşımı) → WARN, sonraki tur.
- Tur sonu INFO `Pending reconcile round completed: processed=N, counts=[...]` yalnız WAITING dışı sonuç varsa; id/tutar yok.
- Expire sonrası Payment başarısı gelirse (tüketici veya görev): `recordLatePayment` → `late_payment_at` (sipariş failed kalır,
  ERROR `LATE_PAYMENT_SUCCESS`; başka ödeme bağlıysa ayrıca ERROR `PAYMENT_ID_CONFLICT`, bağlı ödeme değişmez). v1'de iade YOK.
  İade listesi şablonu:
  ```sql
  SELECT id, user_id, payment_id, total_amount, currency, failure_code, late_payment_at
  FROM orders
  WHERE status = 'failed' AND late_payment_at IS NOT NULL
  ORDER BY late_payment_at DESC;
  ```
- Tek instance varsayımı (StockSyncJob gibi; çoklu instance'ta distributed lock gerekir).

### Order kalıntıları → hangi görev kapatır

| Kalıntı | Nasıl oluşur | Kapatan |
|---|---|---|
| pending + requested | checkout rezervasyon sonucu yazılamadı (kesinti, DB hatası) | PendingReconciliationJob → failed CHECKOUT_INTERRUPTED + release |
| pending + held (ödemesiz/ödemeli) | Payment Unknown, attachPayment hatası, ödeme olayı kayboldu | PendingReconciliationJob → Payment'a sor; sonuç yoksa 10 dk'da ORDER_EXPIRED |
| paid + held | commit dispatch atlandı / NotPerformed / Unknown | StockSyncJob → commit (AlreadyReleased → lost) |
| failed + requested / held | release dispatch atlandı / NotPerformed / Unknown | StockSyncJob → release |
| paid + lost | rezervasyon commit'ten önce doldu | kapanmaz; admin lost listesi |
| failed + late_payment_at | süre dolduktan / başarısızlıktan sonra ödeme başarılı | kapanmaz; admin iade listesi (v1 iade yok) |

### RabbitMQ consumer kalıbı + Payment sonucu (Order Adım 6a)
- Consumer kendi durable kuyruğunu ve binding'lerini tanımlar; producer yalnızca ortak durable topic exchange'i tanımlar. Ortak saf
  kurucu `common.amqp.DeadLetterQueueTopology` = durable work queue (`x-dead-letter-exchange`, `x-dead-letter-routing-key`) + durable
  direct DLX + durable DLQ + DLQ binding + kaynak topic binding'leri. Auto-config/@Configuration YOK; Order, Cart ve ileride
  Notifications kendi config'inde `Declarables` bean'i yapar.
- Order topolojisi: `order.payment-results` → `kitapsepeti.events` üzerinde `payment.succeeded`, `payment.failed`;
  `kitapsepeti.dlx` (direct) → `order.payment-results.dlq`, key `order.payment-results.dead`.
- Listener container (`PaymentResultsConsumerConfig`): prefetch 10, tek consumer; stateless toplam 3 deneme (`maxRetries=2`),
  exponential 1s/2s, 4s tavan; retry tükenince `PaymentResultDeadLetterRecoverer` requeue'suz reject (queue DLX'e yollar).
  `PoisonMessageException` cause zincirinde görülürse retry policy false: doğrudan recoverer/DLQ. Framework'ün stack trace'li varsayılan
  error logu kapalı; listener/recoverer yalnızca tip+sonuç/reason+süre içeren güvenli satırlar yazar.
  DİKKAT: container error handler'ı FIRLATMAMALI (no-op). Fırlatırsa `AbstractMessageListenerContainer.invokeErrorHandler` ERROR +
  stack trace yazar; container özgün istisnayı zaten yeniden fırlatır, `defaultRequeueRejected=false` + recoverer'ın
  `AmqpRejectAndDontRequeueException`'ı ile mesaj requeue'suz DLX'e gider. Cart ve Order (Adım 8'den beri) böyle.
- Cart topolojisi (Cart Adım 7): `cart.checkouts` ← `cart.checked-out`; `kitapsepeti.dlx` → `cart.checkouts.dlq`, key `cart.checkouts.dead`.
  Ortak exchange tanımı common `amqp.EventsExchange.create(name)` (outbox ve consumer-only servisler aynı tanımı kullanır).
- Poison: JSON parse, bilinmeyen AMQP type, eventVersion≠1, zorunlu/geçersiz alan, order yok, amount/currency uyuşmazlığı.
  Teknik DB/kilit hataları retry. Domain final-state çelişkileri poison DEĞİL: ERROR + ack, sipariş değişmez.
- `OrderTransactions` ödeme sonucunu READ_COMMITTED + `findByIdForUpdate` ile uygular; amount `BigDecimal.compareTo`, currency birebir.
  Succeeded APPLIED → paid + aynı TX'te `OrderPaid` ve `CartCheckedOut`; Failed APPLIED → failed + aynı TX'te yalnız `OrderFailed`.
  ALREADY → ack/yazma yok. Farklı paymentId → PAYMENT_ID_CONFLICT ERROR+ack. Failed siparişe geç başarı →
  LATE_PAYMENT_SUCCESS ERROR+ack. Adım 6a stok HTTP çağrısı YAPMAZ; paid+held ve failed+held/requested kalabilir (6b).
- `OrderTransactions.markFailed` APPLIED olduğunda her kaynak için aynı TX'te `OrderFailed` yazar; checkout yolları otomatik kapsanır.
  failed yapılamayan interrupted yol pending kaldığı için olay üretmez. Başarısız ödemede CartCheckedOut YOK, sepet aktif kalır.
- Order outbox: aggregate_type `order`, aggregate_id orderId. Event type/routing:
  `OrderPaid`/`order.paid`, `OrderFailed`/`order.failed`, `CartCheckedOut`/`cart.checked-out`.
  Payload eventId + eventVersion 1; occurredAt order geçişinin Clock zamanı; para metin. Record alan sırası testle kilitli.
- KABUL (uygulama 6b): Catalog commit `AlreadyReleased` → yalnız paid ile geçerli V2 `stock_state='lost'`; tekrar commit yok,
  ERROR, admin listesi DB sorgusundan. V1 bu adımda değişmedi.

- Hata kodları `exception/OrderErrorCode` (ErrorCode): 404 ORDER_NOT_FOUND; 409 ORDER_PENDING_EXISTS, BOOK_NOT_AVAILABLE,
  INSUFFICIENT_STOCK; 422 CART_EMPTY, MIXED_CURRENCY, ORDER_TOTAL_ZERO, ORDER_TOTAL_TOO_LARGE, EMPTY_ORDER, DUPLICATE_BOOK,
  INVALID_QUANTITY, INVALID_PRICE, INVALID_CURRENCY; 503 CART_UNAVAILABLE, CATALOG_UNAVAILABLE, PAYMENT_UNAVAILABLE, ORDER_UNAVAILABLE, CHECKOUT_INTERRUPTED (WARN). Diğerleri INFO.
  `GlobalExceptionHandler.addProperties` → `orderId`; `classify`: `uk_orders_pending_user` → ORDER_PENDING_EXISTS (başka kısıt → common 409 CONFLICT).
- Log: checkout başına TEK INFO `Checkout -> <SONUÇ> (durationMs=n)` (SONUÇ = ORDER_PLACED | hata kodu | INTERNAL_ERROR; doğrulama
  hatası servise ulaşmaz → özet yok). id, tutar, adres, kitap listesi hiçbir satırda yok (testli: happy + hata yolları).
- Bilinen kenar: Payment başka siparişin paymentId'sini dönerse (sözleşme ihlali) attachPayment `uk_orders_payment` → 409 CONFLICT
  (orderId'siz), sipariş pending + held + paymentId null kalır (Adım 8 kapsar). Testte her sipariş için ayrı paymentId stub'lanmalı.
- Test tabanı `controller/CheckoutTestSupport` (ApiTestSupport alt sınıfı): test başına rastgele kullanıcı (pending kuralı testler
  arası çakışmaz), stub'lar sıfır + CB reset; WireMock response templating (`{{jsonPath request.body '$.orderId'}}` +
  `withTransformers("response-template")`, yerel transformer varsayılan açık) ile yanıt istekteki siparişe ait. Reserve NotPerformed:
  gecikmeli lookup sırasında catalog CB `transitionToForcedOpenState()`. Yarış: gecikmeli lookup (iki istek de pending kontrolünü geçer).

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
  sepeti kilitler); ikinci ihlal ve diğer kısıtlar handler'a (409 CONFLICT, log `constraint=…`). RC'de de aynı yarış InnoDB deadlock'u
  (1213, `CannotAcquireLockException`) verebilir (unique duplicate kontrolü + delete-marked kayıtlar → supremum next-key S kilidi +
  insert-intention X); bu da aynı tek yeniden denemeye girer (Cart Adım 7). 1205 lock wait timeout DENENMEZ. Yeni sepet `saveAndFlush` ile hemen
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
- Yol maskeleme: `SecurityConfig.requestPathMasker()` = `/api/cart/items/{bookId}` (common `RequestPathMasker`, Order Adım 0a; eski
  `MaskedRequestPaths` ve handler override'ları/sarmalayıcıları silindi). Common handler'lar, security handler'ları ve assembler'ın
  CATALOG_UNAVAILABLE WARN'ı (`ProblemDetails.log(..., pathMasker)`) aynı bean'i kullanır → `/api/cart/items/:bookId`. Path'te bozuk
  UUID → 400 MALFORMED_REQUEST (TypeMismatch, common'ın varsayılan dalı; mevcut davranış).
- Internal snapshot (`POST /internal/cart/snapshot` {userId}, `controller/internal/InternalCartController`, DTO'lar `dto/internal/`):
  `service/CartSnapshotService` `@Transactional(readOnly = true)` → `findByUserIdAndStatus(userId, ACTIVE)` = TEK SQL (EntityGraph
  join, `for update` yok). Kilit yok, sepet açılmaz, damga yok, Catalog yok. Yanıt `{cartId, updatedAt, items[{bookId, quantity,
  unitPriceSnapshot (scale 2, sayı), currency, title}]}`; aktif sepet yoksa (kapanmış sepetler dahil) cartId/updatedAt null + items [];
  aktif sepet boşsa cartId dolu + items []. userId ve coverUrl yanıtta yok. Gövde okunamazsa (boş, bozuk JSON, UUID değil) 400
  MALFORMED_REQUEST, userId yok/null 400 VALIDATION_FAILED; gönderilen değer yanıtta yok.
- Sepet kapatma (Cart Adım 7, `messaging/CartCheckedOutListener` + `CartTransactions.checkOut`): Order'ın `CartCheckedOut` v1 olayı
  `cart.checkouts` kuyruğundan (Order 6a consumer kalıbı, aşağıdaki bölüm). TX: READ_COMMITTED, `findByIdForUpdate(cartId)` (id ile
  `PESSIMISTIC_WRITE`). Kurallar: sepet yok → poison CART_NOT_FOUND; userId farklı → poison CART_OWNER_MISMATCH (ERROR); active →
  checked_out (updated_at Clock); checked_out → ack DEBUG; abandoned → ack WARN, değişmez. `PermanentCheckoutException` (servis) →
  listener'da `PoisonMessageException`. Satır silinmez; yeni ekleme yeni aktif sepet açar (ayrı TX → flush tuzağı yok).
  Parser (`CartCheckedOutMessageParser`) Order parser'ının kopyası: AMQP type `CartCheckedOut`, eventVersion 1, zorunlu
  eventId/cartId/userId/orderId/occurredAt, bilinmeyen alan yok sayılır. v1 sınırı: sipariş pending iken eklenen ürün sepetle birlikte kapanır.
  Sözleşme testi Order'ın record kaynağını test anında derler (Order koduna bağımlılık/kopya yok).
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
- Spring Cloud kullanan servis (cart, order): Spring Cloud health'e `refreshScope` ve `discoveryComposite` katkılarını ekler → kapatılır
  (`management.health.refresh.enabled=false`, `spring.cloud.discovery.client.composite-indicator.enabled=false`,
  `...health-indicator.enabled=false`). OpenFeign kendi health indicator'ını EKLEMEZ. Kalan katkılar yalnızca yerel: db, diskSpace,
  livenessState, readinessState, ping, ssl (`ActuatorHealthTest.healthContributorsAreLocalOnly` kilitler). Catalog kapalıyken health UP
  ve Catalog'a istek atmaz (testli).

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

## Stok Kesinleştirme / Serbest Bırakma ve Kurtarma (Order Adım 6b)
- **İki Aşamalı Dağıtım (Dispatcher Pattern):**
  - Ödeme sonucu TX'inde sipariş durumu `paid` veya `failed` yapıldığında ve geçiş `APPLIED` olduğunda Spring `ApplicationEventPublisher` ile `StockCommitReadyEvent` veya `StockReleaseReadyEvent` yayımlanır.
  - `StockDispatcher` bu olayları `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` ile yakalar. TX rollback olursa iş bırakılmaz.
  - Olaylar sınırlı bir `ThreadPoolTaskExecutor` (2 core, 4 max, 50 queue capacity, `DiscardPolicy`) havuzuna devredilir. Kuyruk dolduğunda istisna fırlatılmaz, `WARN` loglanır ve kurtarma görevi (`StockSyncJob`) tarafından toplanır.
  - `ALREADY_IN_STATE` veya `CONFLICTING` durumlarda event fırlatılmaz, dış çağrı yapılmaz.
- **Transaction Dışı Catalog Çağrısı ve Kilitli Durum Güncellemesi (`StockCoordinator`):**
  - Catalog commit/release çağrısı DB transaction'ı DIŞINDA yapılır.
  - Çağrı tamamlandıktan sonra sipariş `SELECT ... FOR UPDATE` ile okunur ve durum geçişi uygulanır.
  - **Sonuç Eşleme Kuralları:**
    - Commit:
      - `Committed` → `markStockCommitted` (stock_state = `committed`)
      - `AlreadyReleased` (409) → `markStockLost` + `ERROR STOCK_COMMIT_LOST` (tekrar deneme yok)
      - `Rejected (404 RESOURCE_NOT_FOUND)` → `markStockLost` + `ERROR STOCK_COMMIT_LOST` (rezervasyon yok)
      - Diğer `Rejected` (401 vb.) → durum değişmez (`held` kalır), `ERROR` (yapılandırma hatası; job toparlar)
      - `NotPerformed` / `Unknown` (500 vb.) → durum değişmez (`held` kalır), `WARN` (job toparlar)
    - Release:
      - `Released` → `markStockReleased` (stock_state = `released`)
      - `AlreadyCommitted` (409) → `ERROR`, değişiklik yok
      - `NotPerformed` / `Unknown` / `Rejected` → değişiklik yok, `WARN`
  - Geçiş `APPLIED` değilse (sipariş arada başka yoldan değişmişse) `DEBUG`/`WARN`, istisna fırlatılmaz.
- **Kurtarma Görevi (`StockSyncJob`):**
  - `@Scheduled(fixedDelayString = "${app.stock-sync.interval:30s}", initialDelayString = "${app.stock-sync.initial-delay:10s}")`
  - Ayarlar: `app.stock-sync.enabled` (varsayılan true), `interval: 30s`, `min-age: 10s`, `batch: 50`.
  - Seçim: `(status = 'paid' AND stock_state = 'held') OR (status = 'failed' AND stock_state IN ('requested', 'held'))`, `updated_at < now - minAge`, `updated_at ASC`, `LIMIT batch`.
  - İndeks: `(stock_state, updated_at)` bileşik indeksini kullandığı EXPLAIN ile doğrulanmıştır.
  - Seçim kilitsizdir; her aday için Catalog çağrısı TX dışında yapılır, ardından kilitli durum geçişi yapılır.
  - Devre kesici açıkken (`NotPerformed`) tur erken bitirilir.
  - Tur sonunda iş yapıldıysa kimlik ve tutar içermeyen tek bir `INFO` özet satırı loglanır.
  - **Tek Instance Varsayımı:** `StockSyncJob` distributed lock (ShedLock vb.) içermez; v1 mimarisinde tek instance çalıştığı varsayılır. Çoklu instance'a geçilirse distributed lock eklenmelidir.
- **Admin Lost Sipariş Sorgusu (Şablon):**
  Kaybolan stoklu siparişler için açık uç yoktur; DB üzerinden aşağıdaki şablon sorgu ile incelenir:
  ```sql
  SELECT id, user_id, status, stock_state, total_amount, currency, created_at, updated_at
  FROM orders
  WHERE stock_state = 'lost'
  ORDER BY updated_at DESC;
  ```
- **Veri Değiştirme Kuralı:**
  - Catalog ve User verisi YALNIZCA ilgili servisin API'siyle değiştirilir; doğrudan SQL ile yazma KESİNLİKLE YOKTUR (root yalnızca okuma amaçlıdır). Admin token yoksa DUR ve kullanıcıya sor.
  - Raporlarda id, başlık, token, tutar ASLA YAZILMAZ.

