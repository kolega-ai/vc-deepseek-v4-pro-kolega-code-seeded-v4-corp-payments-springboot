package com.northbridge.paymentapi.security;

/** Application-level error with a stable machine-readable code. */
public class ApiException extends RuntimeException {

    public enum Code {
        UNAUTHORIZED(401),
        FORBIDDEN(403),
        NOT_FOUND(404),
        VALIDATION_FAILED(400),
        STATE_TRANSITION_FORBIDDEN(409),
        APPROVAL_LIMIT_EXCEEDED(422),
        DAILY_LIMIT_EXCEEDED(422),
        SELF_APPROVAL_FORBIDDEN(403),
        SELF_ACTION_FORBIDDEN(403),
        BENEFICIARY_NOT_PAYABLE(422),
        ACCOUNT_NOT_ASSIGNED(403),
        DUPLICATE_RELEASE(409),
        RELEASE_NOT_RETRYABLE(409),
        REVERSAL_NOT_ALLOWED(409),
        COOLING_PERIOD_ACTIVE(422),
        COMPANY_INACTIVE(409),
        USER_DISABLED(403),
        DUPLICATE(409),
        INTERNAL_ERROR(500);

        public final int httpStatus;

        Code(int httpStatus) {
            this.httpStatus = httpStatus;
        }
    }

    private final Code code;

    public ApiException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
