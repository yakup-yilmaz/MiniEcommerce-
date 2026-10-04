package com.ecommerce.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import com.ecommerce.dto.ProductDto;
import com.ecommerce.dto.request.ProductCreateRequest;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public Page<ProductDto> getProductWithFilters(Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String name,
            Pageable pageable) {
        log.info("Ürünler filtreleniyor: categoryId={}, minPrice={}, maxPrice={}, name={}",
                categoryId, minPrice, maxPrice, name);

        Page<Product> products = productRepository.findWithFilters(
                categoryId, minPrice, maxPrice, name, pageable);
        return products.map(productMapper::toDto);

    }

    @Cacheable(value = "products", key = "#id")

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        log.info("Ürün DB'den getiriliyor: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return productMapper.toDto(product);
    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public ProductDto createProduct(ProductCreateRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        Product product = productMapper.toEntity(request);
        product.setCategory(category);

        Product savedProduct = productRepository.save(product);
        log.info("Yeni ürün oluşturuldu: {}", savedProduct.getId());
        return productMapper.toDto(savedProduct);
    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public ProductDto updateProduct(Long id, ProductCreateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        productMapper.updateEntityFromRequest(request, product);
        product.setCategory(category);
        Product updatedProduct = productRepository.save(product);
        return productMapper.toDto(updatedProduct);

    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public void deleteProduct(Long id) {
        log.info("Ürün siliniyor, id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        productRepository.delete(product);
    }

    public void checkStock(Product product, Integer requestedQuantity) {
        if (product.getStock() < requestedQuantity) {
            throw new InsufficientStockException(
                    String.format("Ürün '%s' için yetersiz stok! Mevcut: %d, İstenen: %d",
                            product.getName(), product.getStock(), requestedQuantity));
        }
    }

    @CacheEvict(value = "products", key = "#product.id")
    @Transactional
    public void decreaseStock(Product product, Integer quantity) {
        checkStock(product, quantity);
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
        log.info("Stok düşüldü: Ürün ID={}, Kalan stok={}", product.getId(), product.getStock());
    }

    // Sipariş modülü için: Kilitli Entity getirme (Pessimistic Lock - Race
    // condition önler)
    @Transactional
    public Product getProductEntityWithLock(Long id) {
        return productRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    @Transactional(readOnly = true)
    public Product getProductEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

}
