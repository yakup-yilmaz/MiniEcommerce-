package com.ecommerce.exception;

import org.springframework.http.HttpStatus;

/**
 * ===========================================================================================
 * UNAUTHORIZED EXCEPTION (401 - KIMLIGIN DOGRULANAMADI)
 * ===========================================================================================
 *
 * Ne zaman firlatilir?
 * -> Login'de email/sifre yanlissa
 * -> Refresh token gecersiz, suresi dolmus veya Redis'tekiyle eslesmiyorsa
 * -> Logout'ta token gelmemisse / gecersizse
 *
 * 401 ile 403 farki (cok karistirilir!):
 * -> 401 Unauthorized : "Seni TANIMIYORUM" (kimlik yok/yanlis) -> tekrar login ol
 * -> 403 Forbidden    : "Seni taniyorum ama YETKIN YOK" (CUSTOMER admin endpoint'ine girdi)
 *
 * Neden ApiException'dan turettik?
 * -> GlobalExceptionHandler zaten ApiException'i yakalayip ErrorResponse JSON'u donuyor.
 * -> Yeni handler yazmaya gerek kalmadan, status'u (401) buradan veriyoruz.
 *    AlreadyExistsException (409) ve RateLimitException (429) ile ayni desen.
 * ===========================================================================================
 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
