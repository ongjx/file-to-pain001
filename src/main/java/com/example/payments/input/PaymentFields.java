package com.example.payments.input;

import com.example.payments.conversion.PaymentConversionException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Source values and location only; payment meaning lives in mapping/assembly. */
public final class PaymentFields {
    private final Map<String, String> values;
    private final Map<String, String> names;
    private final long record;
    private final String type;

    public PaymentFields(Map<String, String> values, Map<String, String> names, long record, String type) {
        this.values = Map.copyOf(values);
        this.names = Map.copyOf(names);
        this.record = record;
        this.type = type;
    }

    public String value(String field) { return values.getOrDefault(field, "").strip(); }

    public PaymentConversionException invalid(String field, String reason) {
        return new PaymentConversionException(PaymentConversionException.Kind.VALIDATION,
                (record == 0 ? "CSV" : "CSV record " + record) + (type == null ? "" : " (" + type + ")")
                + ", field " + names.getOrDefault(field, field) + ": " + reason + ".");
    }

    public String text(String field, int max, boolean required) throws PaymentConversionException {
        String value = value(field);
        if (required && value.isBlank()) throw invalid(field, "required value is blank");
        if (value.codePointCount(0, value.length()) > max) throw invalid(field, "exceeds schema maximum length " + max);
        if (value.codePoints().anyMatch(c -> !(c == 9 || c == 10 || c == 13 || c >= 0x20 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF))) {
            throw invalid(field, "contains a character forbidden in XML 1.0");
        }
        return value;
    }

    private String pattern(String field, String regex, String reason) throws PaymentConversionException {
        String value = value(field);
        if (!value.matches(regex)) throw invalid(field, reason);
        return value;
    }

    public String iban(String field) throws PaymentConversionException {
        return pattern(field, "[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}", "expected uppercase IBAN syntax");
    }

    public String bic(String field) throws PaymentConversionException {
        return pattern(field, "[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?", "expected uppercase 8- or 11-character BIC");
    }

    public String currency(String field) throws PaymentConversionException {
        return pattern(field, "[A-Z]{3}", "expected three uppercase letters");
    }

    public LocalDate date(String field) throws PaymentConversionException {
        String value = value(field);
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
                throw new DateTimeParseException("Invalid ISO date", "", 0);
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(field, "expected a real calendar date in YYYY-MM-DD format");
        }
    }

    public BigDecimal amount(String field) throws PaymentConversionException { return decimal(field, 18, 5); }
    public BigDecimal rate(String field) throws PaymentConversionException { return decimal(field, 11, 10); }

    private BigDecimal decimal(String field, int maxDigits, int maxFraction) throws PaymentConversionException {
        String value = pattern(field, "[0-9]+(\\.[0-9]+)?", "expected a positive dot-decimal number without grouping or exponent");
        BigDecimal amount = new BigDecimal(value);
        BigDecimal normalized = amount.stripTrailingZeros();
        int digits = normalized.precision() + Math.max(0, -normalized.scale());
        if (amount.signum() <= 0 || normalized.scale() > maxFraction || digits > maxDigits) {
            throw invalid(field, "must be positive with at most " + maxDigits + " total digits and " + maxFraction + " fractional digits");
        }
        return amount;
    }

    public java.math.BigInteger count(String field) throws PaymentConversionException {
        String value = pattern(field, "[0-9]+", "expected a positive base-10 integer");
        var count = new java.math.BigInteger(value);
        if (count.signum() <= 0) throw invalid(field, "expected a positive base-10 integer");
        return count;
    }
}
