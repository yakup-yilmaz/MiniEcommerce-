package com.ecommerce.repository;

import com.ecommerce.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Kullanıcının kendi siparişlerini görebilmesi için (sayfalı)
    Page<Order> findByUserId(Long userId, Pageable pageable);

    // Sipariş detayı çekerken N+1 problemini önlemek için OrderItem'ları tek
    // sorguda (FETCH JOIN) getiriyoruz.
    // Çünkü Order entity'sinde items liste olduğu için LAZY gelir.
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    // Aynı işlemi kullanıcının kendi sipariş detayı için
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :orderId AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdWithItems(@Param("orderId") Long orderId, @Param("userId") Long userId);

}
