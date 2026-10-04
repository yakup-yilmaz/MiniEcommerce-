package com.ecommerce.security;

import com.ecommerce.entity.User;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * ===========================================================================================
 * CUSTOM USER DETAILS SERVICE
 * ===========================================================================================
 *
 * Bu sinif Spring Security ile kendi User entity'mizin koprusudur.
 *
 * PROBLEM:
 * Spring Security, kullanicilari "kim bu kisi?" diye sorgularken kendi
 * standart arayuzunu (UserDetails) kullanir. Ama bizim User entity'miz
 * bu arayuzu implement etmiyor.
 * 
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // =====================================================================================
    // loadUserByUsername — Kullanicilari Veritabanindan Yukle
    // =====================================================================================
    //
    // Ne zaman cagrilir?
    // 1. Login sirasinda: AuthenticationManager.authenticate() cagrildiginda
    // Spring Security bu metodu tetikler.
    // 2. Her API isteğinde: JwtAuthenticationFilter token'dan email cektikten sonra
    // bu metodu cagirir (SecurityContext'e kullanicilari set etmek icin).
    //
    // Parametreler:
    // username -> Spring Security'de "username" deniyor ama biz email kullaniyoruz.
    // (Spring Security'nin interface'i degistiremeyiz, parametre adi bize kalmis)
    //
    // Ne yapar?
    // 1. Email ile veritabanindan User'i bulur.
    // 2. Bulamazsa UsernameNotFoundException firlatir (Spring Security yakalar ->
    // 401).
    // 3. Bulursa: User'i Spring Security'nin UserDetails'ine "donusturur".
    // =====================================================================================
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // "username" aslinda email. Veritabanindan cek.
        // Optional bos ise (kullanici yoksa) UsernameNotFoundException firlatilir.
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Kullanici bulunamadi: " + username));

        // User entity'sini Spring Security'nin UserDetails'ine donustur.
        // Anonim ic sinif (anonymous inner class) kullaniyoruz:
        // UserDetails'in tum metodlarini implement ediyoruz ama User alanini da
        // tutuyoruz.
        return new UserDetails() {

            // -----------------------------------------------------------------------
            // getAuthorities() -> Kullanicinin Yetkileri (Rolleri)
            // -----------------------------------------------------------------------
            // Spring Security, yetki kontrolu icin "GrantedAuthority" listesi ister.
            // Bizim Role enum'umuz (ADMIN, CUSTOMER) burada "ROLE_ADMIN", "ROLE_CUSTOMER"
            // formatina cevrilir.
            //
            // Neden "ROLE_" on eki?
            // -> Spring Security'nin @PreAuthorize("hasRole('ADMIN')") ve
            // SecurityConfig'teki .hasRole("ADMIN") mekanizmasi
            // otomatik olarak "ROLE_" on ekini ekliyor. Eger biz elle koyamazsak
            // yetki kontrolu calisir ama daha temiz olur.
            // -> hasAuthority("ROLE_ADMIN") ile de kullanilabilir (on ek elle verilir).
            // -----------------------------------------------------------------------
            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                // Kullanicinin rolunu "ROLE_ADMIN" veya "ROLE_CUSTOMER" formatina cevir
                // SimpleGrantedAuthority -> Spring Security'nin basit yetki implementasyonu
                return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
            }

            // Veritabanindan gelen bcrypt ile sifrelenmus parola
            // Spring Security, login sirasinda bu hash'i kullanicinin girdisiyle
            // karsilastirir
            @Override
            public String getPassword() {
                return user.getPassword();
            }

            // Spring Security'de "kullanici adi" bizde email
            // JwtAuthenticationFilter'da extractEmail() ile cekilen deger buraya geliyor
            @Override
            public String getUsername() {
                return user.getEmail();
            }

            // -----------------------------------------------------------------------
            // Hesap Durumu Metodlari
            // -----------------------------------------------------------------------
            // Bu projede hesap kilitleme, suresi dolma gibi ozellikler yok.
            // Hepsi true donuyor (aktif hesap).
            // Ileride "aktif mi degil mi?" ozelligi eklemek istersen bunlari duzenlersiz.
            // -----------------------------------------------------------------------

            // Hesap suresi dolmus mu? false -> dolmamis (aktif)
            @Override
            public boolean isAccountNonExpired() {
                return true;
            }

            // Hesap kilitli mi? false -> kilitli degil (aktif)
            @Override
            public boolean isAccountNonLocked() {
                return true;
            }

            // Parola suresi dolmus mu? false -> dolmamis (aktif)
            @Override
            public boolean isCredentialsNonExpired() {
                return true;
            }

            // Hesap etkin mi? true -> evet, aktif
            @Override
            public boolean isEnabled() {
                return true;
            }

        };
    }
}
