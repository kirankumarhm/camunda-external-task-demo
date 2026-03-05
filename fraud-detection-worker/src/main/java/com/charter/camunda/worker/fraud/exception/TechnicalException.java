package com.charter.camunda.worker.fraud.exception;

public class TechnicalException extends RuntimeException {
    private final String errorCode;

    public TechnicalException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
