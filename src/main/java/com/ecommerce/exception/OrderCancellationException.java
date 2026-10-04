package com.ecommerce.exception;

import org.springframework.http.HttpStatus;

public class OrderCancellationException extends ApiException {
    public OrderCancellationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
