package com.ecommerce.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ===========================================================================================
 * LOGIN REQUEST — POST /api/auth/login istegin govdesi
 * ===========================================================================================
 *
 *    POST /api/auth/login
 *    { "email": "yakup@mail.com", "password": "123456" }
 *
 * Bu DTO'daki validation SADECE FORMAT kontrolu yapar:
 * -> "email bos mu? email formatinda mi? sifre bos mu?"
 *
 * "Sifre DOGRU mu?" kontrolu burada YAPILMAZ!
 * -> O is AuthService.login() icinde AuthenticationManager'a yaptirilir.
 *
 * Neden @Size(min=6) yok (RegisterRequest'te var)?
 * -> Kural kayit sirasinda uygulanir. Login'de "sifre kisa" demek saldirgana
 *    bilgi vermek olur; yanlis sifre ne olursa olsun tek mesaj: "Email veya sifre hatali".
 * ===========================================================================================
 */
@Data
public class LoginRequest {
    @NotBlank(message = "Email alanı boş bırakılamaz")
    @Email(message = "Geçerli bir email adresi giriniz")
    private String email;

    @NotBlank(message = "Şifre alanı boş bırakılamaz")
    private String password;
}
