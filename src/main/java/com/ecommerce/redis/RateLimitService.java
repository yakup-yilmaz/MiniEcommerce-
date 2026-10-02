package com.ecommerce.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ===========================================================================================
 * RATE LIMIT (HIZI SINIRLANDIRMA) SERVİSİ — BRUTE FORCE KORUMASI
 * ===========================================================================================
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ PROBLEM: BRUTE FORCE SALDIRISI NEDİR? │
 * │ │
 * │ Saldırgan, bir kullanıcının şifresini bulmak için binlerce │
 * │ kombinasyonu otomatik olarak dener: │
 * │ │
 * │ │
 * │ ÇÖZÜM: IP BAZLI RATE LIMITING │
 * │ → Aynı IP'den 5 başarısız deneme olursa → 15 dakika bloke et │
 * │ → Başarılı login olursa → sayacı sıfırla │
 * │ → 15 dakika sonra → bloke otomatik kalkar (Redis TTL) │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * NEDEN REDIS?
 * ─────────────
 * 1. Hız: Her login isteğinde kontrol yapılıyor → Redis (RAM) ~1ms
 * 2. TTL: 15 dk sonra key otomatik silinir → bloke otomatik kalkar
 * 3. Atomik INCR: Birden fazla istek aynı anda gelse bile sayaç doğru artar
 * (Redis tek-thread çalışır, race condition olmaz!)
 * 4. Kalıcılık: Uygulama restart olsa bile sayaç kaybolmaz
 * 5. Paylaşım: Birden fazla uygulama instance'ı aynı Redis'i kullanabilir
 *
 * Redis Key Yapısı:
 * Key: "login_attempts:{ip}" → örn: "login_attempts:192.168.1.100"
 * Value: sayaç (integer string) → "1", "2", "3", "4", "5"
 * TTL: 15 dakika → ilk başarısız denemede set edilir
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ SENARYO: Saldırgan IP 1.2.3.4'ten
 * │ │
 * │ 1. deneme (yanlış şifre): │
 * │ → INCR "login_attempts:1.2.3.4" → count=1, TTL=15dk │
 * │ │
 * │ 2. deneme (yanlış şifre): │
 * │ → INCR "login_attempts:1.2.3.4" → count=2 │
 * │ │
 * │ 3. deneme (yanlış şifre): │
 * │ → INCR "login_attempts:1.2.3.4" → count=3 │
 * │ │
 * │ 4. deneme (yanlış şifre): │
 * │ → INCR "login_attempts:1.2.3.4" → count=4 │
 * │ │
 * │ 5. deneme (yanlış şifre): │
 * │ → INCR "login_attempts:1.2.3.4" → count=5 │
 * │ │
 * │ 6. deneme: │
 * │ → isBlocked("1.2.3.4") → count=5 >= MAX → 429 Too Many Requests│
 * │ → "Çok fazla başarısız deneme. 15 dakika bekleyin." │
 * │ │
 * │ ... 15 dakika geçti → TTL doldu → key silindi → tekrar deneye- │
 * │ bilir (ama yine 5 hakkı var) │
 * │ │
 * │ Başka senaryo: Yakup doğru şifreyi girerse │
 * │ → resetAttempts("1.2.3.4") → key silinir → sayaç sıfırlanır │
 * └─────────────────────────────────────────────────────────────────────┘
 * ===========================================================================================
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

    // Redis'e erişmek için template (RedisConfig'ten inject edilir)
    private final RedisTemplate<String, String> redisTemplate;

    // Redis key prefix'i — tüm rate limit key'leri bu ön ek ile başlar
    private static final String KEY_PREFIX = "login_attempts:";

    // Maksimum başarısız deneme sayısı
    // 5. denemeden sonra IP bloke olur
    private static final int MAX_ATTEMPTS = 5;

    // Bloke süresi: 15 dakika
    // İlk başarısız denemede TTL set edilir
    // 15 dakika sonra key otomatik silinir → bloke kalkar
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    // =====================================================================================
    // IS BLOCKED — Bu IP Bloke Edilmiş mi?
    // =====================================================================================
    //
    // Ne zaman çağrılır?
    // → Her login isteğinin EN BAŞINDA (AuthService.login içinde)
    // → Şifre kontrolünden ÖNCE çağrılır (şifre kontrolü yapmaya bile gerek yok)
    //
    // Akış:
    // 1. Redis'ten "login_attempts:1.2.3.4" key'inin değerini oku
    // 2. Değer null ise → hiç başarısız deneme yok → bloke DEĞİL
    // 3. Değer < 5 ise → henüz limit aşılmadı → bloke DEĞİL
    // 4. Değer >= 5 ise → BLOKE → RateLimitException fırlat (429)
    //
    // NOT: Redis'te value String olarak tutulur ("3", "5" gibi)
    // Integer.parseInt ile sayıya çevrilir
    // =====================================================================================
    public boolean isBlocked(String ip) {
        String key = KEY_PREFIX + ip;
        String attempts = redisTemplate.opsForValue().get(key);

        // Key yoksa (null) → hiç başarısız deneme yok → bloke değil
        if (attempts == null) {
            return false;
        }

        // String'i int'e çevir ve MAX_ATTEMPTS ile karşılaştır
        boolean blocked = Integer.parseInt(attempts) >= MAX_ATTEMPTS;

        if (blocked) {
            log.warn("🚫 IP bloke edildi ({}+ başarısız deneme): {}", MAX_ATTEMPTS, ip);
        }

        return blocked;
    }

    // =====================================================================================
    // RECORD FAILED ATTEMPT — Başarısız Denemeyi Kaydet
    // =====================================================================================
    //
    // Ne zaman çağrılır?
    // → Kullanıcı yanlış şifre girdiğinde (AuthService.login içinde, catch
    // bloğunda)
    //
    // Ne yapar?
    // → Redis'teki sayacı 1 artırır (INCR komutu)
    // → İlk denemeyse (count=1) TTL'i 15 dakika olarak set eder
    //
    // INCR komutu nedir?
    // → Redis'in atomik artırma komutudur
    // → Key yoksa: key oluşturur, değeri 1 yapar, 1 döndürür
    // → Key varsa: mevcut değeri 1 artırır, yeni değeri döndürür
    // → ATOMİK: Aynı anda 100 istek gelse bile sayaç doğru artar!
    // (Redis single-threaded çalışır, komutları sırayla işler)
    //
    // Neden TTL sadece count=1'de set ediliyor?
    // → INCR her çağrıldığında TTL sıfırlanmasın diye!
    // → İlk denemede 15dk TTL başlar
    // → 2., 3., 4. denemelerde TTL dokunulmaz (geriye kalan süre devam eder)
    // → Eğer her denemede TTL sıfırlansaydı, saldırgan sürekli deneyerek
    // blokeyi süresiz uzatabilirdi
    //
    // increment(key) dönüş değeri:
    // → Long: artırma sonrası yeni değer
    // → Key yoksa 1 döner (yeni key oluşturuldu)
    // → Key varsa eski değer + 1 döner
    // =====================================================================================
    public void recordFailedAttempt(String ip) {
        String key = KEY_PREFIX + ip;

        // Sayacı atomik olarak 1 artır
        // Key yoksa otomatik oluşturur ve değeri 1 yapar
        Long count = redisTemplate.opsForValue().increment(key);

        // İlk başarısız deneme ise TTL'i ayarla
        // count == 1 demek: key yeni oluşturuldu, ilk deneme
        if (count != null && count == 1) {
            redisTemplate.expire(key, BLOCK_DURATION);
            log.info("⚠️ İlk başarısız login denemesi. IP: {}, TTL: {} dakika", ip, BLOCK_DURATION.toMinutes());
        } else {
            log.info("⚠️ Başarısız login denemesi #{} — IP: {}", count, ip);
        }

        // 5. denemeye ulaşıldıysa uyarı logla
        if (count != null && count >= MAX_ATTEMPTS) {
            log.warn("🔒 IP bloke eşiğine ulaştı! IP: {}, Deneme: {}/{}", ip, count, MAX_ATTEMPTS);
        }
    }

    // =====================================================================================
    // RESET ATTEMPTS — Başarılı Login Sonrası Sayacı Sıfırla
    // =====================================================================================
    //
    // Ne zaman çağrılır?
    // → Kullanıcı BAŞARILI login olduğunda (AuthService.login içinde)
    //
    // Ne yapar?
    // → Redis'ten "login_attempts:1.2.3.4" key'ini siler
    // → Sayaç sıfırlanır
    //
    // Neden önemli?
    // → Diyelim kullanıcı 3 kez yanlış şifre girdi, sonra doğrusunu girdi
    // → Eğer sayacı sıfırlamazsak, sonraki 2 yanlış denemede bloke olur
    // → Bu kullanıcı için haksız bir durum olur
    // → Başarılı login = "bu gerçek kullanıcı" demek → sayacı temizle
    // =====================================================================================
    public void resetAttempts(String ip) {
        String key = KEY_PREFIX + ip;
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("✅ Başarılı login — deneme sayacı sıfırlandı. IP: {}", ip);
        }
    }
}
