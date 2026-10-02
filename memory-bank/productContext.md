# Product Context

Kullanıcıların kitap arayıp sepete ekleyip satın alabildiği bir e-ticaret uygulaması.
Servisler bağımsız geliştirilip deploy edilebilir olacak şekilde ayrılıyor.

- `user-service`: kullanıcı yönetimi (kayıt, kimlik doğrulama, profil, adresler).
- `catalog-service`: kitap kataloğu — public okuma, admin yönetimi, stok rezervasyonu (servisler arası), olaylar.
- `common`: servislerin ortak hata/güvenlik altyapısı (kütüphane, çalıştırılamaz).
- `cart-service`: giriş yapmış kullanıcının sepeti (misafir sepeti yok). Kullanıcı başına bir aktif sepet; satırlarda eklendiği andaki
  fiyat/başlık snapshot'ı, canlı fiyat/stok Catalog'dan (OpenFeign) okunur. Catalog kapalıyken sepet yine görüntülenir (snapshot
  fiyatlarıyla, "doğrulanamadı" işaretli); ekleme ise yapılamaz (503). Olay yayınlamaz.
