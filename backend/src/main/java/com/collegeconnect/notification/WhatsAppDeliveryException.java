package com.collegeconnect.notification;

public class WhatsAppDeliveryException extends RuntimeException {

    public enum Outcome {
        RETRYABLE,
        PERMANENT,
        AMBIGUOUS
    }

    private final Outcome outcome;
    private final String providerCode;
    private final Integer httpStatus;

    public WhatsAppDeliveryException(
            String message,
            Outcome outcome,
            String providerCode,
            Integer httpStatus,
            Throwable cause) {
        super(message, cause);
        this.outcome = outcome;
        this.providerCode = providerCode;
        this.httpStatus = httpStatus;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
