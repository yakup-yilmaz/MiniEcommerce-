package com.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ecommerce.entity.OrderItem;

@Service
public class PricingService {

    /**
     * Siparişteki tüm kalemlerin (Birim Fiyat * Adet) toplamını hesaplar.
     * Kuruş kaybı yaşanmaması için tutarı 2 basamağa yuvarlar.
     *
     */
    public BigDecimal calculateRawTotal(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItem item : items) {
            if (item != null && item.getUnitPrice() != null && item.getQuantity() != null) {
                BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                total = total.add(itemTotal);
            }
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
