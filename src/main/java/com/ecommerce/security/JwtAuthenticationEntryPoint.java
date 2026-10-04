package com.ecommerce.security;

import com.ecommerce.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * ===========================================================================================
 * JWT AUTHENTICATION ENTRY POINT (401 — "SENI TANIMIYORUM" CEVABI)
 * ===========================================================================================
 *
 * PROBLEM:
 * -> Token'siz veya gecersiz token'li biri korumali endpoint'e (POST /api/orders) gelir.
 * -> JwtAuthenticationFilter context'i DOLDURMAZ (anonim kalir) ve istegi devam ettirir.
 * -> Spring Security, ".anyRequest().authenticated()" kuralinda "kimse yok!" der.
 * -> Peki istemciye NE doner?
 *      Biz bir sey yazmazsak: Spring Boot 3 varsayilani BOS GOVDELI 403 Forbidden.
 *      Bu hem yanlis (aslinda 401 olmali) hem de frontend icin anlasilmaz.
 *
 * COZUM:
 * -> AuthenticationEntryPoint arayuzunu uygula. Spring "kimlik yok" durumunda
 *    commence() metodunu cagirir; biz de diger hatalarla AYNI formatta (ErrorResponse)
 *    401 JSON'u yazariz.
 *
 * NEDEN GlobalExceptionHandler BUNU YAKALAYAMIYOR?
 * -> @RestControllerAdvice sadece Controller ICINDE firlayan hatalari yakalar.
 * -> Bu hata Controller'a gelmeden, FILTER ZINCIRINDE olusur.
 *    Filtre katmani Controller'dan once calistigi icin advice oraya erisemez.
 *    Bu yuzden cevabi response'a kendimiz yazmak zorundayiz.
 *
 *    Istek ──► [Filter Zinciri] ──► [DispatcherServlet] ──► [Controller]
 *                    ▲                                          ▲
 *              EntryPoint burada               GlobalExceptionHandler burada
 *
 * SecurityConfig'te: .exceptionHandling(ex -> ex.authenticationEntryPoint(this))
 * ===========================================================================================
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Spring Boot'un hazir ObjectMapper'i -> LocalDateTime'i dogru serialize eder
    // (JavaTimeModule kayitli). new ObjectMapper() deseydik timestamp'te hata alirdik.
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message("Bu işlem için giriş yapmanız gerekiyor. Token eksik, geçersiz veya süresi dolmuş.")
                .timestamp(LocalDateTime.now())
                .build();

        // Controller olmadigi icin cevabi "elle" yaziyoruz:
        response.setStatus(HttpStatus.UNAUTHORIZED.value());          // 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);   // application/json
        response.setCharacterEncoding("UTF-8");                       // Turkce karakterler
        objectMapper.writeValue(response.getOutputStream(), body);    // nesne -> JSON
    }
}
