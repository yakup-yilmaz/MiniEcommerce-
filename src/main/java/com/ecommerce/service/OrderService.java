package com.ecommerce.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.OrderDto;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.event.OrderStatusChangedEvent;
import com.ecommerce.exception.OrderCancellationException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sipariş sorgulama, detay görüntüleme, sipariş iptali ve
 * admin sipariş durum güncelleme işlemlerini yürüten servis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Giriş yapmış kullanıcının geçmiş siparişlerini sayfalı olarak getirir.
     */
    @Transactional(readOnly = true)
    public Page<OrderDto> getUserOrders(Long userId, Pageable pageable) {
        log.info("Kullanıcı siparişleri listeleniyor: Kullanıcı ID={}, Sayfa={}, Boyut={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());
        Page<Order> orders = orderRepository.findByUserId(userId, pageable);
        return orders.map(orderMapper::toDto);
    }

    /**
     * Kullanıcının kendi siparişinin detaylarını getirir.
     * N+1 problemini önlemek için kalemler (items) FETCH JOIN ile tek sorguda çekilir.
     */
    @Transactional(readOnly = true)
    public OrderDto getOrderDetails(Long orderId, Long userId) {
        log.info("Sipariş detayı getiriliyor: Sipariş ID={}, Kullanıcı ID={}", orderId, userId);
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return orderMapper.toDto(order);
    }

    /**
     * Sipariş iptal etme işlemi:
     * 1. Sipariş yalnızca PENDING (Beklemede) durumundaysa iptal edilebilir.
     * 2. Sipariş iptal edilince durumu CANCELLED yapılır.
     * 3. İptal edilen siparişteki tüm ürünlerin stokları sisteme geri iade edilir.
     */
    @Transactional
    public OrderDto cancelOrder(Long orderId, Long userId) {
        log.info("Sipariş iptal talebi alındı: Sipariş ID={}, Kullanıcı ID={}", orderId, userId);

        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // İş Kuralı: Sadece PENDING olan siparişler iptal edilebilir
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderCancellationException(
                    String.format("Yalnızca beklemede (PENDING) olan siparişler iptal edilebilir. Mevcut durum: %s",
                            order.getStatus()));
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);

        // Stokları geri iade et ve Redis cache'ini temizle
        for (OrderItem item : order.getItems()) {
            productService.increaseStock(item.getProduct(), item.getQuantity());
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("Sipariş başarıyla iptal edildi ve stoklar iade edildi. Sipariş ID={}", orderId);

        // İptal durum değişikliği event'i yayınla
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                updatedOrder.getId(),
                updatedOrder.getUser().getId(),
                updatedOrder.getUser().getEmail(),
                previousStatus,
                OrderStatus.CANCELLED
        ));

        return orderMapper.toDto(updatedOrder);
    }

    /**
     * Admin Paneli İçin: Tüm siparişleri opsiyonel durum filtresiyle sayfalı getirir.
     */
    @Transactional(readOnly = true)
    public Page<OrderDto> getAllOrders(OrderStatus status, Pageable pageable) {
        log.info("Admin siparişleri listeliyor: Durum Filtresi={}, Sayfa={}", status, pageable.getPageNumber());
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }
        return orders.map(orderMapper::toDto);
    }

    /**
     * Admin Paneli İçin: Sipariş durumunu günceller (PENDING -> CONFIRMED -> SHIPPED -> DELIVERED).
     * Durum değiştiğinde müşteriye bildirim gitmesi için OrderStatusChangedEvent yayınlar.
     */
    @Transactional
    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.info("Admin sipariş durumu güncelliyor: Sipariş ID={}, Yeni Durum={}", orderId, newStatus);

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);

        Order updatedOrder = orderRepository.save(order);
        log.info("Sipariş durumu güncellendi: Sipariş ID={}, Eski={}, Yeni={}",
                orderId, previousStatus, newStatus);

        // Asenkron bildirim için durum değişikliği event'i fırlat
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                updatedOrder.getId(),
                updatedOrder.getUser().getId(),
                updatedOrder.getUser().getEmail(),
                previousStatus,
                newStatus
        ));

        return orderMapper.toDto(updatedOrder);
    }
}
