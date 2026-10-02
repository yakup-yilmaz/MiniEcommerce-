package com.ecommerce.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ===========================================================================================
 * REFRESH TOKEN SERVİSİ
 * ===========================================================================================
 *
 * Bu sınıf, kullanıcının refresh token'ını Redis'te yönetir.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ REFRESH TOKEN NEDİR? │
 * │ │
 * │ JWT sisteminde 2 tür token vardır: │
 * │ │
 * │ 1) Access Token → API isteklerinde kullanılır (Authorization │
 * │ header'ında). Kısa ömürlüdür: 15 dakika. │
 * │ Süresi dolunca kullanıcı tekrar login olmak zorunda kalır. │
 * │ │
 * │ 2) Refresh Token → Yeni access token almak için kullanılır. │
 * │ Uzun ömürlüdür: 7 gün. │
 * │ Access token süresi dolunca, kullanıcı refresh token ile │
 * │ yeni bir access token alır. Böylece tekrar login olmak gerekmez.│
 * │ │
 * │ Neden Redis'te saklıyoruz? │
 * │ - Logout yapıldığında refresh token'ı silebilmek için │
 * │ - Refresh isteği gelince token'ın hâlâ geçerli olduğunu doğrulamak │
 * │ - Redis'in TTL özelliği ile 7 gün sonra otomatik silinmesi │
 * │ - DB'ye yazmak gereksiz yavaş olurdu (Redis RAM'de çalışır) │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Redis Key Yapısı:
 * Key: "refresh_token:{userId}" → örn: "refresh_token:1"
 * Value: refresh token string → örn: "eyJhbGciOiJIUz..."
 * TTL: 7 gün (604800 saniye) → süre dolunca Redis otomatik siler
 *
 * Kullanım Senaryosu:
 * Login → save(1, "eyJ...") → Redis'e yazar
 * Refresh → find(1) → "eyJ..." → Redis'ten okur, eşleşiyor mu kontrol eder
 * Logout → delete(1) → Redis'ten siler, artık refresh yapılamaz
 *
 * ===========================================================================================
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    // RedisConfig'te tanımladığımız RedisTemplate<String, String> buraya inject
    // edilir
    // Bu template üzerinden Redis'e set/get/delete işlemleri yapılır
    private final RedisTemplate<String, String> redisTemplate;

    // Redis key'lerinde kullanılan prefix (ön ek)

    private static final String KEY_PREFIX = "refresh_token:";

    // Refresh token'ın Redis'te yaşama süresi: 7 gün
    // Duration.ofDays(7) → 604800 saniye
    // Bu süre dolunca Redis key'i otomatik olarak siler (expire olur)
    private static final Duration TTL = Duration.ofDays(7);

    // =====================================================================================
    // SAVE — Refresh Token'ı Redis'e Kaydet
    // =====================================================================================
    //
    // Ne zaman çağrılır?
    // → Kullanıcı başarıyla login olduğunda (AuthService.login içinde)
    //
    // Ne yapar?
    // → Redis'e "refresh_token:1" = "eyJhbGci..." şeklinde yazar
    // → 7 günlük TTL set eder
    //
    // Neden önceki token üzerine yazılır?
    // → Kullanıcı farklı cihazdan login olursa eski refresh token geçersiz olur
    // → Bu "tek oturum" politikası: aynı anda sadece 1 geçerli refresh token //
    // opsForValue() ne demek?
    // → Redis'in "String" veri tipini kullan demek
    // → Redis'te birden fazla veri tipi var: String, List, Set, Hash, SortedSet
    // → Biz basit key-value sakladığımız için String (opsForValue) kullanıyoruz
    //
    // set(key, value, timeout) parametreleri:
    // key → "refresh_token:1"
    // value → "eyJhbGciOiJIUz..."
    // timeout → 7 gün (bu süre sonunda Redis otomatik siler)
    // =====================================================================================
    public void save(Long userId, String refreshToken) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken, TTL);
        log.info("✅ Refresh token Redis'e kaydedildi. UserId: {}, TTL: {} gün", userId, TTL.toDays());
    }

    // =====================================================================================
    // FIND — Refresh Token'ı Redis'ten Oku
    // =====================================================================================
    //
    // Ne zaman çağrılır?
    // → Kullanıcı yeni access token almak istediğinde (POST /api/auth/refresh)
    // → AuthService.refresh() içinde
    //
    // Ne yapar?
    // → Redis'ten "refresh_token:1" key'inin değerini okur
    // → Dönen değer: refresh token string veya null (key yoksa/expire olduysa)
    //
    // Nasıl kullanılır?
    // String savedToken = refreshTokenService.find(userId);
    // if (savedToken == null) → "Oturum süresi dolmuş, tekrar login olun"
    // if (!savedToken.equals(requestToken)) → "Geçersiz refresh token"
    // else → yeni access token üret
    //
    // get(key) → Redis'ten değeri getirir, key yoksa null döner
    // =====================================================================================
    public String find(Long userId) {
        String key = KEY_PREFIX + userId;
        String token = redisTemplate.opsForValue().get(key);

        if (token != null) {
            log.info("🔍 Refresh token Redis'te bulundu. UserId: {}", userId);
        } else {
            log.warn("⚠️ Refresh token Redis'te bulunamadı (expire olmuş veya silinmiş). UserId: {}", userId);
        }

        return token;
    }

    // =====================================================================================
    // DELETE — Refresh Token'ı Redis'ten Sil
    // =====================================================================================
    //
    // Ne zaman çağrılır?
    // → Kullanıcı logout yaptığında (POST /api/auth/logout)
    // → AuthService.logout() içinde
    //
    // Ne yapar?
    // → Redis'ten "refresh_token:1" key'ini siler
    // → Artık bu kullanıcı refresh yapamaz → yeni access token alamaz
    // → Tekrar login olmak zorunda kalır
    //
    // Boolean delete(key):
    // → true: key vardı ve silindi
    // → false: key zaten yoktu
    //
    // Bu metod logout akışının YARISIDIR:
    // Logout = refresh token sil + access token'ı blacklist'e ekle
    // (Blacklist kısmını TokenBlacklistService halleder)
    // =====================================================================================
    public void delete(Long userId) {
        String key = KEY_PREFIX + userId;
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("🗑️ Refresh token Redis'ten silindi. UserId: {}", userId);
        } else {
            log.warn("⚠️ Silinecek refresh token bulunamadı. UserId: {}", userId);
        }
    }
}
