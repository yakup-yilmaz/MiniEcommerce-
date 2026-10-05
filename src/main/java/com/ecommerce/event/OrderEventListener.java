package com.ecommerce.event;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ecommerce.pattern.adapter.NotificationMessage;
import com.ecommerce.pattern.adapter.NotificationSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final List<NotificationSender> notificationSenders;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("[{}] Sipariş verildi olayı yakalandı: Sipariş ID={}, Tutar={} TL",
                Thread.currentThread().getName(), event.getOrderId(), event.getTotalPrice());

        NotificationMessage message = NotificationMessage.builder()
                .recipientEmail(event.getUserEmail())
                .recipientId(event.getUserId())
                .title("Siparişiniz Alındı!")
                .content(String.format("Sayın müşterimiz, #%d numaralı siparişiniz başarıyla alındı. Tutar: %s TL",
                        event.getOrderId(), event.getTotalPrice()))
                .build();

        notificationSenders.forEach(sender -> sender.send(message));
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("[{}] Sipariş durumu değişti olayı yakalandı: Sipariş ID={}, {} -> {}",
                Thread.currentThread().getName(), event.getOrderId(), event.getPreviousStatus(), event.getNewStatus());
        NotificationMessage message = NotificationMessage.builder()
                .recipientEmail(event.getUserEmail())
                .recipientId(event.getUserId())
                .title("Sipariş Durumu Güncellendi")
                .content(String.format("#%d numaralı siparişinizin durumu güncellendi: %s -> %s",
                        event.getOrderId(), event.getPreviousStatus(), event.getNewStatus()))
                .build();
        notificationSenders.forEach(sender -> sender.send(message));
    }

}
