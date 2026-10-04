package com.ecommerce.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ===========================================================================================
 * REGISTER REQUEST — POST /api/auth/register istegin govdesi
 * ===========================================================================================
 *
 *    POST /api/auth/register
 *    { "name": "Yakup", "email": "yakup@mail.com", "password": "123456" }
 *
 * Neden User entity'sini direkt almiyoruz?
 * -> Entity'de "role", "id", "createdAt" alanlari var. Entity'yi body olarak
 *    kabul etseydik kotu niyetli biri { ..., "role": "ADMIN" } gonderip
 *    kendini admin yapabilirdi! (Mass Assignment acigi)
 * -> DTO'da SADECE kullanicinin girmesine izin verdigimiz alanlar var.
 *    Rol, AuthService icinde her zaman CUSTOMER olarak atanir.
 *
 * Validation ne zaman calisir?
 * -> Controller'da @Valid @RequestBody RegisterRequest yazildigi icin
 *    istek AuthService'e ulasmadan ONCE. Hata varsa GlobalExceptionHandler
 *    (MethodArgumentNotValidException) alan bazli 400 cevabi doner.
 * ===========================================================================================
 */
@Data
public class RegisterRequest {
    @NotBlank(message = "İsim alanı boş bırakılamaz")
    private String name;

    @NotBlank(message = "Email alanı boş bırakılamaz")
    @Email(message = "Geçerli bir email adresi giriniz")
    private String email;

    // Bu ham (duz) sifredir. DB'ye ASLA bu haliyle yazilmaz;
    // AuthService.register() icinde passwordEncoder.encode() ile BCrypt hash'e cevrilir.
    @NotBlank(message = "Şifre alanı boş bırakılamaz")
    @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
    private String password;
}
