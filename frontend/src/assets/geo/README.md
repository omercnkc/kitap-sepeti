# 🇹🇷 Türkiye İl, İlçe, Mahalle ve Cadde/Sokak Verileri

Türkiye'de bulunan **il, ilçe, mahalle ve cadde/sokak isimlerini** JSON formatında içeren açık veri seti.

Bu repository, özellikle **adres seçimi, konum girişi, kargo/takip, teslimat, harita ve konum tabanlı uygulamalar** geliştiren projelerde kullanılmak üzere hazırlanmıştır.

> Veri kaynağı olarak **T.C. İçişleri Bakanlığı Nüfus ve Vatandaşlık İşleri Genel Müdürlüğü (NVİ) Adres Kayıt Sistemi** referans alınmıştır.

---

## 📦 Veri İçeriği

Repository'deki veriler farklı JSON dosyalarına ayrılmıştır:

| Dosya               | İçerik                              |
| ------------------- | ----------------------------------- |
| `iller.json`        | Türkiye'deki iller                  |
| `ilceler.json`      | İllere bağlı ilçeler                |
| `mahalleler-1.json` | Mahalle / adres verileri – 1. bölüm |
| `mahalleler-2.json` | Mahalle / adres verileri – 2. bölüm |
| `mahalleler-3.json` | Mahalle / adres verileri – 3. bölüm |
| `mahalleler-4.json` | Mahalle / adres verileri – 4. bölüm |

Bu yapı, büyük JSON verilerinin tek bir dosyada tutulması yerine daha yönetilebilir parçalara ayrılmasını sağlar.

---

## 🗺️ Adres Hiyerarşisi

Veriler temel olarak aşağıdaki adres hiyerarşisini takip eder:

```text
Türkiye
│
├── İl
│   │
│   ├── İlçe
│   │   │
│   │   ├── Mahalle
│   │   │   │
│   │   │   └── Cadde / Sokak
│   │   │
│   │   └── ...
│   │
│   └── ...
│
└── ...
```

Bu yapı sayesinde kullanıcıya kademeli bir adres seçim deneyimi sunulabilir:

**İl → İlçe → Mahalle → Cadde/Sokak**

---

## 📄 JSON Örneği

### İl

`iller.json` içerisinde iller, ID ve isim bilgileriyle tutulmaktadır:

```json
{
  "sehir_id": "34",
  "sehir_adi": "İSTANBUL"
}
```

### İlçe

İlçe verileri `ilceler.json` içerisinde tutulmaktadır.

```json
{
  "il_id": "...",
  "ilce_id": "...",
  "ilce_adi": "..."
}
```

> JSON dosyalarındaki gerçek alan adları ve veri yapısı için ilgili dosyaları doğrudan inceleyebilirsiniz.

---

## 🚀 Kullanım Alanları

Bu veri seti özellikle aşağıdaki projelerde kullanılabilir:

* 📍 Adres seçimi
* 🏠 Konum / adres girişi
* 🔎 Adres arama ve autocomplete
* 🚚 Kargo takip uygulamaları
* 📦 Kargo ve teslimat sistemleri
* 🛵 Kurye uygulamaları
* 🗺️ Harita tabanlı uygulamalar
* 🏢 Saha servis uygulamaları
* 🛒 E-ticaret adres formları
* 📱 Mobil uygulamalardaki adres seçim ekranları
* 🌐 Web uygulamalarındaki konum formları

---

## 💻 Kullanım

JSON dosyalarını doğrudan projenize dahil ederek kullanabilirsiniz.

Örneğin JavaScript / TypeScript projesinde:

```javascript
import cities from "./iller.json";

console.log(cities);
```

Flutter gibi mobil projelerde ise JSON dosyaları `assets` olarak eklenerek kullanılabilir.

Örneğin:

```text
İl seçimi
    ↓
İlçe seçimi
    ↓
Mahalle seçimi
    ↓
Cadde / Sokak seçimi
```

---

## 🔗 Dosyalara Hızlı Erişim

* [`iller.json`](./iller.json)
* [`ilceler.json`](./ilceler.json)
* [`mahalleler-1.json`](./mahalleler-1.json)
* [`mahalleler-2.json`](./mahalleler-2.json)
* [`mahalleler-3.json`](./mahalleler-3.json)
* [`mahalleler-4.json`](./mahalleler-4.json)

---

## 🎯 Projenin Amacı

Bu repository'yi, kendi projemde **konum/adres girişi için gerekli verilere ihtiyaç duyduğumda** oluşturdum.

Türkiye'deki il, ilçe, mahalle ve cadde/sokak verilerine ihtiyaç duyan diğer geliştiricilerin de aynı verilerden faydalanabilmesi amacıyla açık olarak paylaşılmıştır.

Umarım konum ve adres işlemleriyle uğraşan geliştiricilerin işini kolaylaştırır.

---

## 📚 Veri Kaynağı

Veri kaynağı olarak:

**T.C. İçişleri Bakanlığı
Nüfus ve Vatandaşlık İşleri Genel Müdürlüğü (NVİ)
Adres Kayıt Sistemi**

referans alınmıştır.

> Veri setinin güncelliği ve kullanım koşulları için ilgili kurumların resmi kaynaklarının kontrol edilmesi önerilir.

---

## 🤝 Katkıda Bulunma

Eksik, hatalı veya güncel olmayan bir veri tespit ederseniz:

* **Issue** açabilir,
* **Pull Request** gönderebilir,
* Veri güncellemelerine katkıda bulunabilirsiniz.

---

## ⭐ Destek Ol

Bu repository işinize yaradıysa **Star** vererek projeyi destekleyebilirsiniz.

Adres ve konum verilerine ihtiyaç duyan başka geliştiricilerle paylaşmanız da projeye katkı sağlar.

---

### 📌 Not

Bu repository bir **API değildir**.

Veriler doğrudan JSON dosyaları olarak sunulmaktadır. Uygulamanızda kullanmak için dosyaları indirip projenize dahil edebilir veya kendi API'nizi bu veri seti üzerine kurabilirsiniz.
