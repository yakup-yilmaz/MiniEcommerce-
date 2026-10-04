package com.ecommerce.pattern.strategy;

import java.math.BigDecimal;

public interface DiscountStrategy {
    BigDecimal apply(BigDecimal totalPrice);
}
