package com.charter.camunda.worker.payment.exception;

public class PaymentException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public PaymentException(String message, String errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
