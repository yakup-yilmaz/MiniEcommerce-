package com.ecommerce.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ===========================================================================================
 * TOKEN BLACKLIST (KARA LİSTE) SERVİSİ
 * ===========================================================================================
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ PROBLEM: JWT STATELESS'TIR — LOGOUT NASIL YAPILIR? │
 * │ │
 * │ JWT'nin doğası gereği, bir token üretildiğinde sunucu onu │
 * │ "hatırlamaz" (stateless). Token kendi içinde tüm bilgiyi taşır: │
 * │ userId, role, expiration... │
 * │ │
 * │ Kullanıcı logout olduğunda ne olur? │
 * │ → Access token hâlâ geçerli! Süresi (15dk) dolana kadar │
 * │ herhangi biri bu token'la API'ye istek atabilir. │
 * │ │
 * │ Bu büyük bir güvenlik açığı! │
 * │ │
 * │ ÇÖZÜM: TOKEN BLACKLIST (KARA LİSTE) │
 * │ → Logout olan token'ı Redis'te "kara listeye" alıyoruz │
 * │ → Her API isteğinde JwtAuthenticationFilter token'ın blacklist'te │
 * │ olup olmadığını kontrol ediyor │
 * │ → Blacklist'teyse → 401 Unauthorized │
 * │ │
 * │ NEDEN REDIS? │
 * │ → Her istek kontrol gerektiriyor → çok hızlı olmalı (RAM) │
 * │ → TTL desteği var → token zaten expire olunca blacklist kaydı da │
 * │ otomatik silinir (gereksiz veri birikmez) │
 * │ → DB'ye yazmak gereksiz yavaş olurdu │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Redis Key Yapısı:
 * Key: "blacklist:{jti}" → örn: "blacklist:123e4567-e89b-12d3..."
 * Value: "revoked" → sabit string (sadece varlık kontrolü)
 * TTL: Token'ın KALAN ömrü → örn: token 10dk sonra expire olacaksa TTL=10dk
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ TTL NEDEN TOKEN'IN KALAN ÖMRÜNE EŞİT? │
 * │ │
 * │ Düşün: Access token'ın toplam ömrü 15 dakika. │
 * │ Kullanıcı 10. dakikada logout oldu → token'ın kalan ömrü 5 dk. │
 * │ │
 * │ 5 dakika sonra token zaten doğal olarak expire olacak. │
 * │ O yüzden blacklist kaydını 5 dk tutmak yeterli! │
 * │ │
 * │ Eğer TTL koymazsak: zamanla milyonlarca eski token Redis'te │
 * │ birikir → bellek şişer → performans düşer. │
 * │ │
 * │ Eğer sabit TTL (15dk) koyarsak: kullanıcı 14. dakikada logout │
 * │ olduysa, blacklist 15dk tutulur ama 1dk sonra token zaten ölecekti │
 * │ → 14dk gereksiz veri tutmuş oluruz. │
 * │ │
 * │ En doğrusu: TTL = token'ın kalan ömrü │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Kullanım Akışı:
 * 1. Kullanıcı logout olur → POST /api/auth/logout
 * 2. AuthService.logout() çağrılır:
 * a) refreshTokenService.delete(userId) → refresh token silinir
 * b) tokenBlacklistService.add(jti, remainingTTL) → token'ın jti'si kara
 * listeye alınır
 * 3. Kullanıcı aynı access token ile istek atar
 * 4. JwtAuthenticationFilter devreye girer:
 * → tokenBlacklistService.isBlacklisted(jti) → true!
 * → 401 Unauthorized döner
 * ===========================================================================================
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {

    // Redis'e erişmek için kullanılan template (RedisConfig'ten inject edilir)
    private final RedisTemplate<String, String> redisTemplate;

    // Redis key prefix'i — tüm blacklist key'leri bu ön ek ile başlar
    // Örn: "blacklist:123e4567-e89b-12d3-a456-426614174000"
    private static final String KEY_PREFIX = "blacklist:";

    // Blacklist'teki value — sadece key'in varlığı kontrol edildiği için
    // value'nun ne olduğu önemli değil, sabit "revoked" yazıyoruz
    private static final String REVOKED_VALUE = "revoked";

    // =====================================================================================
    // ADD — Access Token'ı Kara Listeye Ekle
    // =====================================================================================
    //
    // Ne zaman çağrılır?
    // → Kullanıcı logout yaptığında (AuthService.logout içinde)
    //
    // Parametreler:
    // jti → kara listeye alınacak JWT'nin ID'si (jti claim)
    // remainingTTL → bu token'ın kaç milisaniye sonra expire olacağı
    // (JwtTokenProvider'dan hesaplanır)
    //
    // Ne yapar?
    // → Redis'e "blacklist:123e..." = "revoked" yazar
    // → TTL = token'ın kalan ömrü
    //
    // Kalan ömür nasıl hesaplanır? (JwtTokenProvider tarafında):
    // token expiration zamanı - şu anki zaman = kalan milisaniye
    // Örn: token 14:15'te expire olacak, şu an 14:10 → kalan = 5 dakika
    //
    // set(key, value, timeout):
    // key → "blacklist:123e4567..."
    // value → "revoked"
    // timeout → Duration.ofMillis(remainingTTL) → kalan süre kadar tut
    // =====================================================================================
    public void add(String jti, long remainingTTLInMillis) {
        String key = KEY_PREFIX + jti;

        // Kalan süre 0'dan büyükse blacklist'e ekle
        // Eğer token zaten expire olduysa (kalan süre <= 0) eklemeye gerek yok
        if (remainingTTLInMillis > 0) {
            redisTemplate.opsForValue().set(key, REVOKED_VALUE, Duration.ofMillis(remainingTTLInMillis));
            log.info("🚫 Access token blacklist'e eklendi. TTL: {} saniye", remainingTTLInMillis / 1000);
        } else {
            log.info("ℹ️ Token zaten expire olmuş, blacklist'e eklemeye gerek yok.");
        }
    }

    // =====================================================================================
    // IS BLACKLISTED — Token Kara Listede mi Kontrol Et
    // =====================================================================================
    //
    // Ne zaman çağrılır?
    // → HER API isteğinde! JwtAuthenticationFilter içinde.
    // → Token imzası ve süresi geçerli olsa bile, blacklist kontrolü yapılır.
    //
    // Akış (JwtAuthenticationFilter):
    // 1. Token var mı? → yoksa → 401
    // 2. Token imzası geçerli mi? → değilse → 401
    // 3. Token süresi dolmuş mu? → dolmuşsa → 401
    // 4. Token blacklist'te mi? → ★ BU METOD ★ → evetse → 401
    // 5. Hepsi OK → SecurityContext'e kullanıcıyı set et → devam
    //
    // Nasıl çalışır?
    // Redis'ten "blacklist:123e..." key'ini oku
    // → null dönerse: key yok → token blacklist'te DEĞİL → geçerli
    // → "revoked" dönerse: key var → token blacklist'te → LOGOUT OLMUŞ → 401
    //
    // Performans notu:
    // Bu metod HER istekte çağrılır! Redis RAM'de çalıştığı için
    // ortalama response time: ~1ms. DB'ye sorsaydık: ~5-10ms olurdu.
    // Yoğun trafikte bu fark çok önemli!
    // =====================================================================================
    public boolean isBlacklisted(String jti) {
        String key = KEY_PREFIX + jti;

        // Redis'ten key'in var olup olmadığını kontrol et
        // Boolean.TRUE.equals() ile null-safe kontrol yapıyoruz
        // hasKey(key): key varsa true, yoksa false, hata olursa null
        Boolean exists = redisTemplate.hasKey(key);

        boolean blacklisted = Boolean.TRUE.equals(exists);

        if (blacklisted) {
            log.warn("⛔ Blacklist'teki token ile istek yapılmaya çalışıldı!");
        }

        return blacklisted;
    }
}
