package com.ecommerce.pattern.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component("NO_DISCOUNT")
public class NoDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal apply(BigDecimal totalPrice) {
        if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalPrice;
    }

}
