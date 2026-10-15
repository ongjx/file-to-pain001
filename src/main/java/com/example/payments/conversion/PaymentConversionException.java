package com.example.payments.conversion;

/** A conversion failure with a category that callers can handle without parsing messages. */
public final class PaymentConversionException extends Exception {
    public enum Kind { USAGE, INPUT, VALIDATION, PROCESSING }

    private final Kind kind;

    public PaymentConversionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public PaymentConversionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
