# Progress

## Çalışanlar
- GitHub reposu bağlı, ilk commit push'landı.
- Multi-module Maven yapısı: kök parent POM + `user-service` modülü, build başarılı.

## Yapılacaklar
- user-service iş mantığı (entity, repository, controller, güvenlik).
- Veritabanı yapılandırması ve Flyway migration'ları.
- Diğer servisler (katalog, sepet, sipariş vb. — henüz kararlaştırılmadı).

## Bilinen sorunlar
- DB yapılandırılmadığı için `contextLoads` testi MySQL olmadan başarısız olur.
