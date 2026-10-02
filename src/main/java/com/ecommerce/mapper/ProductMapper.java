package com.ecommerce.mapper;

import com.ecommerce.dto.ProductDto;
import com.ecommerce.dto.request.ProductCreateRequest;
import com.ecommerce.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = { CategoryMapper.class })
public interface ProductMapper {

    ProductDto toDto(Product product);

    // Kategori veritabanından bulunup Service katmanında ekleneceği için burada
    // ignore (yok say) diyoruz.
    @Mapping(target = "category", ignore = true)
    Product toEntity(ProductCreateRequest request);

    @Mapping(target = "category", ignore = true)
    void updateEntityFromRequest(ProductCreateRequest request, @MappingTarget Product product);
}
