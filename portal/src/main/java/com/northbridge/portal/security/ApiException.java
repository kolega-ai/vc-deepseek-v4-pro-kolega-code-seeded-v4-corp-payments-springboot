package com.northbridge.portal.security;

/** Portal-local error with a stable code, rendered by the global error handler. */
public class ApiException extends RuntimeException {

    private final String code;

    public ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
