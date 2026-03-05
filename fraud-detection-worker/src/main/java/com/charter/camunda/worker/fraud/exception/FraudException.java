package com.charter.camunda.worker.fraud.exception;

public class FraudException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public FraudException(String message, String errorCode, boolean retryable) {
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
