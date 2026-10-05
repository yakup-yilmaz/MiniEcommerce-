package com.ecommerce.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class InvoiceEventListener {

    // Sipariş veritabanına kesin olarak kaydedildikten (AFTER_COMMIT) sonra
    // arka plandaki işçi thread havuzunda (AsyncThread) asenkron çalışır.
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        String invoiceNumber = String.format("INV-2026-%06d", event.getOrderId());
        log.info("[{}] E-Fatura asenkron olarak kesildi. Fatura No: {}, Alıcı: {}, Tutar: {} TL",
                Thread.currentThread().getName(), invoiceNumber, event.getUserEmail(), event.getTotalPrice());
    }
}
