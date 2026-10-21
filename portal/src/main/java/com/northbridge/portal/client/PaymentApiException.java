package com.northbridge.portal.client;

import org.springframework.http.HttpStatusCode;

/** A call to the decision tier failed with a structured error. */
public class PaymentApiException extends RuntimeException {

    private final int status;
    private final String code;

    public PaymentApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public PaymentApiException(HttpStatusCode status, Api.ErrorBody body) {
        super(body == null || body.error() == null ? "payment-api call failed"
                : body.error().message());
        this.status = status.value();
        this.code = body == null || body.error() == null ? "UNKNOWN" : body.error().code();
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
