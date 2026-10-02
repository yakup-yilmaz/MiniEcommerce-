package com.ecommerce.exception;

import org.springframework.http.HttpStatus;

import lombok.Data;

@Data
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

}
