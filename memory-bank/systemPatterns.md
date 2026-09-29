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

## Test
- Repository testleri: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` +
  `@Import(TestcontainersConfiguration.class)`; Flyway test container'ında çalışır.
- Boot 4 paketleri: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`,
  `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`; Testcontainers 2.x:
  `org.testcontainers.mysql.MySQLContainer`, artifact `testcontainers-mysql`.

## Yeni servis eklerken (KULLANICI KURALI)
Kullanıcı, her yeni serviste kök `pom.xml`'in kontrol edilip gerekiyorsa
güncellenmesini ve her değişikliğin NE ve NEDEN olduğunun açıklanmasını istiyor.
Kök POM'un başındaki "YENİ SERVİS EKLERKEN KONTROL LİSTESİ" takip edilir:
1. `<modules>`'a ekle.
2. Servis POM'unun `<parent>`'ını kök POM yap, miras alınanları sil.
3. Yeni kütüphane ailesi varsa versiyonu `<properties>`'e, BOM'u `<dependencyManagement>`'a.
4. Tüm servislerde ortak olan bağımlılığı kök `<dependencies>`'e taşı.
