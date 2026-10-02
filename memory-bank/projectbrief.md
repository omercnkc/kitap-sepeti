# Project Brief

KitapSepeti: Spring Boot tabanlı, çok servisli (mikroservis) bir kitap satış uygulaması.

- Repo: https://github.com/omercnkc/kitap-sepeti (branch: `main`)
- Kök dizin Maven multi-module projesidir; her servis ayrı bir modül/klasördür.
- Mevcut servisler: `user-service` (8081), `catalog-service` (8082, tamamlandı); ortak kütüphane modülü `common`.
- Geliştiriliyor: `cart-service` (8083; Adım 7: sepeti görüntüleme, kitap ekleme, adet değiştirme, satır silme ve sepeti boşaltma uçları çalışıyor).
