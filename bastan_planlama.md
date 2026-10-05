# 🛒 Mini E-Ticaret — Baştan Sona Tam Proje Planlaması (Master Plan)

> **Proje Özeti:**  
> Spring Boot 3 + Java 21 + PostgreSQL + Redis + React Vite mimarisiyle geliştirilen;  
> Concurrency (Pessimistic Lock), JWT Token Rotation, Redis Blacklist & Rate Limiting, Strategy & Adapter Pattern, Asenkron Event'ler ve Testcontainers içeren kurumsal e-ticaret sistemi.

---

## 🧭 Genel İlerleme Özeti

- [x] **Adım 1:** Spring Boot & Docker Compose (PostgreSQL 15 + Redis 7)
- [x] **Adım 2:** Veritabanı Entity'leri (`User`, `Category`, `Product`, `Order`, `OrderItem`)
- [x] **Adım 3:** Enum Tanımları (`Role`: ADMIN/CUSTOMER, `OrderStatus`: PENDING/CONFIRMED/SHIPPED/DELIVERED/CANCELLED)
- [x] **Adım 4:** Repository Katmanı (Pessimistic Write Lock, Timeout & Dinamik Filtreleme)
- [x] **Adım 5:** Global Exception Handling (9 Sınıf: `ApiException`, `ResourceNotFoundException`, `AlreadyExistsException`, `InsufficientStockException`, `OrderCancellationException`, `RateLimitException`, `UnauthorizedException`, `ErrorResponse`, `GlobalExceptionHandler`)
- [x] **Adım 6:** DTO Katmanı (Request & Response modelleri)
- [x] **Adım 7:** MapStruct Mapper'ları (`ProductMapper`, `CategoryMapper`, `OrderMapper`)
- [x] **Adım 8:** Redis Yapılandırması (`RedisConfig`, Cache TTL ayarları)
- [x] **Adım 9:** Redis Servisleri (`RefreshTokenService`, `TokenBlacklistService`, `RateLimitService`)
- [x] **Adım 10:** JWT Altyapısı (`JwtTokenProvider`, `JwtAuthenticationFilter`, `CustomUserDetailsService`)
- [x] **Adım 11:** Spring Security (`SecurityConfig` — Stateless, Endpoint Yetkilendirmeleri)
- [x] **Adım 12:** Kimlik Doğrulama Servis & Controller (`AuthService`, `AuthController`: register, login, refresh, logout)
- [x] **Adım 13:** Swagger / OpenAPI UI Entegrasyonu
- [x] **Adım 14:** Kategori Modülü (`CategoryService` + `CategoryController` + Redis Cache)
- [x] **Adım 15:** Strategy Pattern — İndirim Hesaplama (`DiscountStrategy`, `NoDiscount`, `Percentage`, `FixedAmount`, `DiscountStrategyFactory`)
- [x] **Adım 16:** Adapter Pattern — Çoklu Bildirim Altyapısı (`NotificationSender`, `FakeEmailClient`, `FakePushClient`, `EmailNotificationAdapter`, `PushNotificationAdapter`, `NotificationMessage`)
- [x] **Adım 17:** Fiyatlandırma & Ürün Yönetimi (`PricingService`, `ProductService`, `ProductController`)
- [x] **Adım 18:** Asenkron Event Sistemi (`AsyncConfig`, `OrderPlacedEvent`, `OrderStatusChangedEvent`, `OrderEventListener`, `InvoiceEventListener`)
- [ ] **Adım 19:** Sipariş Orkestrasyonu (`OrderFacade`, `OrderService`, `OrderController`)
- [ ] **Adım 20:** Admin Sipariş Yönetimi (`AdminOrderController`)
- [ ] **Adım 21 - 24:** Test Mühendisliği (Unit, Repository, Controller, Testcontainers Concurrency)
- [ ] **Adım 25:** React + Vite Frontend Arayüzü (Kapsamlı E-Ticaret UI)

---

# 📍 BÖLÜM 1: Kalan Backend Modülleri (Adım 17 - 20)

---

### 🔹 Adım 17: Fiyatlandırma & Ürün Yönetimi (Pricing & Product) ✅ (Tamamlandı)

#### 1. `PricingService.java` ✅ (Tamamlandı)
* **Paket:** `com.ecommerce.service`
* **Görevi:** `calculateRawTotal(List<OrderItem> items)` metodu ile sepetteki ürünlerin adet ve birim fiyatlarını çarpar, ham toplamı kuruş kaybı olmadan 2 basamağa yuvarlayarak döner.

#### 2. `ProductService.java` ✅ (Tamamlandı)
* **Paket:** `com.ecommerce.service`
* **Anotasyonlar:** `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
* **Bağımlılıklar:** `ProductRepository`, `CategoryRepository`, `ProductMapper`
* **Metotlar ve İş Mantığı:**
  1. `getProductWithFilters(Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, String name, Pageable pageable)`: Dinamik filtreleme ve sayfalama.
  2. `getProductById(Long id)`: `@Cacheable(value = "products", key = "#id")` ile Redis önbelleği.
  3. `createProduct(ProductCreateRequest request)`: `@CacheEvict(allEntries = true)` ile yeni ürün ekleme.
  4. `updateProduct(Long id, ProductCreateRequest request)`: `@CacheEvict(allEntries = true)` ile tam güncelleme.
  5. `deleteProduct(Long id)`: `@CacheEvict(allEntries = true)` ile silme.
  6. `checkStock(Product product, Integer requestedQuantity)`: Stok yetersizse `InsufficientStockException`.
  7. `decreaseStock(Product product, Integer quantity)`: `@CacheEvict(key = "#product.id")` ile stok düşürme.
  8. `getProductEntityWithLock(Long id)`: Sipariş modülü için Pessimistic Write Lock ile entity çekme.
  9. `getProductEntityById(Long id)`: Salt okunur entity çekme.

#### 3. `ProductController.java` ✅ (Tamamlandı)
* **Paket:** `com.ecommerce.controller`
* **Endpoint'ler:**
  - `GET /api/products`: Herkese açık (filtre ve sayfalama destekli).
  - `GET /api/products/{id}`: Herkese açık ürün detayı (Redis destekli).
  - `POST /api/products`: Sadece `ADMIN` -> `201 CREATED`.
  - `PUT /api/products/{id}`: Sadece `ADMIN` -> `200 OK`.
  - `DELETE /api/products/{id}`: Sadece `ADMIN` -> `204 NO CONTENT`.

---

### 🔹 Adım 18: Asenkron Spring Event Sistemi

#### 1. `AsyncConfig.java`
* **Paket:** `com.ecommerce.config`
* **Görevi:** `@EnableAsync` ile Spring'in thread havuzunu (`ThreadPoolTaskExecutor`) yapılandırır. Arka plan bildirimleri için 5 core, 10 max thread havuzu ayarlar.

#### 2. `OrderPlacedEvent.java` & `OrderStatusChangedEvent.java`
* **Paket:** `com.ecommerce.event`
* **Görevi:** Sipariş verildiğinde veya durumu değiştiğinde yayınlanan veri taşıyıcı olay nesneleri (orderId, userId, userEmail, totalPrice, status).

#### 3. `OrderEventListener.java`
* **Paket:** `com.ecommerce.event`
* **Anotasyonlar:** `@Component`, `@RequiredArgsConstructor`, `@Slf4j`
* **Bağımlılıklar:** `List<NotificationSender> notificationSenders` (Adım 16'daki Email ve Push adapter'ları).
* **Görevi:**
  - `@Async("taskExecutor")`, `@EventListener` `handleOrderPlaced(OrderPlacedEvent event)`: Tek döngüde hem E-posta hem Push bildirimini asenkron ateşler.
  - `@Async("taskExecutor")`, `@EventListener` `handleOrderStatusChanged(OrderStatusChangedEvent event)`: Durum değişiminde bildirim gönderir.

#### 4. `InvoiceEventListener.java` (Yeni: E-Fatura Kesim Dinleyicisi)
* **Paket:** `com.ecommerce.event`
* **Anotasyonlar:** `@Component`, `@Slf4j`
* **Görevi:**
  - `@Async("taskExecutor")`, `@EventListener` `handleOrderPlaced(OrderPlacedEvent event)`:
  - Sipariş verildiğinde `OrderEventListener` ile aynı anda arka planda paralel çalışır; müşteriyi hiç bekletmeden `INV-2026-XXXXXX` formatında asenkron e-fatura kesimini simüle eder.

---

### 🔹 Adım 19: Sipariş Orkestrasyonu (Facade & Order Modülü)

#### 1. `OrderFacade.java` (Büyük Orkestra Şefi)
* **Paket:** `com.ecommerce.facade`
* **Görevi (`@Transactional placeOrder`):**
  1. `ProductRepository.findByIdWithLock(productId)` ile ürünleri 3 saniye kilitli çeker (Pessimistic Write Lock).
  2. `productService.checkStock(...)` ile stok kontrolü yapar. Yetersizse `InsufficientStockException`.
  3. `OrderItem` listesini oluşturur.
  4. `pricingService.calculateRawTotal(orderItems)` ile ham toplamı alır.
  5. `discountStrategyFactory.getStrategy()` ile indirim stratejisini uygular (`strategy.apply(rawTotal)`).
  6. `Order` entity'sini üretip `orderRepository.save(order)` ile kaydeder.
  7. Her ürün için `productService.decreaseStock(...)` çağırarak stokları düşürür.
  8. `@CacheEvict` ile ürün önbelleğini temizler.
  9. `eventPublisher.publishEvent(new OrderPlacedEvent(...))` ile bildirimleri tetikler.

#### 2. `OrderService.java`
* **Paket:** `com.ecommerce.service`
* **Görevi:**
  - `getUserOrders(Long userId, Pageable pageable)`: Müşterinin kendi geçmiş siparişlerini sayfalı çeker.
  - `getOrderDetails(Long orderId, Long userId)`: `findByIdAndUserIdWithItems` ile N+1 sorgusunu önleyerek detay getirir.
  - `cancelOrder(Long orderId, Long userId)`: Sipariş PENDING ise iptal eder (`CANCELLED`) ve düşülen stokları geri iade eder.

#### 3. `OrderController.java`
* **Paket:** `com.ecommerce.controller`
* **Endpoint'ler:**
  - `POST /api/orders`: Sipariş oluşturma.
  - `GET /api/orders`: Müşterinin sipariş geçmişi.
  - `GET /api/orders/{id}`: Sipariş detayı.
  - `POST /api/orders/{id}/cancel`: Sipariş iptali.

---

### 🔹 Adım 20: Admin Sipariş Yönetimi

#### 1. `AdminOrderController.java`
* **Paket:** `com.ecommerce.controller`
* **Görevi (`hasRole('ADMIN')`):**
  - `GET /api/admin/orders`: Tüm siparişleri duruma göre (PENDING, CONFIRMED vb.) filtreleyerek sayfalı listeleme.
  - `PUT /api/admin/orders/{id}/status`: Sipariş durumunu güncelleme (CONFIRMED -> SHIPPED -> DELIVERED) ve `OrderStatusChangedEvent` tetikleme.

---

# 📍 BÖLÜM 2: Test Mühendisliği (Adım 21 - 24)

1. **Birim Testleri (Adım 21 - Mockito & JUnit 5):**
   - `DiscountStrategyTest`: %10 indirim, 50 TL indirim, kuruş yuvarlama ve negatif tutar koruması.
   - `CategoryServiceTest`: `@Mock` ile CRUD ve `ResourceNotFoundException` doğrulamaları.
   - `PricingServiceTest`: Çoklu kalem ve adet çarpımlarının doğrulanması.
2. **Repository Testleri (Adım 22 - `@DataJpaTest`):**
   - `ProductRepositoryTest`: Dinamik `findWithFilters` sorgusunun tüm kombinasyonları.
   - `OrderRepositoryTest`: Fetch join ve sayfalama testleri.
3. **Controller Testleri (Adım 23 - `@WebMvcTest` & MockMvc):**
   - `AuthControllerTest`: 401 Unauthorized, 429 Too Many Requests ve validasyon testleri.
   - `CategoryControllerTest`: Admin rolü olmayan isteklerde 403 Forbidden kontrolü.
4. **Testcontainers Concurrency Testi (Adım 24 - Zirve Test):**
   - `ConcurrentStockTest`: Gerçek Docker PostgreSQL konteynerinde 2 thread aynı anda son 1 stoku kapmaya çalışır. Biri `200 OK` alırken diğeri `InsufficientStockException` alır ve stok asla eksiye düşmez!

---

# 📍 BÖLÜM 3: Frontend Geliştirme (Adım 25: React + Vite) — GENİŞLETİLMİŞ REHBER

Backend API'lerimizi modern, reaktif ve şık bir vitrine dönüştürecek eksiksiz web uygulaması.

---

## 🛠️ 1. Teknoloji Yığını & Temel Kütüphaneler

* **Framework:** React 18 + Vite (Süper hızlı geliştirme ve derleme ortamı)
* **Yönlendirme:** `react-router-dom` v6 (Sayfa rotaları, Layout yapısı, Korumalı Route'lar)
* **HTTP İstemcisi:** `axios` (Otomatik JWT Bearer interceptor ve silent token rotation akışı ile)
* **İkon Kütüphanesi:** `lucide-react` (Modern ve şık SVG ikonlar)
* **Tasarım & Stil:** Modern Vanilla CSS (HSL renk değişkenleri, Dark & Light temalar, Glassmorphism kart efektleri, mikro animasyonlar ve mobil uyumlu Grid/Flexbox yapısı)

---

## 🗂️ 2. Frontend Klasör ve Dosya Hiyerarşisi

```
frontend/
├── public/
├── src/
│   ├── api/
│   │   ├── axiosClient.js          ← Base Axios instance + JWT Request/Response Interceptors
│   │   ├── authApi.js              ← login, register, refresh, logout API çağrıları
│   │   ├── productApi.js           ← getProducts (filtreli/sayfalı), getProductById, admin CRUD
│   │   ├── categoryApi.js          ← getAllCategories, admin CRUD
│   │   └── orderApi.js             ← placeOrder, getMyOrders, getOrderDetails, cancelOrder, adminOrders
│   │
│   ├── context/
│   │   ├── AuthContext.jsx         ← Kullanıcı kimlik state'i (user, token, login, logout, isAdmin)
│   │   └── CartContext.jsx         ← Sepet state'i (items, addToCart, removeFromCart, updateQuantity, localStorage)
│   │
│   ├── components/
│   │   ├── layout/
│   │   │   ├── Navbar.jsx          ← Logo, kategori linkleri, sepet ikonu & badge, kullanıcı profil menüsü
│   │   │   └── Footer.jsx          ← Telif, kurumsal linkler, sosyal medya ikonları
│   │   ├── common/
│   │   │   ├── ProtectedRoute.jsx  ← Giriş yapmamış kullanıcıyı /login'e yönlendiren koruma kalkanı
│   │   │   ├── AdminRoute.jsx      ← Rolü ADMIN olmayan kullanıcıları engelleyen koruma kalkanı
│   │   │   ├── LoadingSpinner.jsx  ← Şık dönen yükleme animasyonu
│   │   │   └── Toast.jsx           ← Yeşil başarı ve kırmızı hata bildirim kutucukları
│   │   ├── product/
│   │   │   ├── ProductCard.jsx     ← Ürün kartı (resim, kategori etiketi, fiyat, stok rozeti, sepete ekle butonu)
│   │   │   ├── ProductFilter.jsx   ← Kategori filtre butonları, min/max fiyat slider'ı, canlı arama kutusu
│   │   │   └── Pagination.jsx      ← Sayfa numaraları (1, 2, 3...) ve Önceki/Sonraki butonları
│   │   └── cart/
│   │       ├── CartItem.jsx        ← Sepetteki ürün satırı (ürün bilgisi, adet artır/azalt, çöp kutusu butonu)
│   │       └── CartSummary.jsx     ← Ham toplam, aktif kampanya indirimi ve ödenecek nihai tutar dökümü
│   │
│   ├── pages/
│   │   ├── LoginPage.jsx           ← Giriş sayfası (Brute-force 429 kilitleme sayacı ile)
│   │   ├── RegisterPage.jsx        ← Kayıt sayfası (İsim, e-posta, şifre formu ve validasyonlar)
│   │   ├── HomePage.jsx            ← Kampanya vitrini + Ürün kataloğu + Dinamik filtreleme
│   │   ├── ProductDetailPage.jsx   ← Ürün detayları, stok durumu ("Son 3 ürün!"), sepete ekleme
│   │   ├── CartPage.jsx            ← Sepet yönetimi ve sipariş özeti ekranı
│   │   ├── OrdersPage.jsx          ← "Siparişlerim" geçmişi, kargo durum rozetleri ve "İptal Et" butonu
│   │   └── admin/
│   │       ├── AdminDashboard.jsx  ← Admin yönetim ana kokpiti
│   │       ├── AdminProducts.jsx   ← Ürün ekleme/düzenleme/silme tablosu
│   │       ├── AdminCategories.jsx ← Kategori yönetim tablosu
│   │       └── AdminOrders.jsx     ← Gelen siparişlerin durumunu tek tıkla güncelleme paneli
│   │
│   ├── styles/
│   │   ├── index.css               ← CSS Değişkenleri (Renkler, HSL tonları, Fontlar: Outfit / Inter)
│   │   └── components.css          ← Butonlar, kartlar, modal pencereler ve form elemanları stilleri
│   ├── App.jsx                     ← Rota yapılandırması (React Router)
│   └── main.jsx                    ← React DOM Root
```

---

## ⚡ 3. Detaylı Sayfa Modülleri ve Ekran Akışları

### 1. Kimlik Doğrulama Ekranları (`LoginPage.jsx` & `RegisterPage.jsx`)
* **Giriş Ekranı (Login):**
  - E-posta ve şifre giriş alanları.
  - "Giriş Yap" butonu (yükleniyor spinner'ı ile).
  - **429 Brute-Force UX:** Kullanıcı 5 kez yanlış şifre girdiğinde buton kilitlenir ve kırmızı bir kutuda canlı geri sayım başlar:  
    *"Çok fazla hatalı giriş yaptınız. Lütfen 14:59 dakika sonra tekrar deneyiniz."*
* **Kayıt Ekranı (Register):**
  - Ad Soyad, E-posta, Şifre (En az 6 karakter validasyonu).
  - Başarılı kayıtta otomatik giriş veya login ekranına yeşil toast ile yönlendirme.

---

### 2. Ana Sayfa & Ürün Kataloğu (`HomePage.jsx`)
* **Üst Kampanya Banner'ı (Hero Section):**
  - "Büyük İndirim Haftası! Tüm sepette anında %10 indirim uygulanır." gibi dikkat çekici karşılama afişi.
* **Sol Filtre Paneli (`ProductFilter.jsx`):**
  - Kategori seçimi: "Tümü", "Elektronik", "Giyim" vb.
  - Fiyat Aralığı Slider'ı: Min 0 TL - Max 100.000 TL aralığı.
  - Arama Çubuğu: **300ms Debounce** özelliği sayesinde kullanıcı her harfe bastığında değil, yazmayı bitirdiğinde backend'e tek bir istek gider.
* **Ürün Izgarası (Product Grid):**
  - Sayfa başına 8-12 ürün kartı (`ProductCard.jsx`).
  - Kart üzerinde: Ürün adı, kategori etiketi, fiyatı ve stok durumu.
  - Eğer stok sıfırsa: "Tükendi" gri rozeti ve buton pasifleşir.
  - Eğer stok 5'in altındaysa: Kırmızı "Son 3 ürün!" uyarısı.
* **Sayfalama (`Pagination.jsx`):**
  - Toplam sayfa sayısı kadar sayfa numarası ve hızlı geçiş butonları.

---

### 3. Ürün Detay Sayfası (`ProductDetailPage.jsx`)
* Büyük ürün görseli, ürün açıklaması ve kategorisi.
* Canlı stok göstergesi.
* Adet seçici (+ / - butonları).
* "Sepete Ekle" butonu (Tıklandığında sepetteki adet güncellenir ve Navbar'daki sepet ikonu zıplama animasyonu yapar).

---

### 4. Sepet & Ödeme Sayfası (`CartPage.jsx`)
* **Sepet Listesi (`CartItem.jsx`):**
  - Sepetteki her ürünün resmi, adı, adedi ve birim fiyatı.
  - Adet artırma / azaltma butonları (Stok sınırına kadar artırılabilir).
  - Ürünü sepetten tamamen çıkarma butonu.
* **Sipariş Özeti (`CartSummary.jsx`):**
  - **Sepet Ham Tutarı:** 88.000,00 TL
  - **Kampanya İndirimi (%10):** -8.800,00 TL *(Strategy Pattern'in sepete anlık yansıması)*
  - **Ödenecek Nihai Tutar:** 79.200,00 TL
* **"Siparişi Tamamla" Butonu:**
  - Butona tıklandığında `POST /api/orders` isteği gider.
  - **Concurrency / Stok Hatası Yönetimi:** Eğer aynı anda başka bir müşteri son stoku almışsa, backend'den dönen `InsufficientStockException` yakalanır ve kırmızı toast açılır: *"Üzgünüz, sepetteki bir ürünün stoku az önce tükendi!"*
  - Sipariş başarılıysa sepet sıfırlanır ve kullanıcı "Siparişlerim" sayfasına yönlendirilir.

---

### 5. Siparişlerim Sayfası (`OrdersPage.jsx`)
* Kullanıcının geçmiş tüm siparişlerini kronolojik olarak listeler.
* **Durum Rozetleri (Status Badges):**
  - `PENDING` 🟡 (Hazırlanıyor / Onay Bekliyor)
  - `CONFIRMED` 🔵 (Onaylandı)
  - `SHIPPED` 🟣 (Kargoya Verildi)
  - `DELIVERED` 🟢 (Teslim Edildi)
  - `CANCELLED` 🔴 (İptal Edildi)
* **İptal Butonu Mantığı:**
  - Sadece durumu `PENDING` olan siparişlerin yanında kırmızı **"Siparişi İptal Et"** butonu aktiftir.
  - Tıklandığında `POST /api/orders/{id}/cancel` çağrılır, sipariş `CANCELLED` olur ve stoklar veritabanında otomatik geri yüklenir.
  - Sipariş `SHIPPED` olduğunda iptal butonu gizlenir.

---

### 6. Admin Yönetim Paneli (`AdminDashboard.jsx`)
Yalnızca `ADMIN` rolüne sahip kullanıcılara özel korumalı kokpit:
* **Sekme 1: Ürün Yönetimi (`AdminProducts.jsx`)**
  - Ürün listesi tablosu (Resim, İsim, Kategori, Fiyat, Stok, Eylemler).
  - "Yeni Ürün Ekle" modal penceresi (İsim, açıklama, fiyat, stok, kategori seçimi).
  - "Düzenle" ve "Sil" butonları.
  - Silme sırasında ürüne bağlı sipariş varsa backend'in döndüğü 409 Conflict yakalanıp uyarı gösterilir: *"Bu ürüne ait geçmiş siparişler bulunduğu için silinemez!"*
* **Sekme 2: Kategori Yönetimi (`AdminCategories.jsx`)**
  - Yeni kategori ekleme, güncelleme ve silme işlemleri.
* **Sekme 3: Sipariş Yönetimi (`AdminOrders.jsx`)**
  - Siteden verilen tüm siparişlerin müşteri adı ve tutarıyla listesi.
  - Durum filtresi (Sadece `PENDING` olanları gör vb.).
  - **Tek Tıkla Durum Güncelleme:** Açılır menüden `CONFIRMED` veya `SHIPPED` seçildiği anda backend'deki `PUT /api/admin/orders/{id}/status` tetiklenir ve müşteriye anında push/email simülasyonu gönderilir!

---

## 🔒 4. Frontend Güvenlik & Network Mimarisi

1. **Silent Token Rotation (Axios Interceptors):**
   - Access Token sadece tarayıcı RAM'inde (`AuthContext`) saklanır.
   - Herhangi bir istekte `401 Unauthorized` hatası alındığında, Axios Response Interceptor devreye girer.
   - Arka planda gizlice `POST /api/auth/refresh` isteği gönderilir.
   - Yeni gelen Access Token hafızaya yazılır ve kullanıcının yarım kalan isteği otomatik olarak tekrarlanır.
   - Kullanıcı hiçbir kesinti veya sayfa yenileme hissetmez!
2. **Korumalı Rota Güvenliği (`ProtectedRoute` & `AdminRoute`):**
   - Giriş yapmamış biri `/orders` veya `/cart` sayfasına girmeye çalışırsa doğrudan `/login`'e postalanır.
   - Normal bir müşteri `/admin` sayfasına erişmeye çalışırsa `403 Yetkisiz Erişim` ekranı ile engellenir.
