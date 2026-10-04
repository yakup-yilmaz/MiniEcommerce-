package com.ecommerce.pattern.adapter;

import org.springframework.stereotype.Component;

import com.ecommerce.pattern.adapter.client.FakeEmailClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationAdapter implements NotificationSender {

    private final FakeEmailClient fakeEmailClient;

    @Override
    public void send(NotificationMessage message) {

        String address = message.getRecipientEmail();
        String subject = message.getTitle();

        String htmlBody = "<h3>" + message.getTitle() + "</h3></br>" +
                message.getContent();
        boolean success = this.fakeEmailClient.sendMail(address, subject, htmlBody);

        if (!success) {
            log.warn("[EMAIL ADAPTER] E-posta gönderimi başarısız oldu: {}", address);
        }

    }

}
