package com.charter.camunda.worker.validation.exception;

public class ValidationException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public ValidationException(String message, String errorCode, boolean retryable) {
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
