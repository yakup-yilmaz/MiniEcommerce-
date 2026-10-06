package com.ecommerce.config;

import com.ecommerce.security.JwtAccessDeniedHandler;
import com.ecommerce.security.JwtAuthenticationEntryPoint;
import com.ecommerce.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ===========================================================================================
 * SPRING SECURITY YAPILANDIRMASI (ANA GUVENLIK AYAR SINIFI)
 * ===========================================================================================
 *
 * Bu sinif, projenin "guvenlik beyin merkezi"dir. Uc temel gorevi var:
 *
 * 1) KIM GIREBILIR? → Hangi endpoint'e kim erisebilir? (permitAll vs hasRole)
 * 2) NASIL DOGRULANIR? → Session yok, JWT kullaniyoruz (stateless)
 * 3) HANGI ARACLAR KULLANILIR? → BCrypt sifre hashleme, JWT filtreleyici
 *
 * @Configuration -> Spring'e "bu sinif ayar sinifi" der
 * @EnableWebSecurity -> Spring Security'yi aktif eder
 * @EnableMethodSecurity -> @PreAuthorize("hasRole('ADMIN')") anotasyonlarini
 *                       aktif eder
 *                       Bu olmadan servis/controller metodlarindaki rol
 *                       kontrolleri calismas
 * @RequiredArgsConstructor -> Lombok: final alanlar icin constructor (DI icin)
 *                          ===========================================================================================
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Her istekte JWT token'ini kontrol eden filtremiz
    // SecurityFilterChain'e ekleyecegiz (asagida)
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Token yok/gecersiz -> 401 JSON (bkz. JwtAuthenticationEntryPoint)
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    // Token gecerli ama rol yetersiz -> 403 JSON (bkz. JwtAccessDeniedHandler)
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // =====================================================================================
    // SECURITY FILTER CHAIN — ANA GUVENLIK ZINCIRI
    // =====================================================================================
    //
    // "Filter Chain" (Filtre Zinciri): Her gelen HTTP istegi, bir boru hattindan
    // (pipeline) gecer. Her filtre boru hattindaki bir durak. Guvenligi bu
    // zincirleme
    // duraklar saglar.
    //
    // Bu metod, o boru hattinin tamamini yapilandirir:
    // - CSRF kapalimi?
    // - Session kullaniliyor mu?
    // - Hangi URL'lere serbest erisim var?
    // - JWT filtremiz nereye ekleniyor?
    // =====================================================================================
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // -----------------------------------------------------------------------
                // CSRF KORUMASI KAPATILDI
                // -----------------------------------------------------------------------
                // CSRF (Cross-Site Request Forgery): Kullanicinin haberi olmadan
                // onun adina istek gonderilmesi saldirisi.
                //
                // Neden kapattik?
                // -> CSRF korumalasi oturum (session) bazli sistemlerde anlamli.
                // -> Bizim sistemimiz stateless + JWT kullanimi. Session yok.
                // -> JWT token Authorization header'inda gelir; tarayici otomatik
                // gondermez (cookie'den farkli). Bu yuzden CSRF saldirisi mumkun degil.
                // -> CSRF acik birakilirsa her POST/PUT/DELETE istegi hata verir.
                // -----------------------------------------------------------------------
                .csrf(AbstractHttpConfigurer::disable)

                // -----------------------------------------------------------------------
                // SESSION OTURUM YONETIMI: STATELESS
                // -----------------------------------------------------------------------
                // STATELESS ne demek?
                // -> Sunucu hic session tutmaz. Her istek kendi kendine yeterli olmali.
                // -> Kullanici her istekte kimligini JWT token ile ispat eder.
                // -> Sunucu "Bu kullaniciyi daha once gordun mu?" diye sormaz.
                //
                // SessionCreationPolicy.STATELESS:
                // -> Spring Security hic session olusturmaz
                // -> Mevcut session varsa kullanmaz
                // -> Her istek bagimsiz islenir
                // -----------------------------------------------------------------------
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // -----------------------------------------------------------------------
                // ENDPOINT YETKİ KURALLARI
                // -----------------------------------------------------------------------
                // Kurallar yukaridan asagiya, ilk eslesen kural uygulanir.
                // Daha spesifik kurallar once yazilir!
                // -----------------------------------------------------------------------
                .authorizeHttpRequests(auth -> auth

                        // === LOGOUT: /api/auth/** ICINDE AMA TOKEN GEREKTIRIR ===
                        // "Ilk eslesen kural kazanir" -> bu satir permitAll'dan ONCE olmali.
                        // Sonra yazsaydik /api/auth/** onu yutar, logout herkese acik olurdu.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()

                        // === HERKESE ACIK ENDPOINTLER (Token gerekmez) ===

                        // Kayit, giris ve refresh: Token olmadan erisim
                        .requestMatchers("/api/auth/**").permitAll()

                        // Swagger UI ve API Docs: Gelistirme sirasinda dokumana erisim
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/v3/api-docs/**",
                                "/actuator/**")
                        .permitAll()

                        // Urun ve kategori LISTELEME: Herkese acik (arama/browse icin)
                        // NOT: GET izinliyken POST/PUT/DELETE sadece ADMIN'e
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()

                        // === SADECE ADMIN ENDPOINTLERI ===

                        // Admin siparis yonetimi: Tum siparisleri gor, durumu guncelle
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Kategori olusturma, guncelleme, silme: Sadece ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")

                        // Urun olusturma, guncelleme, silme: Sadece ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")

                        // === GERI KALAN HER SEY: GIRIS YAPMIS KULLANICI GEREKTIRIR ===
                        // - POST /api/orders (siparis ver) -> CUSTOMER olmali (servis katmaninda
                        // kontrol)
                        // - GET /api/orders (kendi siparislerim) -> giris yapmis olmali
                        // - POST /api/auth/logout -> giris yapmis olmali
                        .anyRequest().authenticated())

                // -----------------------------------------------------------------------
                // JWT FILTRESINI SPRING SECURITY ZINCIRINE EKLE
                // -----------------------------------------------------------------------
                // Spring Security'nin kendi varsayilan filtresi:
                // UsernamePasswordAuthenticationFilter -> form bazli login icin
                // Bu filtre, POST /login form verisi bekler.
                //
                // Biz form bazli login kullanmiyoruz. JWT kullaniyoruz.
                // Bu yuzden kendi JwtAuthenticationFilter'imizi bu filtreden ONCE ekledik.
                //
                // Istek gelisi sirasi:
                // 1. JwtAuthenticationFilter (BIZIM FILTREMIZ) -> JWT kontrol
                // 2. UsernamePasswordAuthenticationFilter (SPRING'IN FILTREISI) -> es gecildi
                // 3. Controller
                // -----------------------------------------------------------------------
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // -----------------------------------------------------------------------
                // GUVENLIK HATALARINDA NE DONULECEK?
                // -----------------------------------------------------------------------
                // Bu hatalar FILTER zincirinde olustugu icin GlobalExceptionHandler
                // yakalayamaz. Cevabi bu iki sinif JSON olarak yazar:
                //   authenticationEntryPoint -> kimlik yok      -> 401
                //   accessDeniedHandler      -> yetki yetersiz  -> 403
                // Yazmasaydik Spring bos govdeli 403 donerdi (401 durumunda bile).
                // -----------------------------------------------------------------------
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler));

        return http.build();
    }

    // =====================================================================================
    // SIFRE HASHLEME: BCryptPasswordEncoder
    // =====================================================================================
    //
    // BCrypt nedir?
    // -> Sifreleri "okunaksiz" hale getiren (hashleme) bir algoritma.
    // -> Tek yonlu: hash -> sifre donusumleri yapamayiz. Sadece
    // karsilastirabilirsin.
    // -> Her hashlemede farkli sonuc verir (salt kullanimi nedeniyle).
    // Ornek: "123456" -> "$2a$10$abc..." ve "$2a$10$xyz..." (her seferinde farkli!)
    // Ama her ikisi de "123456" icin "dogru" kabul edilir.
    //
    // Neden @Bean olarak tanimliyoruz?
    // -> AuthService.register() icinde kullanicinin sifresini hashlemek icin inject
    // edilir.
    // -> Spring Security'nin AuthenticationManager'i da sifre karsilastirmada
    // kullanir.
    // -> Bir @Bean tanimlamazsak Spring "hangi encoder kullanacagim?" diye hata
    // firlatir.
    //
    // BCryptPasswordEncoder() -> varsayilan strength (10 round). Daha yuksek = daha
    // yavas ama guvenli.
    // =====================================================================================
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // =====================================================================================
    // AUTHENTICATION MANAGER
    // =====================================================================================
    //
    // AuthenticationManager nedir?
    // -> Spring Security'nin kimlik dogrulama motorudur.
    // -> "Bu email ve sifre dogru mu?" sorusunu yanitleyen ana mekanizma.
    //
    // Nasil calisir?
    // AuthenticationManager.authenticate(token) cagirildiginda:
    // 1. CustomUserDetailsService.loadUserByUsername(email) -> kullanicilari yukle
    // 2. Yuklenen kullanicinin hashli sifresi ile girilen sifre karsilastirilir
    // (BCrypt)
    // 3. Eslesiyorsa -> Authentication nesnesi doner (basarili)
    // 4. Eslesmiyorsa -> BadCredentialsException firlatilir (yanlis sifre)
    //
    // Neden @Bean olarak cikartiyoruz?
    // -> AuthService.login() icinde dogrudan kullanmak istiyoruz.
    // -> Spring varsayilan olarak AuthenticationManager'i disariya expose etmez.
    // -> authenticationConfiguration.getAuthenticationManager() ile Spring'in
    // otomatik olusturduğu manager'i alip @Bean yapiyoruz.
    // =====================================================================================
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
