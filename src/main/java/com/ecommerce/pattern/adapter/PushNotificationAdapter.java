package com.ecommerce.pattern.adapter;

import org.springframework.stereotype.Component;

import com.ecommerce.pattern.adapter.client.FakePushClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Component
@Slf4j
public class PushNotificationAdapter implements NotificationSender {
    private final FakePushClient fakePushClient;

    @Override
    public void send(NotificationMessage message) {
        String deviceKey = "user-" + message.getRecipientId();
        String title = message.getTitle();
        String payloadJson = String.format("{\"userId\":%d,\"content\":\"%s\"}", message.getRecipientId(),
                message.getContent());
        String pushId = fakePushClient.pushToDevice(deviceKey, title, payloadJson);
        log.info("[PUSH ADAPTER] Bildirim başarıyla iletildi, Dönen ID: {}", pushId);
    }

}
