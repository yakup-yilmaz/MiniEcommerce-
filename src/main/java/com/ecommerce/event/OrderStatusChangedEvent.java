package com.ecommerce.event;

import com.ecommerce.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
// Sipariş durumu değiştiğindeki event payload
public class OrderStatusChangedEvent {
    private final Long orderId;
    private final Long userId;
    private final String userEmail;
    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;

}
