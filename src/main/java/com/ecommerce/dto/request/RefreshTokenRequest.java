package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ===========================================================================================
 * REFRESH TOKEN REQUEST — POST /api/auth/refresh istegin govdesi
 * ===========================================================================================
 *
 * Senaryo:
 * -> Kullanicinin Access Token'i (15 dk) doldu. API 401 dondu.
 * -> Frontend, elinde sakladigi Refresh Token'i (7 gun) bu DTO ile gonderir:
 *
 *    POST /api/auth/refresh
 *    { "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
 *
 * -> Cevap olarak YENI bir accessToken + YENI bir refreshToken alir (TokenResponse).
 *
 * Neden header'da degil de body'de?
 * -> Authorization header'i Access Token'a ayrildi. JwtAuthenticationFilter
 *    o header'i okuyor. Refresh Token'i oraya koyarsak filtre onu access token
 *    sanip isleme sokar -> karisiklik. Body'de gondermek niyeti netlestirir.
 * ===========================================================================================
 */
@Data
public class RefreshTokenRequest {

    // @NotBlank -> null, "" ve "   " degerlerini reddeder.
    // Controller'da @Valid oldugu icin bos gelirse GlobalExceptionHandler 400 doner,
    // istek AuthService'e hic ulasmaz.
    @NotBlank(message = "Refresh token boş bırakılamaz")
    private String refreshToken;
}
