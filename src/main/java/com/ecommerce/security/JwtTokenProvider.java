package com.ecommerce.security;

import com.ecommerce.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * ===========================================================================================
 * JWT TOKEN PROVIDER (JWT TOKEN URETICI VE DOGRULAYICI)
 * ===========================================================================================
 *
 * Bu sinif, projedeki tum JWT islemlerinin tek adresidir. Uc ana gorevi var:
 *
 * 1) Token URETMEK -> Kullanici login olunca Access Token ve Refresh Token
 * uret.
 * 2) Token DOGRULAMAK -> Gelen token'in imzasi gecerli mi? Suresi dolmus mu?
 * 3) Token'dan BILGI OKUMAK -> "Bu token kimin? Rolu ne? jti'si ne?" diye
 * sormak.
 *
 * JWT (JSON Web Token) NEDIR?
 * Uc parcadan olusan noktali bir metindir:
 * eyJhbGciOiJIUzI1NiJ9 <-- Header (Hangi algoritma?)
 * .
 * eyJ1c2VySWQiOjEsInJvb <-- Payload (Token icindeki veriler: userId, role,
 * jti...)
 * .
 * SflKxwRJSMeKKF2QT4fwpM <-- Signature (Sahtecilik onleme imzasi)
 *
 * Payload icindeki her bir veriye "Claim" denir.
 * Biz su claim'leri token'a koyuyoruz:
 * - "sub" -> subject: kullanicinin email'i
 * - "userId" -> kullanicinin veritabani ID'si
 * - "role" -> kullanicinin rolu (ADMIN veya CUSTOMER)
 * - "jti" -> JWT ID: her token icin benzersiz UUID (blacklist icin kullanilir)
 * - "iat" -> issued at: token'in uretildigi zaman
 * - "exp" -> expiration: token'in bitecegi zaman
 *
 * ===========================================================================================
 */
@Slf4j
@Component
public class JwtTokenProvider {

    // JWT imzalama icin kullanilan gizli anahtar.
    // application.yml'de "jwt.secret" olarak tanimlanir.

    @Value("${jwt.secret}")
    private String secretKey;

    // Access Token'in gecerlilik suresi (milisaniye).
    // application.yml'de "jwt.access-expiration-ms" olarak tanimlanir.
    // Ornek: 900000 -> 15 dakika (15 * 60 * 1000 = 900.000 ms)
    @Value("${jwt.access-expiration-ms}")
    private long accessTokenExpirationMs;

    // Refresh Token'in gecerlilik suresi (milisaniye).
    // application.yml'de "jwt.refresh-expiration-ms" olarak tanimlanir.
    // Ornek: 604800000 -> 7 gun (7 * 24 * 60 * 60 * 1000 ms)
    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenExpirationMs;

    // =====================================================================================
    // YARDIMCI METOD: STRING SECRET -> SecretKey NESNESINE CEVIR
    // =====================================================================================
    //
    // JJWT kutuphanesi ham String'i degil, SecretKey nesnesini ister.
    // Keys.hmacShaKeyFor() -> HMAC-SHA algoritmasi icin uygun anahtar olusturur.

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // =====================================================================================
    // ACCESS TOKEN URET
    // =====================================================================================
    //
    // Ne zaman cagrilir?
    // -> Kullanici basariyla login oldugunda (AuthService.login)
    // -> Refresh token ile yeni token istendiginde (AuthService.refresh)
    //
    // Token icerigi:
    // - subject -> kullanicinin email'i
    // - userId -> veritabani ID'si (API'de kullaniciya erisim icin)
    // - role -> ADMIN veya CUSTOMER
    // - jti -> benzersiz UUID
    // LOGOUT'TA BU JTI BLACKLIST'E YAZILIR!
    // Ornek Redis key: "blacklist:550e8400-e29b-41d4-a716..."
    // - iat -> token'in uretilme zamani
    // - exp -> token'in bitecegi zaman (simdi + 15dk)
    //
    // Jwts.builder() -> JJWT'nin zincirli builder'i
    // .signWith(key) -> token'i imzalar (sahtecilik onlenir)
    // .compact() -> her seyi "aaa.bbb.ccc" formatina cevirir
    // =====================================================================================
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                // Token'in sahibi: kullanicinin email'i
                .subject(user.getEmail())
                // Ozel claim'ler: payload'a gizli bilgiler ekliyoruz
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .claim("type", "access") // Token tipi (GÜVENLİK AÇIĞINI KAPATMAK İÇİN)
                // jti: Her token icin benzersiz kimlik
                // Logout'ta "blacklist:{jti}" seklinde Redis'e kaydedilir
                // Her refresh'te yeni bir Access Token uretilir -> yeni jti
                .id(UUID.randomUUID().toString())
                // Zaman bilgileri
                .issuedAt(now)
                .expiration(expiry)
                // Imzala -> bu imza olmadan token sahte sayilir
                .signWith(getSigningKey())
                // Sonucu "header.payload.signature" String'ine donustur
                .compact();
    }

    // =====================================================================================
    // REFRESH TOKEN URET
    // =====================================================================================
    //
    // Access Token ile ayni mantikta calisir, tek fark:
    // - Sure cok daha uzun (7 gun)
    // - Payload daha az bilgi icerir (role vs. yok, sadece subject + userId)
    // - Bu token API isteklerinde KULLANILMAZ
    // Sadece POST /api/auth/refresh endpoint'inde yeni Access Token almak icin
    // kullanilir
    //
    // Refresh Token ayrica Redis'e kaydedilir (RefreshTokenService.save()).
    // Asil dogrulama Redis uzerinden yapilir:
    // "Bu token Redis'te kayitli mi?" -> evet -> gecerli
    // =====================================================================================
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(user.getEmail()) // Token'ın sahibi
                .claim("userId", user.getId()) // Ekstra bilgiler id gibi
                .claim("type", "refresh") // Token tipi (GÜVENLİK AÇIĞINI KAPATMAK İÇİN)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    // =====================================================================================
    // TOKEN DOGRULA (Gecerli mi?)
    // =====================================================================================
    //
    // Ne zaman cagrilir?
    // -> JwtAuthenticationFilter'da, her API istegininde.
    //
    // Ne kontrol eder?
    // 1. Imza dogru mu? (Bizim secret key ile imzalanmis mi?)
    // 2. Sure dolmus mu? (exp alani gecti mi?)
    // 3. Format bozuk mu? (Nokta sayisi, encoding vs. yanlis mi?)
    //
    // Firlatabilecegi exception'lar:
    // - ExpiredJwtException -> Token suresi dolmus
    // - MalformedJwtException -> Bozuk format
    // - SecurityException -> Imza gecersiz (baskasi olusturmus olabilir)
    // - IllegalArgumentException -> Token bos veya null
    // Hepsi JwtException'dan turettigi icin tek catch'te yakaliyoruz.
    //
    // true donerse -> Token gecerli, JwtAuthenticationFilter devam edebilir
    // false donerse -> Token gecersiz, 401 Unauthorized donecek
    // =====================================================================================
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    // "Bu key ile imzalanmis olmali" demek
                    .verifyWith(getSigningKey())
                    .build()
                    // Token'i coz; imza yanlis veya sure dolmussa burasi exception firlatir
                    .parseSignedClaims(token); // her şeyi burası yapıyor
            return true;
        } catch (JwtException e) {
            log.warn("Gecersiz JWT token: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("JWT token bos veya null: {}", e.getMessage());
            return false;
        }
    }

    // =====================================================================================
    // TOKEN'DAN TUM CLAIMS'LERI OKU (YARDIMCI METOD)
    // =====================================================================================
    //
    // Claims nesnesi bir Map gibi davranir:
    // claims.getSubject() -> email
    // claims.get("userId", Long.class) -> kullanici ID'si
    // claims.get("role", String.class) -> rol
    // claims.getId() -> jti
    // claims.getExpiration() -> bitis zamani
    //
    // DIKKAT: Bu metod sadece validateToken() true donunce cagrilmali!
    // Dogrulanmamis token uzerinde cagrilirsa exception firlatir.
    // =====================================================================================
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                // getPayload() -> JWT'nin ortasindaki kisim (Claims nesnesi)
                .getPayload();
    }

    // =====================================================================================
    // TOKEN'DAN EMAIL OKU
    // =====================================================================================
    //
    // Ne zaman kullanilir?
    // -> JwtAuthenticationFilter'da, token gecerliyse kim oldugunu anlamak icin.
    // -> Ornek akis: token -> email -> userDetailsService.loadUserByUsername(email)
    //
    // .getSubject() -> JWT'deki "sub" alani. Biz oraya email'i koymustur.
    // =====================================================================================
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    // =====================================================================================
    // TOKEN'DAN USER ID OKU
    // =====================================================================================
    //
    // Ne zaman kullanilir?
    // -> AuthService.logout() -> refreshTokenService.delete(userId) icin
    // -> AuthService.refresh() -> refreshTokenService.find(userId) icin
    //
    // Neden DB'ye gitmiyoruz?
    // -> userId zaten token'in icinde ("userId" claim'i). Imzayi dogruladiysak
    // bu bilgiye guvenebiliriz -> ekstra SELECT sorgusuna gerek yok.
    //
    // Neden Number.class?
    // -> JSON'da sayilar tip bilgisi tasimaz. JJWT kucuk sayilari (1, 2, 3...)
    // Integer olarak, buyukleri Long olarak okuyabilir.
    // -> Number ikisinin de ortak atasidir -> .longValue() ile guvenle Long'a ceviririz.
    // =====================================================================================
    public Long extractUserId(String token) {
        Number userId = extractAllClaims(token).get("userId", Number.class);
        return userId != null ? userId.longValue() : null;
    }

    // =====================================================================================
    // TOKEN'DAN JTI OKU
    // =====================================================================================
    //
    // Ne zaman kullanilir?
    // -> Logout sirasinda, Access Token'i blacklist'e eklemek icin.
    // -> AuthService.logout() -> tokenBlacklistService.add(jti, remainingTTL)
    //
    // Neden tam token yerine jti kullaniyoruz?
    // -> Access Token 300+ karakterlik bir string. Redis'te saklamak bellek israf.
    // -> jti ise kisa bir UUID (36 karakter).
    // -> Redis key: "blacklist:550e8400-..." -> cok daha verimli!
    //
    // .getId() -> Claims'deki "jti" alani
    // =====================================================================================
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    // =====================================================================================
    // TOKEN'DAN KALAN SURE HESAPLA (Milisaniye)
    // =====================================================================================
    //
    // Ne zaman kullanilir?
    // -> Logout sirasinda, blacklist'e kaydedilecek TTL'yi bulmak icin.
    // -> AuthService.logout() -> tokenBlacklistService.add(jti, remainingMillis)
    //
    // Formul:
    // token'in bitis zamani - su anki zaman = kalan milisaniye
    // Ornek: token 14:15'te bitiyor, su an 14:10 -> kalan = 300.000 ms (5 dk)
    //
    // Neden Math.max ile 0'la karsilastiriyoruz?
    // -> Token coktan expire olmussaa hesaplama negatif doner.
    // -> Negatif TTL ile Redis'e yazamazsin -> en az 0 donuyoruz.
    // -> TokenBlacklistService'de de "<=0 ise yazma" kontrolu var (cift guvenlik).
    // =====================================================================================
    public long extractRemainingMillis(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return Math.max(0, expiration.getTime() - System.currentTimeMillis());
    }

    // =====================================================================================
    // TOKEN'DAN TIP OKU (ACCESS MI REFRESH MI?)
    // =====================================================================================
    //
    // Ne zaman kullanilir?
    // -> Güvenlik acigini kapatmak icin.
    // -> Filter sadece "access", AuthService.refresh ise sadece "refresh" kabul eder.
    // =====================================================================================
    public String extractTokenType(String token) {
        return extractAllClaims(token).get("type", String.class);
    }
}
