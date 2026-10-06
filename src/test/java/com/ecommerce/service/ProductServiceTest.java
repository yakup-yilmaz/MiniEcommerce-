package com.ecommerce.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.dto.ProductDto;
import com.ecommerce.dto.request.ProductCreateRequest;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private Category category;
    private ProductDto productDto;
    private ProductCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(1L)
                .name("Elektronik")
                .description("Test Kategori")
                .build();

        product = Product.builder()
                .id(100L)
                .name("Test Laptop")
                .description("Test Aciklama")
                .price(new BigDecimal("15000.00"))
                .stock(10)
                .category(category)
                .build();

        productDto = new ProductDto();
        productDto.setId(100L);
        productDto.setName("Test Laptop");
        productDto.setPrice(new BigDecimal("15000.00"));
        productDto.setStock(10);

        createRequest = new ProductCreateRequest();
        createRequest.setName("Test Laptop");
        createRequest.setDescription("Test Aciklama");
        createRequest.setPrice(new BigDecimal("15000.00"));
        createRequest.setStock(10);
        createRequest.setCategoryId(1L);
    }

    @Test
    @DisplayName("Urun ID ile Basariyla Bulunmali")
    void testGetProductById_Success() {
        // Arrange
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(productMapper.toDto(product)).thenReturn(productDto);

        // Act
        ProductDto result = productService.getProductById(100L);

        // Assert
        assertNotNull(result);
        assertEquals("Test Laptop", result.getName());
        assertEquals(10, result.getStock());
        verify(productRepository, times(1)).findById(100L);
    }

    @Test
    @DisplayName("Olmayan Urun Arandiginda Hata Firlatmali")
    void testGetProductById_NotFound() {
        // Arrange
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            productService.getProductById(999L);
        });
        verify(productRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Yeni Urun Basariyla Olusturulmali")
    void testCreateProduct_Success() {
        // Arrange
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productMapper.toEntity(createRequest)).thenReturn(product);
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toDto(product)).thenReturn(productDto);

        // Act
        ProductDto result = productService.createProduct(createRequest);

        // Assert
        assertNotNull(result);
        assertEquals("Test Laptop", result.getName());
        verify(categoryRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).save(any(Product.class));
    }
}
