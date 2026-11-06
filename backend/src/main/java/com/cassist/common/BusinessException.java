package com.cassist.common;

import org.springframework.http.HttpStatus;

public final class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String safeMessage) {
        super(safeMessage);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
