package com.ecommerce.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;

    // Ürün listelenirken içindeki kategori bilgisi de gitsin diye bunu ekliyoruz
    private CategoryDto category;
}
