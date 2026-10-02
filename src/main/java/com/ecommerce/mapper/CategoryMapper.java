package com.ecommerce.mapper;

import com.ecommerce.dto.CategoryDto;
import com.ecommerce.dto.request.CategoryCreateRequest;
import com.ecommerce.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    CategoryDto toDto(Category category);

    Category toEntity(CategoryCreateRequest request);

    void updateEntityFromRequest(CategoryCreateRequest request, @MappingTarget Category category);
}
