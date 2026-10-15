package com.example.payments.input.csv;

import com.example.payments.conversion.PaymentConversionException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum CsvLayout {
    DEFAULT("default"), CAMEL_CASE("camel-case");

    private static final Set<String> OPTIONAL = Set.of("creditor_bic", "remittance");
    private static final String[] FIELDS = {
        "initiating_party_name", "debtor_name", "debtor_iban", "debtor_bic", "execution_date",
        "end_to_end_id", "creditor_name", "creditor_iban", "creditor_bic", "amount", "currency", "remittance"
    };
    private final String option;

    CsvLayout(String option) {
        this.option = option;
    }

    public String header(String field) {
        if (this == DEFAULT) {
            return field;
        }
        String[] words = field.split("_");
        StringBuilder result = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            result.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return result.toString();
    }

    public Set<String> headers() {
        return Arrays.stream(FIELDS).map(this::header).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> requiredHeaders() {
        return Arrays.stream(FIELDS).filter(f -> !OPTIONAL.contains(f))
                .map(this::header).collect(Collectors.toUnmodifiableSet());
    }

    public static CsvLayout fromOption(String value) throws PaymentConversionException {
        for (CsvLayout layout : values()) {
            if (layout.option.equals(value)) {
                return layout;
            }
        }
        throw new PaymentConversionException(PaymentConversionException.Kind.USAGE,
                "Unknown CSV layout; use default or camel-case.");
    }
}
