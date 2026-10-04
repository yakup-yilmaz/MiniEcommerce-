package com.ecommerce.pattern.adapter.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FakePushClient {
    public String pushToDevice(String deviceKey, String title, String payloadJson) {

        String pushId = "PUSH-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("[PUSH CLIENT] to={} | title={} | payload={}", deviceKey, title, payloadJson);
        return pushId;

    }
}
