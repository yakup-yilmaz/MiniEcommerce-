package com.ecommerce.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ===========================================================================================
 *  REDİS KONFİGÜRASYON SINIFI
 * ===========================================================================================
 *
 *  Bu sınıf projede Redis'in nasıl kullanılacağını tanımlar.
 *  İki ana görev yapar:
 *
 *  1) RedisTemplate Bean'i → RefreshTokenService, TokenBlacklistService, RateLimitService
 *     gibi sınıfların Redis'e doğrudan veri yazıp okumasını sağlar.
 *     (Key-Value şeklinde: redisTemplate.opsForValue().set("key", "value"))
 *
 *  2) RedisCacheManager Bean'i → Spring Cache mekanizmasının (@Cacheable, @CacheEvict)
 *     Redis'i arka planda cache deposu olarak kullanmasını sağlar.
 *     (ProductService ve CategoryService'deki cache anotasyonları bu manager ile çalışır)
 *
 *  @Configuration → Spring'e "bu sınıf ayar sınıfıdır, içindeki @Bean metotlarını oku" der
 *  @EnableCaching → Spring Cache mekanizmasını aktif eder (@Cacheable, @CacheEvict çalışır)
 *
 *  NOT: @EnableCaching olmazsa @Cacheable anotasyonları hiçbir işe yaramaz!
 * ===========================================================================================
 */
@Configuration
@EnableCaching
public class RedisConfig {

    // =====================================================================================
    //  REDIS TEMPLATE BEAN'İ
    // =====================================================================================
    //
    //  RedisTemplate, Redis'e doğrudan erişmek için kullanılan Spring sınıfıdır.
    //  Düşün ki Redis bir dolap, RedisTemplate ise o dolabın anahtarı.
    //
    //  Bu projede RedisTemplate'i 3 sınıf kullanıyor:
    //    - RefreshTokenService  → refresh token kaydetmek/okumak/silmek için
    //    - TokenBlacklistService → logout olan token'ı kara listeye almak için
    //    - RateLimitService     → başarısız login denemelerini saymak için
    //
    //  RedisTemplate<String, String> demek:
    //    - Key (anahtar) tipi: String  → örn: "refresh_token:1", "blacklist:eyJ..."
    //    - Value (değer) tipi: String  → örn: "abc123tokenvalue", "revoked", "3"
    //
    //  Serializer nedir?
    //    Redis'e yazdığımız Java String'leri byte dizisine çevrilmeli (serialize).
    //    Redis'ten okuduğumuz byte dizileri tekrar Java String'e çevrilmeli (deserialize).
    //    StringRedisSerializer tam bunu yapar: String ↔ byte[]
    //
    //  @Bean → Spring konteynırına "bu metotun döndürdüğü objeyi sakla, isteyen yere inject et" der
    //  RedisConnectionFactory → Spring Boot otomatik oluşturur (application.yml'deki host:port bilgisiyle)
    // =====================================================================================
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, String> template = new RedisTemplate<>();

        // Redis'e bağlanmak için gerekli olan connection factory'yi set et
        // Bu factory, application.yml'deki spring.data.redis.host ve port bilgisini kullanır
        template.setConnectionFactory(connectionFactory);

        // Key'leri (anahtarları) serialize etmek için StringRedisSerializer kullan
        // Örn: "refresh_token:1" → Redis'te düzgün string olarak görünür
        // Bu olmadan key'ler \xac\xed\x00 gibi okunamaz byte dizisi olarak yazılır!
        template.setKeySerializer(new StringRedisSerializer());

        // Value'ları (değerleri) de StringRedisSerializer ile serialize et
        // Örn: "eyJhbGciOiJIUz..." → Redis'te düzgün string olarak görünür
        template.setValueSerializer(new StringRedisSerializer());

        // Hash key ve value serializer'ları da ayarla (şu an kullanmasak da best practice)
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        // Tüm ayarları uygula
        template.afterPropertiesSet();

        return template;
    }

    // =====================================================================================
    //  REDIS CACHE MANAGER BEAN'İ
    // =====================================================================================
    //
    //  Spring Cache (@Cacheable, @CacheEvict) mekanizması arka planda bir CacheManager
    //  kullanır. Biz burada RedisCacheManager oluşturarak Spring'e
    //  "cache verilerini Redis'e yaz" diyoruz.
    //
    //  Neden özel config lazım?
    //    - Her cache bölgesinin (products, categories) farklı TTL'i olabilir
    //    - Null değerleri cache'lememek istiyoruz (boş sonuçları saklama)
    //    - Value'ları JSON olarak saklamak istiyoruz (debug'da okunabilir olsun)
    //
    //  İki farklı cache bölgesi var:
    //    "products"   → TTL: 10 dakika (ürünler sık güncellenir)
    //    "categories" → TTL: 30 dakika (kategoriler nadiren değişir)
    // =====================================================================================
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // --- DEFAULT (varsayılan) cache ayarları ---
        // Eğer bir cache adı için özel ayar tanımlanmamışsa bu ayarlar geçerli olur
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // Varsayılan TTL: 10 dakika
                // TTL (Time To Live) = verinin Redis'te ne kadar yaşayacağı
                // 10dk sonra otomatik silinir, bir sonraki istek DB'den taze veriyi çeker
                .entryTtl(Duration.ofMinutes(10))

                // Null değerleri cache'leme!
                // Örn: Varolmayan bir categoryId ile sorgu yapıldığında sonuç null döner
                // Bu null'ı cache'lersek, kategori oluşturulsa bile cache'ten null gelmeye devam eder
                .disableCachingNullValues()

                // Value serializer: JSON formatında kaydet
                // Bu sayede Redis'te saklanan veri {"id":1,"name":"Laptop","price":15999} şeklinde
                // okunabilir olur. Debug yaparken Redis CLI'da direkt görebilirsin.
                // GenericJackson2JsonRedisSerializer → Java objesini JSON'a çevirir
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer())
                );

        // --- HER CACHE BÖLGESİ İÇİN ÖZEL AYARLAR ---
        // "products" ve "categories" cache'lerinin farklı TTL'leri var
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // "products" cache'i: 10 dakika TTL
        // Ürün listesi sık değişebilir (stok düşer, fiyat güncellenir)
        // Bu yüzden 10dk yeterli bir süre
        cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // "categories" cache'i: 30 dakika TTL
        // Kategoriler nadiren değişir (Elektronik, Giyim vs. sabit kalır)
        // Bu yüzden daha uzun süre cache'te tutabiliriz
        cacheConfigurations.put("categories", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // RedisCacheManager oluştur ve döndür
        return RedisCacheManager.builder(connectionFactory)
                // Özel ayar tanımlanmayan cache'ler için varsayılan config
                .cacheDefaults(defaultConfig)
                // Her cache bölgesi için özel config'leri uygula
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
