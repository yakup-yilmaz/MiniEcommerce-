package com.ecommerce.pattern.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("PERCENTAGE")
public class PercentageDiscountStrategy implements DiscountStrategy {

    private final BigDecimal percentage;

    public PercentageDiscountStrategy(@Value("${ecommerce.discount.percentage-rate:0.10}") BigDecimal percentage) {
        this.percentage = percentage;
    }

    @Override
    public BigDecimal apply(BigDecimal totalPrice) {
        if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discountAmount = totalPrice.multiply(percentage);

        BigDecimal finalPrice = totalPrice.subtract(discountAmount);

        return finalPrice.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

}
