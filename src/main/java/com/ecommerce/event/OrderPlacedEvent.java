package com.ecommerce.event;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
// Sipariş verildiğinde fırlatılır normal entity kullanmıyoruz hibernate session
// lazyinitilization olabilir
public class OrderPlacedEvent {
    private final Long orderId;
    private final Long userId;
    private final String userEmail;
    private final BigDecimal totalPrice;
}
