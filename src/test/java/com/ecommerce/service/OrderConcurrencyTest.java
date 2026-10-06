package com.ecommerce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.dto.request.OrderCreateRequest;
import com.ecommerce.dto.request.OrderCreateRequest.OrderItemRequest;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.Role;
import com.ecommerce.facade.OrderFacade;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;

@SpringBootTest
public class OrderConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Pessimistic Lock Testi: 10 Thread Aynı Anda Ürün Almaya Çalışıyor")
    void testPessimisticLock_Concurrency() throws InterruptedException {
        // 1. Test Verilerini Hazırlama
        Category category = Category.builder().name("Kategori").description("Test").build();
        categoryRepository.save(category);

        Product product = Product.builder()
                .name("Sınırlı Stoklu Ürün")
                .description("Sadece 100 adet var")
                .price(new BigDecimal("100.0"))
                .stock(100) // Başlangıç stoku 100
                .category(category)
                .build();
        productRepository.save(product);

        User customer = User.builder()
                .name("Test Müşteri")
                .email("test" + System.currentTimeMillis() + "@ecommerce.com")
                .password("Pass123*")
                .role(Role.CUSTOMER)
                .build();
        userRepository.save(customer);

        // 2. Çoklu Thread (Eşzamanlılık) Hazırlığı
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 3. 10 farklı kişi (thread) aynı anda üründen 5'er adet sipariş veriyor
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    OrderCreateRequest request = new OrderCreateRequest();
                    OrderItemRequest itemRequest = new OrderItemRequest();
                    itemRequest.setProductId(product.getId());
                    itemRequest.setQuantity(5); // Herkes 5 adet almak istiyor
                    request.setItems(List.of(itemRequest));

                    // Pessimistic Lock (PESSIMISTIC_WRITE) burada devreye giriyor!
                    orderFacade.placeOrder(request, customer);
                } catch (Exception e) {
                    System.out.println("Hata (Stok bitmiş olabilir veya başka hata): " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Tüm thread'lerin işlemini bitirmesini bekle
        latch.await();

        // 4. Doğrulama (Assert)
        // Başlangıçta 100 stok vardı. 10 kişi x 5 adet aldı = 50 adet satıldı.
        // Pessimistic Lock düzgün çalıştıysa (race condition olmadıysa) stok KESİN OLARAK 50 kalmalıdır.
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        
        System.out.println("==================================================");
        System.out.println("Beklenen Kalan Stok: 50");
        System.out.println("Gerçekleşen Kalan Stok: " + updatedProduct.getStock());
        System.out.println("==================================================");

        assertEquals(50, updatedProduct.getStock(), "Pessimistic Lock çalışmadı! Race condition oluştu.");
    }
}
