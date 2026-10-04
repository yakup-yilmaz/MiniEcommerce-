package com.ecommerce.service;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RefreshTokenRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.entity.User;
import com.ecommerce.enums.Role;
import com.ecommerce.exception.AlreadyExistsException;
import com.ecommerce.exception.RateLimitException;
import com.ecommerce.exception.UnauthorizedException;
import com.ecommerce.redis.RateLimitService;
import com.ecommerce.redis.RefreshTokenService;
import com.ecommerce.redis.TokenBlacklistService;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ===========================================================================================
 * AUTH SERVICE
 * ===========================================================================================
 *
 * Simdiye kadar yazdigimiz tum "altyapi" parcalari BURADA bir araya geliyor:
 *
 * ┌──────────────────────┬──────────────────────────────────────────────────┐
 * │ Parca │ AuthService'te ne icin kullaniliyor? │
 * ├──────────────────────┼──────────────────────────────────────────────────┤
 * │ PasswordEncoder │ register: sifreyi BCrypt ile hashle │
 * │ AuthenticationManager│ login : SIFRE DOGRU MU? kontrolu (asil yer!) │
 * │ JwtTokenProvider │ token uret / token'dan jti, userId, sure oku │
 * │ RefreshTokenService │ refresh token'i Redis'e yaz / oku / sil │
 * │ TokenBlacklistService│ logout: access token'in jti'sini kara listeye al │
 * │ RateLimitService │ login: brute-force korumasi (IP basina 5 hak) │
 * │ UserRepository │ kullanici var mi? kaydet / bul │
 * └──────────────────────┴──────────────────────────────────────────────────┘
 *
 * HATIRLATMA — Filter ile AuthService arasindaki fark:
 * -> JwtAuthenticationFilter : HER istekte calisir. Sifre BILMEZ. Sadece
 * token'in
 * imzasina bakar, email'i alir, UserDetails'i context'e koyar.
 * -> AuthService.login() : SADECE login'de calisir. Sifreyi BURADA dogrulariz
 * ve basariliysa token URETIRIZ.
 * Yani: Login = "token al", Filter = "token goster".
 *
 * Dort metod var:
 * register() -> yeni kullanici + token cifti
 * login() -> sifre kontrolu + token cifti
 * refresh() -> eski refresh token ile yeni token cifti
 * logout() -> access token'i kara listeye al + refresh token'i sil
 * ===========================================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RateLimitService rateLimitService;

    // =====================================================================================
    // 1) REGISTER — YENI KULLANICI KAYDI
    // =====================================================================================
    //
    // Akis:
    // 1. Bu email ile kullanici var mi? -> varsa 409 Conflict
    // (AlreadyExistsException)
    // 2. Sifreyi BCrypt ile hashle -> "123456" -> "$2a$10$Xy..."
    // 3. Rolu CUSTOMER olarak ata -> kullanici rol SECEMEZ (guvenlik!)
    // 4. DB'ye kaydet
    // 5. Token cifti uret ve don -> kayit olur olmaz giris yapmis sayilir
    //
    // Neden existsByEmail ile once kontrol ediyoruz? DB'de zaten unique constraint
    // var.
    // -> Constraint ihlali DataIntegrityViolationException firlatir;
    // GlobalExceptionHandler
    // onu 500 olarak doner ve mesaj anlasilmaz olur. Once kontrol edip anlamli bir
    // 409 mesaji donmek kullanici deneyimi icin daha iyi. (Constraint yine de son
    // savunma hatti olarak kalir: ayni anda gelen iki istek icin.)
    //
    // =====================================================================================
    @Transactional
    public TokenResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AlreadyExistsException("Bu email adresi zaten kayıtlı: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                // ★ ASLA request.getPassword()'u direkt kaydetme! ★
                // encode() -> her cagrildiginda farkli hash uretir (salt), ama
                // login'de matches() ile dogru eslestirilir.
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();

        // save() sonrasi user.getId() dolar (IDENTITY ile DB uretti).
        // Token'a userId koyacagimiz icin save'den SONRA token uretmeliyiz.
        User savedUser = userRepository.save(user);
        log.info("Yeni kullanıcı kaydedildi. id={}, email={}", savedUser.getId(), savedUser.getEmail());

        return issueTokens(savedUser);
    }

    // =====================================================================================
    // 2) LOGIN — SIFRE DOGRULAMA BURADA!
    // =====================================================================================
    //
    // Akis:
    // 1. Bu IP bloke mu? (RateLimitService) -> evet: 429, sifreye BAKMA bile
    // 2. authenticationManager.authenticate(...) -> sifre kontrolu
    // 2a. Yanlis: deneme sayacini artir -> 401
    // 2b. Dogru : sayaci sifirla
    // 3. Kullaniciyi DB'den al, token cifti uret, refresh'i Redis'e yaz
    //
    // authenticate() ICERIDE NE YAPIYOR? (Sihir yok, adim adim)
    // new UsernamePasswordAuthenticationToken(email, sifre)
    // -> Bu hali "DOGRULANMAMIS" bir talep
    // -> DaoAuthenticationProvider'a devreder:
    // a) CustomUserDetailsService.loadUserByUsername(email) -> DB'den UserDetails
    // b) passwordEncoder.matches("123456", "$2a$10$Xy...") -> BCrypt karsilastirma
    // c) Eslesirse -> "DOGRULANMIS" Authentication doner
    // Eslesmezse -> BadCredentialsException firlatir
    //
    // Not: Email DB'de yoksa da BadCredentialsException gelir (UsernameNotFound
    // degil).
    // Spring bunu bilerek yapar: "boyle bir kullanici yok" demek saldirgana hangi
    // email'lerin kayitli oldugunu sizdirir. Tek mesaj: "Email veya sifre hatali".
    //
    // SORU: Dogrulanmis Authentication'i SecurityContextHolder'a koymuyor muyuz?
    // -> HAYIR, gerek yok. Stateless sistemde context istek bitince silinir.
    // -> Login'in ciktisi "context" degil, TOKEN'dir. Kullanici sonraki isteklerde
    // token'i gosterir, Filter da her seferinde context'i yeniden doldurur.
    // =====================================================================================
    public TokenResponse login(LoginRequest request, String clientIp) {

        // ADIM 1: Brute-force kontrolu — sifre kontrolunden ONCE
        if (rateLimitService.isBlocked(clientIp)) {
            throw new RateLimitException("Çok fazla başarısız giriş denemesi. Lütfen 15 dakika sonra tekrar deneyin.");
        }

        // ADIM 2: Sifre kontrolu (asil dogrulama)
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            // Yanlis sifre (veya olmayan email) -> sayaci 1 artir
            rateLimitService.recordFailedAttempt(clientIp);
            log.warn("Başarısız login denemesi. email={}, ip={}", request.getEmail(), clientIp);
            // BadCredentialsException'i oldugu gibi birakirsak GlobalExceptionHandler'da
            // karsiligi olmadigi icin 500 doner. Kendi 401 exception'imiza ceviriyoruz.
            throw new UnauthorizedException("Email veya şifre hatalı");
        }

        // ADIM 2b: Basarili -> bu IP'nin hata sayacini temizle
        rateLimitService.resetAttempts(clientIp);

        // ADIM 3: Token uretmek icin User entity lazim (id ve role icin).
        // authenticate() basarili oldugu icin kullanici kesin var; orElseThrow sadece
        // savunma.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email veya şifre hatalı"));

        log.info("Kullanıcı giriş yaptı. id={}, ip={}", user.getId(), clientIp);
        return issueTokens(user);
    }

    // =====================================================================================
    // 3) REFRESH — ACCESS TOKEN SURESI DOLUNCA YENISINI AL
    // =====================================================================================
    //
    // Akis:
    // 1. Refresh token'in imzasi/suresi gecerli mi? (JwtTokenProvider)
    // 2. Icinden userId'yi oku
    // 3. Redis'teki "refresh_token:{userId}" ile AYNI mi? (RefreshTokenService)
    // -> Yoksa: logout olmus veya 7 gun dolmus -> 401
    // -> Farkliysa: eski/calinmis bir token -> 401
    // 4. Kullaniciyi DB'den al (rolu degismis olabilir -> guncel rol token'a
    // girsin)
    // 5. YENI access + YENI refresh uret, yeni refresh'i Redis'e yaz (eskisinin
    // uzerine)
    //
    // Neden refresh token'i da yeniliyoruz? (REFRESH TOKEN ROTATION)
    // -> Her refresh'te eski refresh token Redis'te ezildigi icin bir daha
    // KULLANILAMAZ.
    // -> Biri refresh token'ini calsa bile, sen bir kez refresh yaptiginda
    // onun elindeki token cop olur.
    //
    // Imza gecerli olmasi neden yetmiyor da Redis'e de bakiyoruz?
    // -> JWT stateless: imza 7 gun boyunca gecerli kalir. Logout'ta Redis'ten
    // sildigimiz icin, Redis kontrolu sayesinde logout sonrasi refresh YAPILAMAZ.
    // =====================================================================================
    public TokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // ADIM 1: Imza + sure kontrolu
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("Refresh token geçersiz veya süresi dolmuş. Lütfen tekrar giriş yapın.");
        }

        // ADIM 2: Token kimin?
        Long userId = jwtTokenProvider.extractUserId(refreshToken);
        if (userId == null) {
            throw new UnauthorizedException("Refresh token geçersiz.");
        }

        // ADIM 3: Redis'teki ile karsilastir
        String savedToken = refreshTokenService.find(userId);
        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new UnauthorizedException("Oturum sonlanmış. Lütfen tekrar giriş yapın.");
        }

        // ADIM 4: Guncel kullanici bilgisi (silinmis olabilir, rolu degismis olabilir)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı."));

        // ADIM 5: Yeni cift (issueTokens yeni refresh'i Redis'e de yazar -> eskisi
        // ezilir)
        log.info("Token yenilendi. userId={}", userId);
        return issueTokens(user);
    }

    // =====================================================================================
    // 4) LOGOUT — OTURUMU SUNUCU TARAFINDA KAPAT
    // =====================================================================================
    //
    // JWT'de "logout" neden zor? -> Token'i sunucu hatirlamaz; 15 dk boyunca
    // gecerli kalir.
    // Cozum iki adim (TokenBlacklistService'teki aciklamanin uygulamasi):
    //
    // a) Access token'in jti'sini blacklist'e yaz (TTL = kalan omru)
    // -> Filter her istekte isBlacklisted(jti) soruyor -> artik 401
    // b) Refresh token'i Redis'ten sil
    // -> refresh() Redis'te bulamaz -> yeni access token alinamaz
    //
    // Sadece (a) yapsak: kullanici refresh ile yeni access token alir -> logout
    // anlamsiz.
    // Sadece (b) yapsak: elindeki access token 15 dk daha calisir.
    // Ikisi birlikte = tam logout.
    //
    // Parametre: Authorization header'indan gelen ham access token ("Bearer "
    // atilmis hali)
    // =====================================================================================
    public void logout(String accessToken) {

        // SecurityConfig'te logout "authenticated()" oldugu icin buraya gecersiz token
        // normalde ulasmaz (Filter context'i doldurmaz -> 401). Yine de servis tek
        // basina
        // da guvenli olsun diye kontrol ediyoruz (savunmaci programlama).
        if (accessToken == null || !jwtTokenProvider.validateToken(accessToken)) {
            throw new UnauthorizedException("Geçersiz token.");
        }

        String jti = jwtTokenProvider.extractJti(accessToken);
        long remainingMillis = jwtTokenProvider.extractRemainingMillis(accessToken);
        Long userId = jwtTokenProvider.extractUserId(accessToken);

        // a) Access token'i kara listeye al
        tokenBlacklistService.add(jti, remainingMillis);

        // b) Refresh token'i sil
        refreshTokenService.delete(userId);

        log.info("Kullanıcı çıkış yaptı. userId={}", userId);
    }

    // =====================================================================================
    // YARDIMCI: TOKEN CIFTI URET + REFRESH'I REDIS'E YAZ
    // =====================================================================================
    //
    // register, login ve refresh UCU de ayni seyi yapiyor: "token cifti ver".
    // Kod tekrarini (DRY) onlemek icin tek yerde topladik.
    //
    // refreshTokenService.save() -> "refresh_token:{userId}" key'ine yazar.
    // Ayni key oldugu icin onceki refresh token EZILIR:
    // -> Baska cihazdan login = eski cihazin refresh'i gecersiz (tek oturum
    // politikasi)
    // -> refresh() = rotation (eski refresh cop olur)
    // =====================================================================================
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        refreshTokenService.save(user.getId(), refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
