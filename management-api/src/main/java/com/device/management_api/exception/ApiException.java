package com.device.management_api.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String error;
    private final Object details;

    public ApiException(HttpStatus status, String error, Object details) {
        super(error);
        this.status = status;
        this.error = error;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Object getDetails() {
        return details;
    }
}
