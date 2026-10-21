package com.northbridge.coremock.web;

import org.springframework.http.HttpStatus;

/**
 * Business exception carrying core-mock's own error code and HTTP status.
 * Serialized by {@link ApiExceptionHandler} as {@code {"error":{"code":...,"message":...}}}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException scopeDenied(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "CORE_SCOPE_DENIED", message);
    }

    public static ApiException accountNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found");
    }

    public static ApiException recordNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CORE_RECORD_NOT_FOUND", "Payment status record not found");
    }

    public static ApiException accountExists(String accountNumber) {
        return new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "An account with number " + accountNumber + " already exists");
    }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", message);
    }
}
