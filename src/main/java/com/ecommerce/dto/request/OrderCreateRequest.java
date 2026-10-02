package com.ecommerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class OrderCreateRequest {
    
    @NotNull(message = "Sipariş listesi boş olamaz")
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull(message = "Ürün ID boş olamaz")
        private Long productId;

        @NotNull(message = "Adet boş olamaz")
        @Min(value = 1, message = "En az 1 adet ürün almalısınız")
        private Integer quantity;
    }
}
