package com.ecommerce.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductCreateRequest {
    
    @NotBlank(message = "Ürün adı boş olamaz")
    private String name;
    
    private String description;
    
    @NotNull(message = "Fiyat boş olamaz")
    @DecimalMin(value = "0.1", message = "Fiyat 0'dan büyük olmalıdır")
    private BigDecimal price;
    
    @NotNull(message = "Stok boş olamaz")
    @Min(value = 0, message = "Stok eksi olamaz")
    private Integer stock;
    
    @NotNull(message = "Kategori ID boş olamaz")
    private Long categoryId;
}
