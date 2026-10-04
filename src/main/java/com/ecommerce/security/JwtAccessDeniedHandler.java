package com.ecommerce.security;

import com.ecommerce.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * ===========================================================================================
 * JWT ACCESS DENIED HANDLER (403 — "SENI TANIYORUM AMA YETKIN YOK" CEVABI)
 * ===========================================================================================
 *
 * EntryPoint'in kardesi. Fark:
 *
 *   ┌───────────────────────────┬────────────────────────────┬──────────────┐
 *   │ Durum                     │ Kim devreye girer?         │ Status       │
 *   ├───────────────────────────┼────────────────────────────┼──────────────┤
 *   │ Token yok / gecersiz      │ JwtAuthenticationEntryPoint│ 401          │
 *   │ Token gecerli, rol YETMEZ │ JwtAccessDeniedHandler     │ 403          │
 *   └───────────────────────────┴────────────────────────────┴──────────────┘
 *
 * Ornek: CUSTOMER rolundeki Yakup gecerli token'la POST /api/products atar.
 * -> Filter: token gecerli, context'e ROLE_CUSTOMER koyar.
 * -> SecurityConfig: POST /api/products -> hasRole("ADMIN") -> UYMUYOR.
 * -> Spring: AccessDeniedException -> bu sinifin handle() metodu -> 403 JSON.
 *
 * Neden ayri bir sinif? Ayni sebep: hata FILTER zincirinde (URL kurallari) olusur,
 * GlobalExceptionHandler oraya erisemez.
 *
 * NOT: @PreAuthorize ile METOD seviyesinde reddedilen istekler ise Controller/Service
 * icinde firlar -> onlari GlobalExceptionHandler'daki AccessDeniedException handler'i yakalar.
 *
 * SecurityConfig'te: .exceptionHandling(ex -> ex.accessDeniedHandler(this))
 * ===========================================================================================
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message("Bu işlemi gerçekleştirmek için yetkiniz bulunmamaktadır.")
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
