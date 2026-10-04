package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ===========================================================================================
 * TOKEN RESPONSE — register / login / refresh cevaplarinin ortak govdesi
 * ===========================================================================================
 *
 * {
 * "accessToken": "eyJhbGciOi...", <- 15 dk, her API isteginde header'a konur
 * "refreshToken": "eyJhbGciOi..." <- 7 gun, sadece /api/auth/refresh'e
 * gonderilir
 * }
 *
 * Frontend ne yapar?
 * -> accessToken'i her istekte: "Authorization: Bearer {accessToken}"
 * -> API 401 donunce refreshToken ile /api/auth/refresh cagirir, yeni ciftini
 * alir.
 * -> Logout'ta ikisini de kendi tarafinda siler (sunucu tarafini
 * AuthService.logout halleder).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
}
