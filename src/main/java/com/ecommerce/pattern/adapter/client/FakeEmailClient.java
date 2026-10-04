package com.ecommerce.pattern.adapter.client;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FakeEmailClient {

    public boolean sendMail(String address, String subject, String htmlBody) {
        log.info("[EMAIL CLIENT] to={} | subject={} | body={}", address, subject, htmlBody);
        return true;
    }

}
