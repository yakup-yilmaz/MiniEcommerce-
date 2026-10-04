package com.ecommerce.security;

import com.ecommerce.redis.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ===========================================================================================
 * JWT AUTHENTICATION FILTER (JWT DOGRULAMA FILTRELEYICISI)
 * ===========================================================================================
 *
 * Bu sinif, her gelen HTTP istegininin "kapidaki guvenlik gorevcisi"dir.
 *
 * Her istek sunucuya geldiginde (GET /products, POST /orders vs.) bu filtre
 * DEVREYE GIRER. Controller'a ulasmadan once su sorulari sorar:
 *
 * 1. Header'da "Authorization: Bearer ..." var mi?
 * -> Yoksa: token yok -> Anonim kullanici (public endpoint'ler gorunur,
 * korunanlara erisemez)
 *
 * 2. Token'in imzasi gecerli mi ve suresi dolmamis mi?
 * -> Degil ise: 401 Unauthorized
 *
 * 3. Token blacklist'te mi? (Logout yapilmis mi?)
 * -> Evet ise: 401 Unauthorized
 *
 * 4. Hepsi OK ise: Email'i token'dan cikart -> Kullaniciyi DB'den bul ->
 * SecurityContext'e kaydet -> Controller'a gecis izni ver
 *
 * NEDEN OncePerRequestFilter?
 * -> Spring'in bazi konfigurasyonlarinda ayni filtre bir istek icin birden
 * fazla
 * calisabilir. OncePerRequestFilter bunu onler: "her istek icin SADECE 1 kez
 * alış."
 *
 * NEDEN SecurityContextHolder?
 * -> Spring Security, "bu istek hangi kullanicidan geldi?" sorusunun cevabini
 * SecurityContextHolder icerisinde tutar.
 * -> Controller icerisinde @AuthenticationPrincipal ile bu bilgiye
 * ulasilabilir.
 * -> Bu filtre set etmezse Spring "kimse yok" dusunur -> korunanlar 403/401
 * verir.
 *
 * ===========================================================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Token'i dogrulamak ve token'dan bilgi cektigin icin bu sinifi kullaniyoruz
    private final JwtTokenProvider jwtTokenProvider;

    // Email -> UserDetails donusumu icin (DB'den kullanicilari bulan sinif)
    private final CustomUserDetailsService userDetailsService;

    // Logout yapilmis token'lari kontrol etmek icin
    private final TokenBlacklistService tokenBlacklistService;

    // =====================================================================================
    // FILTRENIN ANA METODU
    // =====================================================================================
    //
    // Her HTTP istegi bu metoda girer.
    // Metodun sonunda "filterChain.doFilter(request, response)" denilmezse
    // istek Controller'a ULASEMEZ. Bu yuzden basarili path'lerde mutlaka cagirilir.
    //
    // Parametreler:
    // request -> Gelen HTTP istegi (header, body, ip vs. burada)
    // response -> Sunucudan donecek HTTP yaniti
    // filterChain -> Diger filtrelerin zinciri; "devam et" demek icin kullanilir
    // =====================================================================================
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // -----------------------------------------------------------------------
        // ADIM 1: Authorization header'indan token'i cikart
        // -----------------------------------------------------------------------
        // HTTP isteklerinde token su sekilde gelir:
        // Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.xyz
        //
        // extractTokenFromRequest() bu header'i alir ve "Bearer " on ekini atar.
        // Sonuc: sadece "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.xyz" kalir
        // -----------------------------------------------------------------------
        String token = extractTokenFromRequest(request);

        // -----------------------------------------------------------------------
        // ADIM 2: Token yoksa devam et (filter chain'e birak)
        // -----------------------------------------------------------------------
        // Token yoksa bu kullanici anonim. Public endpoint'ler (login, register, GET
        // /products)
        // SecurityConfig'te "permitAll" olarak tanimlanacak, onlara erisebilir.
        // Korunan endpoint'lere (POST /orders, admin endpointler) erismeye calisirsa
        // Spring Security otomatik olarak 401 doner.
        //
        // Burada 401 firlatamazsak cunku SecurityConfig henuz devreye girmedi.
        // Sadece "Benim yapacak isim yok, bir sonraki filtreye gec" diyoruz.
        // -----------------------------------------------------------------------
        if (token == null) {
            filterChain.doFilter(request, response);
            return; // Bu metodun geri kalanini calistirma
        }

        // -----------------------------------------------------------------------
        // ADIM 3: Token gecerli mi? (imza + sure kontrolu)
        // -----------------------------------------------------------------------
        // JwtTokenProvider.validateToken() token'i cozumler.
        // Imza yanlis, sure dolmus veya format bozuksa false doner.
        // -----------------------------------------------------------------------
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Gecersiz veya suresi dolmus JWT token");
            // Devam et ama SecurityContext bos kalacak -> korunanlar 401 verecek
            filterChain.doFilter(request, response);
            return;
        }

        // -----------------------------------------------------------------------
        // ADIM 3.5: Token tipi dogru mu? (GUVENLIK ACIGI KAPATMASI)
        // -----------------------------------------------------------------------
        // Kullanici kurnazlik yapip 7 gunluk refresh token'i gonderdiyse?
        // Imza dogru oldugu icin onceki adimi gecer. Ama tipi "access" olmali!
        // -----------------------------------------------------------------------
        String tokenType = jwtTokenProvider.extractTokenType(token);
        if (!"access".equals(tokenType)) {
            log.warn("Access token bekleniyordu ama baska tip token geldi: {}", tokenType);
            filterChain.doFilter(request, response);
            return;
        }

        // -----------------------------------------------------------------------
        // ADIM 4: Token blacklist'te mi? (Logout kontrolu)
        // -----------------------------------------------------------------------
        // Kullanici logout yapmissa, Access Token'in jti'si Redis blacklist'e yazildi.
        // Burada kontrol ediyoruz: "Bu token daha once iptal edilmis mi?"
        //
        // Neden bu kontrol gerekli?
        // -> JWT stateless. Token suresi dolmadan "gecersiz" sayamayiz.
        // -> Logout sonrasi 15 dakikaya kadar hala gecerli sayilir (sure dolana kadar).
        // -> Blacklist bu acigi kapatir: logout olan token Redis'teyse 401 doner.
        // -----------------------------------------------------------------------
        String jti = jwtTokenProvider.extractJti(token);
        if (tokenBlacklistService.isBlacklisted(jti)) {
            log.warn("Blacklist'teki token ile istek yapilmaya calisildi. JTI: {}", jti);
            filterChain.doFilter(request, response);
            return;
        }

        // -----------------------------------------------------------------------
        // ADIM 5: Token'dan email'i cikart ve kullaniciyi DB'den bul
        // -----------------------------------------------------------------------
        // Token gecerliyse ve blacklist'te degilse, "bu token kimin?" diye soruyoruz.
        // extractEmail() token'in "sub" alanini okuyor.(BALIK HAFIZA)
        //
        // SecurityContextHolder'da hali hazirda bir kullanici varsa tekrar islem yapma.
        // Bu durum normalde olmaz ama savunmaci programlama geregi kontrol ediyoruz.
        // -----------------------------------------------------------------------
        String email = jwtTokenProvider.extractEmail(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Email ile veritabanindan kullanicilari yukle
            // loadUserByUsername() -> UserRepository.findByEmail(email)
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // -------------------------------------------------------------------
            // ADIM 6: Authentication nesnesini olustur ve SecurityContext'e set et
            // -------------------------------------------------------------------
            // Parametreler:
            // 1. principal -> UserDetails nesnesi (kim?)
            // 2. credentials -> null (token bazli auth'da sifre tekrar lazim degil)
            // 3. authorities -> kullanicinin yetkileri (ROLE_ADMIN vs.)
            //
            // setDetails() -> istegin IP, session ID gibi detaylarini ekler (loglama icin)
            // -------------------------------------------------------------------
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities());

            // Istegin ek detaylarini (IP adresi, session ID vs.) authentication'a ekle
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // SecurityContext'e set et: "Bu istek su kullanicidan geldi" de
            // Controller'lar artik bu bilgiye @AuthenticationPrincipal ile erisebilir
            SecurityContextHolder.getContext().setAuthentication(authToken);

            log.debug("Kullanici dogrulandi: {}", email);
        }

        // -----------------------------------------------------------------------
        // ADIM 7: Bir sonraki filtreye (veya Controller'a) gec
        // -----------------------------------------------------------------------
        // Bu satir MUTLAKA cagrilmali. Aksi halde istek asili kalir (sonsuza bekler).
        // SecurityContext set edildikten sonra diger filtreler ve nihayetinde
        // Controller devreye girecek.
        // -----------------------------------------------------------------------
        filterChain.doFilter(request, response);
    }

    // =====================================================================================
    // YARDIMCI METOD: HTTP Header'dan Token'i Cikart
    // =====================================================================================
    //
    // HTTP isteklerinde Authorization header'i su formatta gelir:
    // "Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.xyz"
    //
    // Biz sadece "eyJ..." kismini istiyoruz, "Bearer " on ekini atmak gerekiyor.
    //
    // StringUtils.hasText() -> null veya bos string kontrolu yapar (null-safe)
    // startsWith("Bearer ") -> Dogru formatta mi?
    // substring(7) -> "Bearer " 7 karakterdir, gerisi token
    //
    // null donerse: Header yok veya yanlis format -> ADIM 2'de ele alinacak
    // =====================================================================================
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // Header var mi ve "Bearer " ile baslyor mu?
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // "Bearer " (7 karakter) at, geri kalan token
            return bearerToken.substring(7);
        }

        // Header yok veya yanlis format
        return null;
    }
}
