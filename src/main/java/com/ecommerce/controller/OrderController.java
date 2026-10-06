package com.ecommerce.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.dto.OrderDto;
import com.ecommerce.dto.request.OrderCreateRequest;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.facade.OrderFacade;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Giriş yapmış müşterilerin sipariş süreçlerini (oluşturma, listeleme, detay, iptal)
 * yöneten REST Controller.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderFacade orderFacade;
    private final OrderService orderService;
    private final UserRepository userRepository;

    /**
     * POST /api/orders
     * Müşterinin sepetindeki ürünlerle yeni sipariş oluşturmasını sağlar.
     * Kilit, stok kontrolü, indirim ve event tetikleme akışı OrderFacade tarafından yürütülür.
     */
    @PostMapping
    public ResponseEntity<OrderDto> placeOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Sipariş verme isteği alındı. Kullanıcı={}", userDetails.getUsername());
        User user = getAuthenticatedUser(userDetails);
        OrderDto orderDto = orderFacade.placeOrder(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderDto);
    }

    /**
     * GET /api/orders
     * Giriş yapmış müşterinin geçmiş siparişlerini sayfalı ve en yeniden eskiye listeler.
     */
    @GetMapping
    public ResponseEntity<Page<OrderDto>> getUserOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Kullanıcı siparişleri listeleniyor: Kullanıcı={}", userDetails.getUsername());
        User user = getAuthenticatedUser(userDetails);
        Page<OrderDto> orders = orderService.getUserOrders(user.getId(), pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * GET /api/orders/{id}
     * Kullanıcının kendi siparişinin detaylarını kalemleriyle (items) birlikte tek sorguda (FETCH JOIN) getirir.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Sipariş detayı isteği: Sipariş ID={}, Kullanıcı={}", id, userDetails.getUsername());
        User user = getAuthenticatedUser(userDetails);
        OrderDto orderDto = orderService.getOrderDetails(id, user.getId());
        return ResponseEntity.ok(orderDto);
    }

    /**
     * PUT /api/orders/{id}/cancel
     * Yalnızca PENDING durumundaki siparişi iptal eder ve ürünlerin stoklarını iade eder.
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderDto> cancelOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Sipariş iptal isteği alındı: Sipariş ID={}, Kullanıcı={}", id, userDetails.getUsername());
        User user = getAuthenticatedUser(userDetails);
        OrderDto orderDto = orderService.cancelOrder(id, user.getId());
        return ResponseEntity.ok(orderDto);
    }

    /**
     * Güvenlik oturumundan User entity'sini çeken yardımcı metot.
     */
    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userDetails.getUsername()));
    }
}
