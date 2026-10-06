package com.ecommerce.pattern.strategy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PercentageDiscountStrategyTest {

    @Test
    @DisplayName("Standart tutarda yuzdelik indirim dogru hesaplanmalidir")
    void apply_WhenValidPrice_ShouldCalculateDiscountCorrectly() {
        PercentageDiscountStrategy strategy = new PercentageDiscountStrategy(new BigDecimal("0.10"));
        BigDecimal originalPrice = new BigDecimal("100.00");

        BigDecimal actualPrice = strategy.apply(originalPrice);

        assertEquals(new BigDecimal("90.00"), actualPrice);
    }

    @Test
    @DisplayName("Sifir veya negatif tutarda sifir donmelidir")
    void apply_WhenPriceIsZeroOrNegative_ShouldReturnZero() {
        PercentageDiscountStrategy strategy = new PercentageDiscountStrategy(new BigDecimal("0.10"));

        BigDecimal zeroResult = strategy.apply(BigDecimal.ZERO);
        BigDecimal negativeResult = strategy.apply(new BigDecimal("-50.00"));

        assertEquals(BigDecimal.ZERO, zeroResult);
        assertEquals(BigDecimal.ZERO, negativeResult);
    }

    @Test
    @DisplayName("Null tutar girildiginde sifir donmelidir")
    void apply_WhenPriceIsNull_ShouldReturnZero() {
        PercentageDiscountStrategy strategy = new PercentageDiscountStrategy(new BigDecimal("0.10"));

        BigDecimal result = strategy.apply(null);

        assertEquals(BigDecimal.ZERO, result);
    }
}
