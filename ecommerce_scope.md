# 🛒 Mini E-Ticaret Projesi — Tam Kapsam Belgesi

---

## 🗂️ Entity'ler

### User
```
id          Long        PK
name        String      kullanıcı adı
email       String      unique, login için
password    String      bcrypt ile hash'lenir
role        Enum        ADMIN | CUSTOMER
createdAt   LocalDateTime
```

### Category
```
id          Long        PK
name        String      unique (örn: Elektronik, Giyim)
description String
```

### Product
```
id          Long        PK
name        String
description String
price       BigDecimal  (0'dan büyük)
stock       Integer     (0'dan küçük olamaz)
category    Category    ManyToOne
createdAt   LocalDateTime
updatedAt   LocalDateTime
```
> ⚠️ `stock` alanı pessimistic lock ile güncellenir, race condition önlenir.

### Order
```
id           Long        PK
user         User        ManyToOne
items        List<OrderItem>  OneToMany
totalPrice   BigDecimal  sipariş anındaki toplam (anlık hesaplama değil, kaydedilir)
status       Enum        PENDING | CONFIRMED | SHIPPED | DELIVERED | CANCELLED
createdAt    LocalDateTime
updatedAt    LocalDateTime
```

### OrderItem
```
id          Long        PK
order       Order       ManyToOne
product     Product     ManyToOne
quantity    Integer
unitPrice   BigDecimal  sipariş anındaki ürün fiyatı (ürün fiyatı sonradan değişse de korunur)
```

---

## 📊 Tablo Görünümü (Yakup'un Hikayesi)

### 1. `users` Tablosu
Sisteme "Yakup" adında bir müşteri kayıt oldu.
| id | name | email | password (şifrelenmiş) | role | created_at |
|---|---|---|---|---|---|
| **1** | Yakup Yılmaz | yakup@test.com | $2a$10$w... | CUSTOMER | 2026-09-29 10:00 |

### 2. `categories` Tablosu
Admin sisteme "Elektronik" adında bir kategori ekledi.
| id | name | description |
|---|---|---|
| **10** | Elektronik | Bilgisayar ve telefonlar |

### 3. `products` Tablosu
Admin "Elektronik" kategorisine (category_id: 10) iki tane ürün ekledi.
| id | name | description | price | stock | category_id |
|---|---|---|---|---|---|
| **50** | Macbook Pro | M3 Çip, 16GB RAM | 80.000 | 5 | **10** |
| **51** | Magic Mouse | Siyah Renk | 4.000 | 20 | **10** |

### 4. `orders` Tablosu (Siparişin Ana Fişi)
Yakup, 1 Macbook ve 2 Mouse sipariş etti. Toplam tutar: 88.000 TL.
| id | user_id | total_price | status | created_at |
|---|---|---|---|---|
| **1001** | **1** | 88.000 | PENDING | 2026-09-29 14:30 |

### 5. `order_items` Tablosu (Siparişin Kalemleri)
| id | order_id | product_id | quantity | unit_price |
|---|---|---|---|---|
| 1 | **1001** | **50** (Macbook) | **1** | 80.000 |
| 2 | **1001** | **51** (Mouse) | **2** | 4.000 |

---
## 🔗 İlişkiler

```
User        1 ────────── * Order
Order       1 ────────── * OrderItem
OrderItem   * ────────── 1 Product
Product     * ────────── 1 Category
```

| İlişki | Tip | Fetch Stratejisi |
|---|---|---|
| User → Orders | OneToMany | LAZY |
| Order → OrderItems | OneToMany | EAGER (sipariş detayında her zaman lazım) |
| OrderItem → Product | ManyToOne | LAZY |
| Product → Category | ManyToOne | LAZY |

---

## 🔐 Auth & Güvenlik

### Genel Yaklaşım
- JWT ile **stateless** authentication (sunucuda session yok)
- **Access Token** → kısa ömürlü (15 dakika), her API isteğinde `Authorization: Bearer <token>` header'ında
- **Refresh Token** → uzun ömürlü (7 gün), Redis'te `refresh_token:{userId}` key'i ile saklanır
- Rol tabanlı yetkilendirme: `ADMIN` / `CUSTOMER`

### Token Akışı (Adım Adım)
```
1. POST /api/auth/login
   → Kullanıcı doğrula
   → Access Token üret (15dk)
   → Refresh Token üret (7gün) → Redis'e yaz
   → Her ikisini de response'da döndür

2. API isteği yaparken:
   Authorization: Bearer <accessToken>
   → JwtAuthenticationFilter devreye girer
   → Token imzası geçerli mi? (JJWT ile doğrula)
   → Token süresi dolmuş mu?
   → Token blacklist'te var mı? (Redis'e bak)
   → OK ise SecurityContext'e kullanıcıyı set et

3. Access Token süresi dolunca:
   POST /api/auth/refresh  { refreshToken: "..." }
   → Redis'te bu refresh token var mı? Süresi dolmuş mu?
   → OK ise yeni Access Token üret → döndür

4. Logout:
   POST /api/auth/logout
   → Refresh Token'ı Redis'ten sil (artık refresh yapılamaz)
   → Access Token'ı Redis blacklist'e ekle, TTL = token'ın kalan süresi
   → Bir sonraki API isteğinde filter blacklist'i görür → 401
```

### Redis Key Yapısı
| Key | Kullanım | Value | TTL |
|---|---|---|---|
| `refresh_token:{userId}` | Auth | refresh token string | 7 gün |
| `blacklist:{accessToken}` | Auth | `"revoked"` | Token'ın kalan ömrü |
| `products::*` | Cache | JSON (product listesi) | 10 dakika |
| `categories::all`, `categories::{id}` | Cache | JSON (kategori listesi / tekil kategori) | 30 dakika |
| `login_attempts:{ip}` | Rate Limit | sayaç (integer) | 15 dakika |

### Rol Yetkileri
| Endpoint Grubu | ADMIN | CUSTOMER |
|---|---|---|
| Category CRUD | ✅ | ❌ |
| Product CRUD (ekle/güncelle/sil) | ✅ | ❌ |
| Product listele/detay | ✅ | ✅ |
| Sipariş ver | ❌ | ✅ |
| Kendi siparişlerini gör | ❌ | ✅ |
| Tüm siparişleri gör | ✅ | ❌ |
| Sipariş durumu güncelle | ✅ | ❌ |

---

## 📦 API Endpoint'leri

### Auth
```
POST   /api/auth/register          → Kayıt ol
       Body: { name, email, password }
       Response: { message: "Kayıt başarılı" }

POST   /api/auth/login             → Giriş yap
       Body: { email, password }
       Response: { accessToken, refreshToken }

POST   /api/auth/refresh           → Yeni access token al
       Body: { refreshToken }
        Response: { accessToken, refreshToken }  (refresh token rotation: her refresh'te yeni çift döner)

POST   /api/auth/logout            → Çıkış yap
       Header: Authorization: Bearer <accessToken>
       Body: { refreshToken }
       Response: { message: "Çıkış başarılı" }
```

### Category (ADMIN yetkisi gerekir — GET hariç)
```
POST   /api/categories             → Kategori oluştur
       Body: { name, description }

GET    /api/categories             → Tüm kategorileri listele (herkese açık)

PUT    /api/categories/{id}        → Güncelle
       Body: { name, description }

DELETE /api/categories/{id}        → Sil
```

### Product
```
POST   /api/products               → Ürün ekle (ADMIN)
       Body: { name, description, price, stock, categoryId }

GET    /api/products               → Listele + filtrele + sayfalama (herkese açık)
       Query Params:
         page=0, size=10, sort=price,asc
         categoryId=1
         minPrice=0, maxPrice=500
         name=laptop  (isim ile arama)

GET    /api/products/{id}          → Detay (herkese açık)

PUT    /api/products/{id}          → Güncelle (ADMIN)
       Body: { name, description, price, stock, categoryId }

DELETE /api/products/{id}          → Sil (ADMIN)
```

### Order (CUSTOMER)
```
POST   /api/orders                 → Sipariş ver
       Body: {
         items: [
           { productId: 1, quantity: 2 },
           { productId: 3, quantity: 1 }
         ]
       }
       Response: OrderResponse (id, items, totalPrice, status)

GET    /api/orders?page=0&size=10  → Kendi siparişlerini listele

GET    /api/orders/{id}            → Sipariş detayı (sadece kendi siparişi)

PUT    /api/orders/{id}/cancel     → İptal et (sadece PENDING ise)
```

### Admin Order (ADMIN)
```
GET    /api/admin/orders?page=0&size=20&status=PENDING  → Tüm siparişler + filtre

PUT    /api/admin/orders/{id}/status
       Body: { status: "CONFIRMED" }  → Durum güncelle
```

---

## 🧩 Design Pattern'ler

### 1. Strategy Pattern — İndirim Hesaplama

**Problem:** İndirim mantığı değişebilir. Yüzdelik mi, sabit tutar mı, indirim yok mu?
Bunu if-else ile yazmak OCP'yi ihlal eder.

**Çözüm:** Her indirim tipi ayrı bir sınıf olur, hepsi aynı interface'i implement eder.

```
DiscountStrategy (interface)
  └── BigDecimal apply(BigDecimal totalPrice)

  ├── NoDiscountStrategy
  │     return totalPrice;
  │
  ├── PercentageDiscountStrategy
  │     @Value("${ecommerce.discount.percentage-rate:0.10}")
  │     return totalPrice - (totalPrice * percentage);
  │
  └── FixedAmountDiscountStrategy
        @Value("${ecommerce.discount.fixed-amount:50.00}")
        return totalPrice - amount;
```

**Nasıl kullanılır:**
```java
// OrderFacade içinde
// 1. Sistemde o an aktif olan stratejiyi döner (application.yml'deki active-strategy)
DiscountStrategy strategy = discountStrategyFactory.getStrategy();

// Veya istenirse belirli bir tipe göre strateji çekilebilir:
// DiscountStrategy strategy = discountStrategyFactory.getStrategy("PERCENTAGE");

BigDecimal finalPrice = strategy.apply(rawTotal);
```

**Neden iyi:**
- Yeni indirim tipi eklemek için mevcut kodu değiştirmezsin → OCP
- Test etmek kolay → her stratejiyi izole test edebilirsin

---

### 2. Facade Pattern — Sipariş İş Akışı

**Problem:** Sipariş vermek 5-6 adımlık bir süreç. Controller'ın bu karmaşıklığı bilmesi gerekmez.

**Çözüm:** `OrderFacade` tüm adımları koordine eder. Controller sadece `orderFacade.placeOrder(request, user)` der.

```
OrderFacade.placeOrder(request, user):
  1. Her ürün için stok kontrolü yap          → ProductService
  2. Ürünleri pessimistic lock ile getir       → ProductRepository
  3. Ham toplam fiyatı hesapla                 → PricingService
  4. Aktif indirim stratejisini uygula         → DiscountStrategy
  5. Order + OrderItem'ları oluştur            → OrderBuilder
  6. Siparişi kaydet                           → OrderService
  7. Her ürünün stokunu düş                    → ProductService
  8. Event yayınla: OrderPlacedEvent           → ApplicationEventPublisher
```

**Neden iyi:**
- Controller sadece bir metot çağırır → Single Responsibility
- Adımlar değişirse sadece Facade değişir
- Her adım kendi servisinde test edilebilir

---

### 3. Adapter Pattern — Bildirim Gönderme (Email + Push)

**Problem:** Sipariş verilince kullanıcıya bildirim gitmeli. Ama bildirim gönderen "dış" sınıfların
imzaları birbirinden ve bizim sistemimizden farklı (`sendMail(...)`, `pushToDevice(...)`).
Listener'ın bu farklılıkları bilmesi gerekmez.

**Çözüm:** Sistemin kendi interface'i (`NotificationSender`) tanımlanır. Her dış sınıf için bir **Adapter**
yazılır, adapter mesajı dış sınıfın beklediği formata **çevirir**.

> ℹ️ Dış client'lar gerçek mail/push göndermez, sadece log basar (simülasyon).
> Gerçek bir SDK (örn. JavaMailSender) gelirse sadece adapter'ın içi değişir, listener'a dokunulmaz.

```
NotificationMessage (küçük DTO)
  recipientEmail, recipientId, title, content

NotificationSender (interface — sistemin beklediği arayüz)
  └── void send(NotificationMessage message)

  ├── EmailNotificationAdapter  → FakeEmailClient'a çevirir
  │     FakeEmailClient.sendMail(String address, String subject, String htmlBody) : boolean
  │     - recipientEmail → address
  │     - title → subject
  │     - content düz metin → HTML'e sarılır (<h3>, <b>)
  │     - boolean false ise uyarı loglar
  │
  └── PushNotificationAdapter   → FakePushClient'a çevirir
        FakePushClient.pushToDevice(String deviceKey, String title, String payloadJson) : String
        - recipientId → deviceKey ("user-" + id)
        - content → JSON payload'a çevrilir
        - dönen mesaj id'sini loglar
```

**Log çıktıları (aynı bilgi, farklı biçim):**
```
[EMAIL] to=yakup@test.com | subject=Siparişiniz alındı | body=<h3>Siparişiniz alındı</h3><b>Sipariş #1001, toplam 88.000 TL</b>
[PUSH]  device=user-1 | id=PUSH-8a3f | title=Siparişiniz alındı | payload={"orderId":1001,"total":"88.000"}
```

**Nasıl kullanılır:**
```java
// OrderEventListener içinde — log.info yerine
// Spring tüm NotificationSender bean'lerini (iki adapter) otomatik toplar
private final List<NotificationSender> senders;

NotificationMessage message = new NotificationMessage(
    user.getEmail(), user.getId(), "Siparişiniz alındı", "Sipariş #" + order.getId() + ", toplam " + order.getTotalPrice());
senders.forEach(sender -> sender.send(message));
```

**Kapsam:** Sadece `OrderPlacedEvent` için tetiklenir. `User` entity'sine alan eklenmez.
Sipariş akışı (OrderFacade) değişmez, sadece listener'daki log satırı yerine bu çağrı gelir.

**Neden iyi:**
- Listener dış sınıfların imzasını bilmez
- Yeni kanal (SMS, Slack) eklemek için sadece yeni bir Adapter yazarsın, mevcut kod değişmez
- Test kolay: `NotificationSender` mock'lanabilir

---

### 4. Builder Pattern — Sipariş Oluşturma

**Problem:** `Order` nesnesi karmaşık — user, items listesi, totalPrice, status, timestamp.
Constructor ile oluşturmak çirkin ve hataya açık.

**Çözüm:** `@Builder` (Lombok) veya manuel `OrderBuilder` ile adım adım oluşturma.

```java
Order order = Order.builder()
    .user(currentUser)
    .items(orderItems)
    .totalPrice(finalPrice)
    .status(OrderStatus.PENDING)
    .createdAt(LocalDateTime.now())
    .build();
```

**Neden iyi:**
- Okunabilir, hangi alanın ne olduğu belli
- Zorunlu/opsiyonel alanları kontrol edebilirsin
- Test'te farklı senaryolar için kolayca farklı objeler oluşturursun

---

### 5. Repository Pattern

**Yaklaşım:** Her entity için `JpaRepository` extend eden interface.
Basit CRUD otomatik gelir. Kompleks sorgular `@Query` ile yazılır.

**Örnek custom sorgular:**

```java
// ProductRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);

// Kategori + fiyat aralığı filtresi
@Query("SELECT p FROM Product p WHERE " +
       "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
       "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
       "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
       "(:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))")
Page<Product> findWithFilters(..., Pageable pageable);

// OrderRepository
Page<Order> findByUser(User user, Pageable pageable);
Page<Order> findByStatus(OrderStatus status, Pageable pageable);
```

---

## ⚡ Spring Event-Driven Mekanizması

### Neden Kullanıyoruz?
`OrderFacade` sipariş kaydettikten sonra ne yapılacak? Log yazmak, stok uyarısı vermek vs.
Bunları Facade'a yazarsak tek sorumluluk ilkesi bozulur.

**Çözüm:** Event yayınla, ilgilenen sınıflar dinlesin. Facade sadece olayı duyurur, nasıl işleneceğini bilmez.

---

### Event Sınıfları
```java
// Sipariş verildi eventi (Payload DTO)
public class OrderPlacedEvent {
    private final Long orderId;
    private final Long userId;
    private final String userEmail;
    private final BigDecimal totalPrice;
}

// Sipariş durumu değişti eventi (Payload DTO)
public class OrderStatusChangedEvent {
    private final Long orderId;
    private final String userEmail;
    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;
}
```

---

### Event Yayınlama
```java
// OrderFacade içinde — sipariş kaydedildikten SONRA
eventPublisher.publishEvent(new OrderPlacedEvent(order.getId(), user.getId(), user.getEmail(), order.getTotalPrice()));

// AdminOrderController içinde — sipariş durumu güncellenince
eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getUser().getEmail(), previousStatus, newStatus));
```

---

### Event Dinleyiciler (Asenkron & Ayrık Çalışan Dinleyiciler)

#### 1. `OrderEventListener` (Bildirim Dinleyicisi — Email + Push Adapter)
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    // Spring, NotificationSender uygulayan EmailNotificationAdapter ve PushNotificationAdapter'ı otomatik enjekte eder
    private final List<NotificationSender> notificationSenders;

    @Async("taskExecutor")
    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("[{}] Sipariş verildi olayı yakalandı: Sipariş ID={}, Tutar={} TL",
                Thread.currentThread().getName(), event.getOrderId(), event.getTotalPrice());

        NotificationMessage message = NotificationMessage.builder()
                .recipient(event.getUserEmail())
                .title("Siparişiniz Alındı!")
                .body(String.format("Sayın müşterimiz, #%d numaralı siparişiniz başarıyla alındı. Tutar: %s TL",
                        event.getOrderId(), event.getTotalPrice()))
                .build();

        notificationSenders.forEach(sender -> sender.send(message));
    }

    @Async("taskExecutor")
    @EventListener
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("[{}] Sipariş durumu değişti: Sipariş ID={}, {} -> {}",
                Thread.currentThread().getName(), event.getOrderId(), event.getPreviousStatus(), event.getNewStatus());

        NotificationMessage message = NotificationMessage.builder()
                .recipient(event.getUserEmail())
                .title("Sipariş Durumu Güncellendi")
                .body(String.format("#%d numaralı siparişinizin durumu güncellendi: %s -> %s",
                        event.getOrderId(), event.getPreviousStatus(), event.getNewStatus()))
                .build();

        notificationSenders.forEach(sender -> sender.send(message));
    }
}
```

#### 2. `InvoiceEventListener` (E-Fatura Kesim Dinleyicisi — Simülasyon)
```java
@Component
@Slf4j
public class InvoiceEventListener {

    // Sipariş verildiğinde OrderEventListener ile aynı anda arka planda paralel çalışır
    @Async("taskExecutor")
    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        String invoiceNumber = String.format("INV-2026-%06d", event.getOrderId());
        log.info("[{}] E-Fatura asenkron olarak kesildi. Fatura No: {}, Alıcı: {}, Tutar: {} TL",
                Thread.currentThread().getName(), invoiceNumber, event.getUserEmail(), event.getTotalPrice());
    }
}
```

---

### @Async için Config
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("EventAsync-");
        executor.initialize();
        return executor;
    }
}
```

---

### Event Akışı Özeti
```
POST /api/orders (CUSTOMER)
  ↓
OrderController.createOrder()
  ↓
OrderFacade.placeOrder()          ← @Transactional burada
  ├── stok kontrolü
  ├── fiyat hesapla
  ├── indirim uygula
  ├── DB'ye kaydet  ←── transaction commit
  └── publishEvent(OrderPlacedEvent)
        ↓
        Transaction commit sonrası tetiklenir (@TransactionalEventListener)
        ↓
        OrderEventListener.handleOrderPlaced()  ←── @Async (ayrı thread)
          └── log: "Sipariş verildi: OrderId=42, ..."
```

---

## ⚠️ Global Exception Handler

`@RestControllerAdvice` — tüm controller'lardan fırlayan exception'ları tek noktada yakalar ve standart `ErrorResponse` JSON yapısına dönüştürür.

### Özel Exception Sınıfları (9 Sınıf)
| Sınıf Adı | HTTP Status | Ne zaman fırlatılır? |
|---|---|---|
| `ApiException` | Dinamik | Tüm özel exception'ların soyut ata sınıfı (`status` ve `message` taşır) |
| `ResourceNotFoundException` | 404 NOT FOUND | Aranan ürün, kategori, sipariş veya kullanıcı veritabanında bulunamadığında |
| `AlreadyExistsException` | 409 CONFLICT | E-posta veya kategori adı gibi unique alanlar zaten kullanımda olduğunda |
| `InsufficientStockException` | 400 BAD REQUEST | İstenen adet mevcut stoktan fazla olduğunda |
| `OrderCancellationException` | 400 BAD REQUEST | `PENDING` dışındaki (onaylanmış/kargoya verilmiş) sipariş iptal edilmek istendiğinde |
| `RateLimitException` | 429 TOO MANY REQUESTS | 15 dakika içinde 5 kez hatalı şifre denendiğinde (Brute-force kalkanı) |
| `UnauthorizedException` | 401 UNAUTHORIZED | Token geçersiz, imzası bozuk, süresi dolmuş veya Redis blacklist'te olduğunda |
| `ErrorResponse` | Model | `status`, `error`, `message`, `timestamp`, `validationErrors` taşıyan DTO |
| `GlobalExceptionHandler` | ControllerAdvice | `ApiException`, `MethodArgumentNotValidException`, `DataIntegrityViolationException` ve `Exception` yakalayan beyin |

### Framework Seviyesindeki Hataların Yakalanması:
* **`MethodArgumentNotValidException` (400 Bad Request):** `@Valid` anotasyonu ihlal edildiğinde alan bazlı hata haritası (`validationErrors`) üretir.
* **`DataIntegrityViolationException` (409 Conflict):** Veritabanı foreign key veya unique constraint ihlal edildiğinde (örn: geçmiş siparişi olan ürünü silmeye çalışmak) sistemi korur.
* **`AccessDeniedException` (403 Forbidden):** Rol yetersizliği durumunda `JwtAccessDeniedHandler` devreye girer.
* **`BadCredentialsException` (401 Unauthorized):** Login anında şifre yanlış girildiğinde fırlatılır.
* **`Exception` (500 Internal Server Error):** Öngörülemeyen genel sistem hataları için en son emniyet sübabı.

### Standart Hata Cevabı (ErrorResponse)
Başarılı cevaplar sarmalayıcı (wrapper) olmadan doğrudan DTO olarak döner.
Sadece hatalar standart `ErrorResponse` formatında döner:
```json
// Hata
{
  "status": 404,
  "error": "Not Found",
  "message": "Ürün bulunamadı. id : '5'",
  "timestamp": "2026-10-04T01:00:00",
  "validationErrors": null
}

// Validation hatası
{
  "status": 400,
  "error": "Bad Request",
  "message": "Gönderilen verilerde doğrulama hataları mevcut.",
  "timestamp": "2026-10-04T01:00:00",
  "validationErrors": {
    "email": "Geçerli bir email adresi giriniz",
    "password": "Şifre en az 6 karakter olmalıdır"
  }
}
```

---

## ✅ Validation (Bean Validation)

### RegisterRequest
```java
@NotBlank(message = "Ad zorunludur")
String name;

@NotBlank @Email(message = "Geçerli bir email giriniz")
String email;

@NotBlank @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
String password;
```

### ProductRequest
```java
@NotBlank(message = "Ürün adı zorunludur")
String name;

@NotNull @Positive(message = "Fiyat 0'dan büyük olmalıdır")
BigDecimal price;

@NotNull @Min(value = 0, message = "Stok negatif olamaz")
Integer stock;

@NotNull(message = "Kategori zorunludur")
Long categoryId;
```

### OrderRequest
```java
@NotEmpty(message = "Sipariş en az 1 ürün içermelidir")
List<OrderItemRequest> items;

// OrderItemRequest
@NotNull Long productId;
@Min(value = 1, message = "Miktar en az 1 olmalıdır") Integer quantity;
```

> Controller'da `@Valid` anotasyonu ile aktif edilir.
> Hata otomatik olarak GlobalExceptionHandler'daki `MethodArgumentNotValidException` handler'ına düşer.

---

## 🔒 Transaction & Pessimistic Locking

### @Transactional — Sipariş Akışı
```java
@Transactional  // OrderFacade.placeOrder() üzerinde
public OrderResponse placeOrder(OrderRequest request, User user) {
    // Bu metodun içindeki tüm DB operasyonları tek transaction'da
    // Herhangi biri hata verirse hepsi rollback olur
    // 1. Stok getir (with lock)
    // 2. Stok güncelle
    // 3. Sipariş kaydet
    // --- transaction commit ---
    // 4. Event tetiklenir (AFTER_COMMIT)
}
```

### Pessimistic Locking — Stok Güncelleme
```java
// ProductRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

**Senaryo (2 kullanıcı son 1 ürünü aynı anda almaya çalışıyor):**
```
T1: findByIdWithLock(productId) → KİLİT ALINDI, stock=1
T2: findByIdWithLock(productId) → 3 saniye BEKLER
T1: stock=0 yap → kaydet → commit → KİLİT BIRAKILDI
T2: kilidi aldı → stock=0 → InsufficientStockException fırlatır ✅
```

> ✅ İkinci kullanıcı anında hata almaz, önce bekler. Bu gerçek hayata en yakın davranış.

---

## 📊 Pagination

### Controller Kullanımı
```java
@GetMapping
public ResponseEntity<Page<ProductDto>> getProducts(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "id") String sortBy,
    @RequestParam(defaultValue = "asc") String direction,
    @RequestParam(required = false) Long categoryId,
    @RequestParam(required = false) BigDecimal minPrice,
    @RequestParam(required = false) BigDecimal maxPrice,
    @RequestParam(required = false) String name
) {
    Pageable pageable = PageRequest.of(page, size,
        direction.equals("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());
    return ...;
}
```

### Response Formatı
```json
{
  "content": [ { "id": 1, "name": "Laptop", "price": 15999.00 } ],
  "pageable": { "pageNumber": 0, "pageSize": 10 },
  "totalElements": 47,
  "totalPages": 5,
  "first": true,
  "last": false,
  "numberOfElements": 10
}
```

---

## 📖 Swagger / OpenAPI

### Dependency (pom.xml)
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

### application.yml
```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
```

### SwaggerConfig.java
```java
@OpenAPIDefinition(
    info = @Info(title = "E-Ticaret API", version = "1.0", description = "Mini E-Ticaret REST API"),
    security = @SecurityRequirement(name = "Bearer")
)
@SecurityScheme(
    name = "Bearer",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
```

### Controller Anotasyonları
```java
@Tag(name = "Products", description = "Ürün yönetimi")
@RestController
public class ProductController {

    @Operation(summary = "Ürün listele", description = "Filtre ve sayfalama destekler")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Başarılı"),
        @ApiResponse(responseCode = "401", description = "Token gerekli")
    })
    @GetMapping
    public ResponseEntity<?> getProducts(...) { }
}
```

> 🌐 Swagger UI: `http://localhost:8080/swagger-ui.html`
> `Authorize` butonuna Bearer token girerek korumalı endpoint'leri test edebilirsin.

---

## 🔄 Order Status Akışı

```
          ┌─────────┐
          │ PENDING │  ← Sipariş verildiğinde
          └────┬────┘
               │  ADMIN onaylar
          ┌────▼─────┐
          │CONFIRMED │
          └────┬─────┘
               │  ADMIN kargoya verir
          ┌────▼────┐
          │ SHIPPED │
          └────┬────┘
               │  Teslim edildi
          ┌────▼──────┐
          │ DELIVERED │
          └───────────┘

PENDING → CANCELLED  ← Sadece PENDING'den iptal olur
                        (CONFIRMED, SHIPPED iptal edilemez)
```

**Business Rule:** `OrderCancellationException` fırlatma koşulları:
- Status PENDING değilse → "Bu sipariş artık iptal edilemez"
- Başkasının siparişine erişilirse → `UnauthorizedAccessException`

---

## 🧪 Test Yapısı

### 1. Unit Test — JUnit 5 + Mockito
Bağımlılıklar `@Mock` ile taklit edilir, sadece test ettiğin sınıf gerçek.

| Test Sınıfı | Test Edilen Davranışlar |
|---|---|
| `OrderServiceTest` | Sipariş iptal — PENDING ise OK, değilse exception |
| `DiscountStrategyTest` | Her strateji için doğru hesaplama |
| `OrderFacadeTest` | Servislerin doğru sırayla çağrıldığı (verify ile) |
| `PricingServiceTest` | Toplam fiyat hesaplama, quantity * unitPrice |
| `RefreshTokenServiceTest` | Token kaydet, bul, sil — Redis mock ile |
| `TokenBlacklistServiceTest` | Blacklist'e ekle, var mı kontrolü |
| `OrderEventListenerTest` | Event gelince tüm `NotificationSender`'lar çağrılıyor mu? |
| `EmailNotificationAdapterTest` | Mesaj doğru subject/HTML'e çevriliyor mu? |
| `PushNotificationAdapterTest` | Mesaj doğru deviceKey/JSON'a çevriliyor mu? |

---

### 2. Repository Test — @DataJpaTest
Sadece JPA katmanı yüklenir, H2 in-memory DB kullanılır.

| Test Sınıfı | Test Edilen Sorgular |
|---|---|
| `ProductRepositoryTest` | `findWithFilters` — kategori/fiyat/isim filtresi |
| `ProductRepositoryTest` | `findByIdWithLock` — lock'un çalışıp çalışmadığı |
| `OrderRepositoryTest` | `findByUser` — sadece o kullanıcının siparişleri |
| `OrderRepositoryTest` | `findByStatus` — PENDING siparişleri getir |
| `UserRepositoryTest` | `findByEmail` — email ile kullanıcı bul |

---

### 3. Controller Test — @WebMvcTest + MockMvc
Sadece web katmanı yüklenir. Servisler `@MockBean` ile mock'lanır.
Gerçek HTTP isteği simüle edilir.

| Test Sınıfı | Test Edilen Senaryolar |
|---|---|
| `AuthControllerTest` | Register — başarılı, email zaten var, validation hatası |
| `AuthControllerTest` | Login — başarılı, yanlış şifre |
| `AuthControllerTest` | Refresh — geçerli token, geçersiz token |
| `ProductControllerTest` | GET /products — 200, filtreler çalışıyor mu? |
| `ProductControllerTest` | POST /products — ADMIN ise 201, CUSTOMER ise 403 |
| `ProductControllerTest` | POST /products — validation hatası (price negatif) |
| `OrderControllerTest` | POST /orders — başarılı sipariş |
| `OrderControllerTest` | PUT /orders/{id}/cancel — PENDING → iptal |
| `OrderControllerTest` | Başkasının siparişine erişim → 403 |

---

### 4. Integration Test — @SpringBootTest + Testcontainers
Docker'da gerçek PostgreSQL + Redis ayağa kalkar. Gerçek HTTP isteği atılır. Test biter, container kapanır.

| Test Sınıfı | Test Edilen Senaryo |
|---|---|
| `AuthIntegrationTest` | register → login → token al → refresh → logout → tekrar istek at → 401 |
| `OrderIntegrationTest` | login → sipariş ver → DB'de sipariş var mı? → stok düştü mü? |
| `ConcurrentStockTest` | **10 thread aynı anda** 1 stoklu ürüne sipariş atar → sadece 1 başarılı olur |

> ⭐ `ConcurrentStockTest` en kritik test. Pessimistic locking'in gerçekten çalıştığını kanıtlar.
> `ExecutorService` ile 10 thread açılır, hepsi aynı anda sipariş verir. Sonuçta stok 0, sadece 1 sipariş başarılı.

### Test Dependency'leri (pom.xml)
```xml
<!-- Testcontainers -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<!-- Embedded Redis (test için) -->
<dependency>
    <groupId>com.github.codemonstur</groupId>
    <artifactId>embedded-redis</artifactId>
    <version>1.4.3</version>
    <scope>test</scope>
</dependency>
```

---

## 📁 Package Yapısı

```
com.ecommerce
├── config/
│   ├── SecurityConfig.java          ← Spring Security filter chain, stateless JWT, endpoint yetkileri
│   ├── RedisConfig.java             ← Redis connection factory, RedisTemplate ve Cache TTL ayarları
│   └── AsyncConfig.java             ← @EnableAsync, ThreadPoolTaskExecutor (Worker Thread havuzu)
│
├── controller/
│   ├── AuthController.java          ← register, login, refresh, logout
│   ├── ProductController.java       ← ürün CRUD + dinamik filtreleme + sayfalama
│   ├── CategoryController.java      ← kategori CRUD
│   ├── OrderController.java         ← sipariş ver, listele, iptal
│   └── AdminOrderController.java    ← tüm siparişler, durum güncelle
│
├── service/
│   ├── AuthService.java             ← kimlik doğrulama, token rotasyonu, blacklist & brute-force kontrolü
│   ├── ProductService.java          ← ürün CRUD, Redis cache (@Cacheable, @CacheEvict), stok kontrolü
│   ├── CategoryService.java         ← kategori CRUD, Redis cache
│   ├── OrderService.java            ← sipariş kaydet, iptal, geçmiş siparişleri listele
│   └── PricingService.java          ← sepetteki ham toplam tutarı hesapla
│
├── facade/
│   └── OrderFacade.java             ← sipariş orkestrasyonu (lock, stok, indirim, kayıt, event fırlatma)
│
├── event/
│   ├── OrderPlacedEvent.java        ← sipariş verildi olay modeli (orderId, userId, userEmail, totalPrice)
│   ├── OrderStatusChangedEvent.java ← durum değişti olay modeli (orderId, userEmail, oldStatus, newStatus)
│   ├── OrderEventListener.java      ← @Async, bildirim adapter'ları (Email + Push) ile müşteriyi bilgilendirir
│   └── InvoiceEventListener.java    ← @Async, INV-2026-XXXXXX formatında e-fatura kesimini simüle eder
│
├── pattern/
│   ├── strategy/
│   │   ├── DiscountStrategy.java            ← indirim arayüzü
│   │   ├── NoDiscountStrategy.java          ← indirimsiz strateji
│   │   ├── PercentageDiscountStrategy.java  ← yüzdelik indirim (%10)
│   │   ├── FixedAmountDiscountStrategy.java ← sabit tutar indirimi (50 TL)
│   │   └── DiscountStrategyFactory.java     ← konfigürasyona veya tipe göre strateji seçici
│   └── adapter/
│       ├── NotificationMessage.java         ← recipient, title, body DTO
│       ├── NotificationSender.java          ← sistemin beklediği bildirim arayüzü
│       ├── EmailNotificationAdapter.java    ← FakeEmailClient'a adapte eder
│       ├── PushNotificationAdapter.java     ← FakePushClient'a adapte eder
│       └── client/
│           ├── FakeEmailClient.java         ← dış email SDK simülasyonu
│           └── FakePushClient.java          ← dış mobil push SDK simülasyonu
│
├── entity/
│   ├── User.java                    ← kullanıcı entity'si
│   ├── Product.java                 ← ürün entity'si (kategori ilişkisi, stock)
│   ├── Category.java                ← kategori entity'si
│   ├── Order.java                   ← sipariş başlığı (totalPrice, status)
│   └── OrderItem.java               ← sipariş kalemi (product, quantity, unitPrice)
│
├── repository/
│   ├── UserRepository.java          ← findByEmail
│   ├── ProductRepository.java       ← findByIdWithLock (Pessimistic Lock 3000ms), findWithFilters
│   ├── CategoryRepository.java      ← existsByName
│   ├── OrderRepository.java         ← findByUser, findByStatus, findByIdAndUserIdWithItems
│   └── OrderItemRepository.java
│
├── dto/
│   ├── CategoryDto.java
│   ├── ProductDto.java
│   ├── OrderDto.java
│   ├── OrderItemDto.java
│   ├── request/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   ├── RefreshTokenRequest.java
│   │   ├── ProductCreateRequest.java
│   │   ├── CategoryCreateRequest.java
│   │   └── OrderCreateRequest.java
│   └── response/
│       └── TokenResponse.java       ← accessToken, refreshToken, tokenType, expiresIn
│
├── exception/
│   ├── ApiException.java                 ← tüm özel hataların temeli (HttpStatus + message)
│   ├── ResourceNotFoundException.java    ← 404 Not Found
│   ├── AlreadyExistsException.java       ← 409 Conflict
│   ├── InsufficientStockException.java   ← 400 Bad Request
│   ├── OrderCancellationException.java   ← 400 Bad Request
│   ├── RateLimitException.java           ← 429 Too Many Requests
│   ├── UnauthorizedException.java        ← 401 Unauthorized
│   ├── ErrorResponse.java                ← standart JSON hata formatı
│   └── GlobalExceptionHandler.java       ← @RestControllerAdvice merkezi
│
├── security/
│   ├── JwtTokenProvider.java             ← JWT token üretimi, doğrulama, claim çıkarma
│   ├── JwtAuthenticationFilter.java      ← her istekte Bearer token ve Redis blacklist kontrolü
│   ├── CustomUserDetailsService.java     ← veritabanından User yükleme
│   ├── JwtAuthenticationEntryPoint.java  ← kimliksiz isteklerde 401 JSON yanıtı
│   └── JwtAccessDeniedHandler.java       ← yetkisiz rol isteklerinde 403 JSON yanıtı
│
├── redis/
│   ├── RefreshTokenService.java          ← Refresh token saklama, rotasyon, doğrulama, silme
│   ├── TokenBlacklistService.java        ← Logout olan token'ları TTL süresince kara listeye alma
│   └── RateLimitService.java             ← IP bazlı 5 denemede 15 dk kilitleme (sliding window)
│
├── mapper/
│   ├── ProductMapper.java                ← MapStruct: Product ↔ ProductDto / ProductCreateRequest
│   ├── CategoryMapper.java               ← MapStruct: Category ↔ CategoryDto
│   └── OrderMapper.java                  ← MapStruct: Order ↔ OrderDto
│
└── enums/
    ├── Role.java                         ← ADMIN, CUSTOMER
    └── OrderStatus.java                  ← PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
```

---

## ⚡ Spring Cache (Redis ile)

### Neden?
Ürün listesi ve kategori listesi en çok okunan endpoint'ler. Her istekte DB'ye gitmeye gerek yok.
Sonuç Redis'te cache'lenir, TTL dolunca veya veri değişince temizlenir.

### Nasıl Çalışır?
```
GET /api/products?categoryId=1
  → Spring Cache Redis'e bakar: "products::1" key'i var mı?
  ✅ Varsa → DB'ye gitmeden Redis'ten döndür
  ❌ Yoksa → DB'den çek → Redis'e yaz (TTL: 10dk) → döndür

POST/PUT/DELETE /api/products
  → @CacheEvict tetiklenir
  → "products" cache tamamen temizlenir
  → Bir sonraki GET isteği DB'den taze veriyi çeker
```

### Kullanılan Anotasyonlar
```java
// ProductService
@Cacheable(value = "products", key = "#categoryId + '-' + #pageable.pageNumber")
public Page<ProductResponse> getProducts(Long categoryId, Pageable pageable) { ... }

@CacheEvict(value = "products", allEntries = true)
public ProductResponse createProduct(ProductRequest request) { ... }

@CacheEvict(value = "products", allEntries = true)
public ProductResponse updateProduct(Long id, ProductRequest request) { ... }

@CacheEvict(value = "products", allEntries = true)
public void deleteProduct(Long id) { ... }

// CategoryService
@Cacheable(value = "categories", key = "'all'")
public List<CategoryDto> getAllCategory() { ... }

@Cacheable(value = "categories", key = "#id")
public CategoryDto getCategoryById(Long id) { ... }

// Yeni kategori: sadece liste cache'i silinir (tekil key'ler hala geçerli)
@CacheEvict(value = "categories", key = "'all'")
public CategoryDto createCategory(CategoryCreateRequest request) { ... }

// Güncelle/Sil: liste + ilgili id'nin cache'i silinir, diğer kategoriler cache'te kalır
@Caching(evict = {
    @CacheEvict(value = "categories", key = "'all'"),
    @CacheEvict(value = "categories", key = "#id")
})
public CategoryDto updateCategory(Long id, CategoryCreateRequest request) { ... }

@Caching(evict = {
    @CacheEvict(value = "categories", key = "'all'"),
    @CacheEvict(value = "categories", key = "#id")
})
public void deleteCategory(Long id) { ... }
```

### Config
```yaml
# application.yml
spring:
  cache:
    type: redis
  data:
    redis:
      time-to-live: 600000   # default 10 dakika (ms)
```

```java
// RedisConfig.java içinde
@Bean
public RedisCacheConfiguration cacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10))
        .disableCachingNullValues()
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer())
        );
}
```

> ⚠️ `@EnableCaching` anotasyonu `RedisConfig` veya ana class'a eklenmeli.

### Cache TTL Tablosu
| Cache Adı | TTL | Ne zaman temizlenir? |
|---|---|---|
| `products` | 10 dakika | Ürün ekle/güncelle/sil |
| `categories` | 30 dakika | Kategori ekle (`all`), güncelle/sil (`all` + `#id`) |

---

## 🛡️ Rate Limiting (Brute Force Koruması)

### Neden?
Biri login endpoint'ine sürekli yanlış şifre deneyebilir (brute force).
Redis ile IP bazlı deneme sayısı tutulur, 5 başarısız denemede 15 dakika bloke.

### Nasıl Çalışır?
```
POST /api/auth/login (IP: 1.2.3.4)
  → Redis'te "login_attempts:1.2.3.4" var mı?

  ❌ Yoksa: key oluştur, değer=1, TTL=15dk
  ✅ Varsa ve değer < 5: değeri artır (INCR)
  ✅ Varsa ve değer >= 5: 429 Too Many Requests dön

  Başarılı login → "login_attempts:1.2.3.4" key'ini sil
```

### Uygulama
```java
// RateLimitService.java (redis/ paketinde)
@Service
public class RateLimitService {
    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_DURATION = 15; // dakika

    public boolean isBlocked(String ip) {
        String key = "login_attempts:" + ip;
        String attempts = redisTemplate.opsForValue().get(key);
        return attempts != null && Integer.parseInt(attempts) >= MAX_ATTEMPTS;
    }

    public void recordFailedAttempt(String ip) {
        String key = "login_attempts:" + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(BLOCK_DURATION));
        }
    }

    public void resetAttempts(String ip) {
        redisTemplate.delete("login_attempts:" + ip);
    }
}
```

```java
// AuthService.login() içinde
public AuthResponse login(LoginRequest request, String clientIp) {
    if (rateLimitService.isBlocked(clientIp)) {
        throw new RateLimitException("Çok fazla başarısız deneme. 15 dakika bekleyin.");
    }
    try {
        // şifre doğrula...
        rateLimitService.resetAttempts(clientIp);   // başarılı → sıfırla
        return buildAuthResponse(user);
    } catch (BadCredentialsException e) {
        rateLimitService.recordFailedAttempt(clientIp);  // başarısız → say
        throw new AuthException("Email veya şifre hatalı");
    }
}
```

```java
// AuthController'da IP almak için
@PostMapping("/login")
public ResponseEntity<?> login(
    @RequestBody LoginRequest request,
    HttpServletRequest httpRequest
) {
    String ip = httpRequest.getRemoteAddr();
    return ResponseEntity.ok(authService.login(request, ip));
}
```

### Exception Eklenecek
| Exception | HTTP Status | Ne zaman? |
|---|---|---|
| `RateLimitException` | 429 Too Many Requests | 5+ başarısız login |

---

## 🛠️ Teknoloji Stack

| Katman | Teknoloji | Notlar |
|---|---|---|
| Backend | Spring Boot 3.x | |
| Auth | Spring Security + JJWT | io.jsonwebtoken:jjwt |
| Redis (Auth) | Spring Data Redis | refresh token + blacklist |
| Redis (Cache) | Spring Cache + Redis | @Cacheable / @CacheEvict |
| Redis (Rate Limit) | Spring Data Redis | Login brute force koruması |
| DB | PostgreSQL | |
| ORM | Spring Data JPA / Hibernate | |
| Validation | Jakarta Bean Validation | spring-boot-starter-validation |
| Event | Spring ApplicationEventPublisher | Native, ek dependency yok |
| API Doküman | Springdoc OpenAPI (Swagger UI) | springdoc-openapi-starter-webmvc-ui |
| Test | JUnit 5 + Mockito + Testcontainers | |
| Build | Maven | |
| Frontend | React + Vite | |

### Spring Initializr Dependency Listesi
```
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Data Redis
- PostgreSQL Driver
- Spring Validation
- Lombok
- Spring Boot DevTools
```
> Springdoc OpenAPI ve JJWT, Testcontainers pom.xml'e manuel eklenir.

### Docker Compose (Geliştirme Ortamı)
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: ecommerce
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```
> `docker compose up -d` ile her ikisi de ayağa kalkar.

---

## 📋 Başlangıç Sırası (Önerilen)

1. Spring Boot projesi oluştur (Spring Initializr)
2. `docker-compose.yml` oluştur, PostgreSQL + Redis ayağa kaldır
3. Entity'leri yaz (`User`, `Category`, `Product`, `Order`, `OrderItem`)
4. Repository'leri yaz (custom sorgular + `@Lock` dahil)
5. Enum'ları yaz (`Role`, `OrderStatus`)
6. Exception sınıfları + `GlobalExceptionHandler` + `ErrorResponse`
   - `RateLimitException` (429) bu adımda eklenir
7. DTO'ları yaz (request + response)
8. Mapper'ları yaz (`ProductMapper`, `CategoryMapper`, `OrderMapper`)
9. Redis config + `@EnableCaching` + Cache TTL ayarları
   - `RefreshTokenService` + `TokenBlacklistService` + **`RateLimitService`**
10. JWT: `JwtTokenProvider` + `JwtAuthenticationFilter` + `CustomUserDetailsService`
11. `SecurityConfig` + `AuthService` + `AuthController`
    - `AuthService.login()` içine **Rate Limiting** entegre et
    - Swagger'ı /login endpoint'inde 429 response olarak belgele
12. Auth endpoint'lerini test et (register → login → brute force → refresh → logout)
13. Swagger config + anotasyonlar
14. `CategoryService` + `CategoryController` (CRUD)
    - **`@Cacheable` / `@CacheEvict`** category'ye ekle
15. Strategy pattern + `DiscountStrategyFactory`
16. Adapter pattern: `NotificationSender` + Email/Push adapter'ları + Fake client'lar
17. `PricingService` + `ProductService` + `ProductController` (CRUD + pagination + filtre)
    - **`@Cacheable` / `@CacheEvict`** product'a ekle
18. Event sınıfları + `OrderEventListener` (NotificationSender'ları çağırır) + `StockEventListener` + `AsyncConfig`
19. `OrderFacade` + `OrderService` + `OrderController` (sipariş verme akışı)
    - `@CacheEvict` → sipariş verilince stok düştüğü için product cache temizle
20. Sipariş iptal + `AdminOrderController` (durum güncelleme + `OrderStatusChangedEvent`)
21. Unit testler (Service + Pattern + Listener + RateLimitService)
22. Repository testleri (`@DataJpaTest`)
23. Controller testleri (`@WebMvcTest` + MockMvc)
24. Integration testleri (Testcontainers + `ConcurrentStockTest`)
25. React frontend
