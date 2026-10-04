package com.ecommerce.pattern.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("FIXED_AMOUNT")
public class FixedAmountDiscountStrategy implements DiscountStrategy {

    private final BigDecimal amount;

    // yedekleme
    public FixedAmountDiscountStrategy(@Value("${ecommerce.discount.fixed-amount:50.00}") BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public BigDecimal apply(BigDecimal totalPrice) {
        if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal finalPrice = totalPrice.subtract(amount);
        return finalPrice.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

    }

    public BigDecimal getAmount() {
        return amount;
    }

}
