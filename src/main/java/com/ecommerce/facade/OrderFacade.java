package com.ecommerce.facade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.OrderDto;
import com.ecommerce.dto.request.OrderCreateRequest;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.event.OrderPlacedEvent;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.pattern.strategy.DiscountStrategy;
import com.ecommerce.pattern.strategy.DiscountStrategyFactory;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PricingService;
import com.ecommerce.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sipariş orkestrasyonunu (Facade Pattern) yöneten servis.
 * 1. Pessimistic Lock ile ürünleri getirme ve stok kontrolü
 * 2. Order ve OrderItem varlıklarını oluşturma
 * 3. Fiyatlandırma ve İndirim stratejisini çalıştırma
 * 4. Çift yönlü JPA ilişkisini bağlama ve siparişi kaydetme
 * 5. Stokları düşme ve Redis önbelleğini temizleme
 * 6. Asenkron bildirimler ve fatura üretimi için OrderPlacedEvent fırlatma
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFacade {

    private final ProductService productService;
    private final PricingService pricingService;
    private final DiscountStrategyFactory discountStrategyFactory;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderDto placeOrder(OrderCreateRequest request, User user) {
        log.info("Sipariş verme süreci başlatıldı. Kullanıcı ID={}", user.getId());

        List<OrderItem> orderItems = new ArrayList<>();

        // 1. & 2. Adım: Ürünleri Pessimistic Lock (SELECT FOR UPDATE) ile çek,
        // stok kontrolü yap ve OrderItem kalemlerini hazırla
        for (OrderCreateRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productService.getProductEntityWithLock(itemReq.getProductId());
            productService.checkStock(product, itemReq.getQuantity());

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            orderItems.add(orderItem);
        }

        // 3. Adım: Ham toplam fiyatı hesapla
        BigDecimal rawTotal = pricingService.calculateRawTotal(orderItems);

        // 4. Adım: Aktif indirim stratejisini uygula (Strategy Pattern)
        DiscountStrategy strategy = discountStrategyFactory.getStrategy();
        BigDecimal finalPrice = strategy.apply(rawTotal);

        log.info("Fiyat hesaplandı: Ham Tutar={} TL, İndirimli Tutar={} TL, Strateji={}",
                rawTotal, finalPrice, strategy.getClass().getSimpleName());

        // 5. Adım: Order nesnesini oluştur
        Order order = Order.builder()
                .user(user)
                .items(orderItems)
                .totalPrice(finalPrice)
                .status(OrderStatus.PENDING)
                .build();

        // Çift yönlü JPA ilişkisini bağla (FK sahibi OrderItem)
        orderItems.forEach(item -> item.setOrder(order));

        // 6. Adım: Siparişi kaydet (CascadeType.ALL sayesinde kalemler de otomatik
        // INSERT edilir)
        Order savedOrder = orderRepository.save(order);
        log.info("Sipariş DB'ye kaydedildi. Sipariş ID={}", savedOrder.getId());

        // 7. Adım: Her ürünün stokunu düş (ve Redis önbelleğini temizle)
        for (OrderItem item : orderItems) {
            productService.decreaseStock(item.getProduct(), item.getQuantity());
        }

        // 8. Adım: Event yayınla (AFTER_COMMIT fazında asenkron bildirim & fatura
        // tetiklenir)
        eventPublisher.publishEvent(new OrderPlacedEvent(
                savedOrder.getId(),
                user.getId(),
                user.getEmail(),
                savedOrder.getTotalPrice()));

        log.info("OrderPlacedEvent yayınlandı. Sipariş başarıyla tamamlandı. Sipariş ID={}", savedOrder.getId());

        return orderMapper.toDto(savedOrder);
    }
}
