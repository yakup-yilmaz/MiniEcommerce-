package com.ecommerce.service;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.dto.CategoryDto;
import com.ecommerce.dto.request.CategoryCreateRequest;
import com.ecommerce.entity.Category;
import com.ecommerce.exception.AlreadyExistsException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.repository.CategoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("Var olan kategori ID soruldugunda DTO donmelidir")
    void getCategoryById_WhenCategoryExists_ShouldReturnDto() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Elektronik");

        CategoryDto categoryDto = new CategoryDto();
        categoryDto.setId(1L);
        categoryDto.setName("Elektronik");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryMapper.toDto(category)).thenReturn(categoryDto);

        CategoryDto result = categoryService.getCategoryById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Elektronik", result.getName());
        verify(categoryRepository).findById(1L);
    }

    @Test
    @DisplayName("Bulunamayan kategori ID icin ResourceNotFoundException firlatilmalidir")
    void getCategoryById_WhenCategoryNotFound_ShouldThrowException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(99L));
        verify(categoryRepository).findById(99L);
        verify(categoryMapper, never()).toDto(any());
    }

    @Test
    @DisplayName("Ayni isimde kategori zaten varsa AlreadyExistsException firlatilmalidir")
    void createCategory_WhenNameAlreadyExists_ShouldThrowException() {
        CategoryCreateRequest request = new CategoryCreateRequest();
        request.setName("Elektronik");

        when(categoryRepository.existsByName("Elektronik")).thenReturn(true);

        assertThrows(AlreadyExistsException.class, () -> categoryService.createCategory(request));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Var olan kategori ID silinmek istendiginde delete cagrilmalidir")
    void deleteCategory_WhenCategoryExists_ShouldCallDelete() {
        Category category = new Category();
        category.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }
}
