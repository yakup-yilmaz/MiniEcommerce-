package com.ecommerce.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.dto.OrderDto;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.service.OrderService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Yalnızca ADMIN yetkisine sahip kullanıcıların siparişleri yönettiği REST Controller.
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminOrderController {

    private final OrderService orderService;

    /**
     * GET /api/admin/orders
     * Sistemdeki tüm siparişleri sayfalı olarak listeler.
     * Opsiyonel olarak ?status=PENDING gibi durum filtresi alabilir.
     */
    @GetMapping
    public ResponseEntity<Page<OrderDto>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Admin siparişleri listeliyor: Durum Filtresi={}, Sayfa={}", status, pageable.getPageNumber());
        Page<OrderDto> orders = orderService.getAllOrders(status, pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * PUT /api/admin/orders/{id}/status
     * Siparişin durumunu günceller (PENDING -> CONFIRMED -> SHIPPED -> DELIVERED).
     * Durum güncellenince müşteriye bildirim gitmesi için OrderStatusChangedEvent tetiklenir.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        log.info("Admin sipariş durumu güncelleme isteği: Sipariş ID={}, Yeni Durum={}", id, request.getStatus());
        OrderDto updatedOrder = orderService.updateOrderStatus(id, request.getStatus());
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Durum güncelleme istek gövdesi (JSON Payload)
     * Örnek: { "status": "CONFIRMED" }
     */
    @Data
    public static class OrderStatusUpdateRequest {
        @NotNull(message = "Sipariş durumu boş olamaz")
        private OrderStatus status;
    }
}
