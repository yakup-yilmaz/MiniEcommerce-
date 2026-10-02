package com.ecommerce.enums;

public enum OrderStatus {
    PENDING, // onay bekliyor
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED// sadece pendingten geçilebilir
}
