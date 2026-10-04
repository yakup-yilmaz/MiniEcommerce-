package com.ecommerce.pattern.strategy;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DiscountStrategyFactory {

    private final Map<String, DiscountStrategy> strategies;
    private final String activeStrategyName;

    public DiscountStrategyFactory(Map<String, DiscountStrategy> strategies,
            @Value("${ecommerce.discount.active-strategy:NO_DISCOUNT}") String activeStrategyName) {
        this.strategies = strategies;
        this.activeStrategyName = activeStrategyName;
    }

    public DiscountStrategy getStrategy() {

        return strategies.getOrDefault(activeStrategyName, strategies.get("NO_DISCOUNT"));

    }

    public DiscountStrategy getStrategy(String strategyType) {
        return strategies.getOrDefault(strategyType, strategies.get("NO_DISCOUNT"));
    }

}
