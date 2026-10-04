package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RefreshTokenRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ===========================================================================================
 * AUTH CONTROLLER — KIMLIK ENDPOINT'LERI (/api/auth/**)
 * ===========================================================================================
 *
 * Controller'in gorevi INCE olmaktir:
 * 1. HTTP istegini al (body, header, IP)
 * 2. @Valid ile format kontrolu yaptir
 * 3. Isi AuthService'e devret
 * 4. Dogru HTTP status kodu ile cevabi don
 * Is mantigi (sifre kontrolu, Redis, token) BURADA OLMAZ -> hepsi
 * AuthService'te.
 *
 * ┌────────┬──────────────────────┬──────────────┬──────────────────────────────┐
 * │ Metod │ URL │ Token lazim? │ Basarili cevap │
 * ├────────┼──────────────────────┼──────────────┼──────────────────────────────┤
 * │ POST │ /api/auth/register │ Hayir │ 201 Created + TokenResponse │
 * │ POST │ /api/auth/login │ Hayir │ 200 OK + TokenResponse │
 * │ POST │ /api/auth/refresh │ Hayir (*) │ 200 OK + TokenResponse │
 * │ POST │ /api/auth/logout │ EVET │ 204 No Content │
 * └────────┴──────────────────────┴──────────────┴──────────────────────────────┘
 * (*) Access token degil, body'de refresh token ister.
 *
 * Hata durumlari (hepsi GlobalExceptionHandler'dan JSON ErrorResponse olarak
 * doner):
 * 400 -> @Valid hatasi (bos email, kisa sifre...)
 * 401 -> UnauthorizedException (yanlis sifre, gecersiz refresh token)
 * 409 -> AlreadyExistsException (email zaten kayitli)
 * 429 -> RateLimitException (5 yanlis deneme)
 * ===========================================================================================
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =====================================================================================
    // POST /api/auth/login
    // =====================================================================================
    // HttpServletRequest -> Spring otomatik inject eder. IP adresini almak icin
    // lazim,
    // cunku RateLimitService IP bazli calisiyor.
    //
    // getRemoteAddr() hakkinda NOT:
    // -> Uygulama direkt internete aciksa gercek istemci IP'sini verir.
    // -> Onunde Nginx / load balancer varsa HEP proxy'nin IP'sini verir!
    // O durumda "X-Forwarded-For" header'i okunmali (ve sadece guvenilen
    // proxy'den geliyorsa guvenilmeli, yoksa saldirgan header'i uydurur).
    // -> Simdilik lokal gelistirme icin getRemoteAddr() yeterli.
    // =====================================================================================
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(authService.login(request, clientIp));
    }

    // =====================================================================================
    // POST /api/auth/refresh
    // =====================================================================================
    // Body: { "refreshToken": "eyJ..." }
    // Access token suresi dolmus olsa bile cagrilabilir (permitAll) — zaten amaci
    // bu.
    // =====================================================================================
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    // =====================================================================================
    // POST /api/auth/logout
    // =====================================================================================
    // Header: Authorization: Bearer eyJ...
    //
    // @RequestHeader(HttpHeaders.AUTHORIZATION) -> header'in degerini String olarak
    // verir.
    // required = false -> header yoksa Spring 400 firlatmasin; zaten
    // SecurityConfig'te
    // logout "authenticated()" oldugu icin token'siz istek buraya gelmeden 401
    // alir.
    //
    // Neden token'i tekrar header'dan okuyoruz? Filter zaten okumadi mi?
    // -> Filter token'dan UserDetails uretip context'e koydu, ham token'i
    // saklamadi.
    // -> Logout icin HAM token lazim: jti ve kalan sureyi ondan okuyacagiz.
    //
    // 204 NO CONTENT -> "Islem basarili, donecek bir govde yok."
    // =====================================================================================
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        // "Bearer eyJ..." -> "eyJ..." ("Bearer " 7 karakter)
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : null;

        authService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
