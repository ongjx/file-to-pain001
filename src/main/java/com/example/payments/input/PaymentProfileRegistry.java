package com.example.payments.input;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.input.csv.CsvLayout;
import com.example.payments.input.csv.CsvPaymentFileHandler;
import com.example.payments.input.csv.MultiRecordCsvPaymentFileHandler;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Known contracts only: no guessing, fallback, or runtime plugin discovery. */
public final class PaymentProfileRegistry {
    public static final String DEFAULT = "flat-default-v1";
    private static final PaymentInputProfile FLAT = PaymentInputProfile.flat(DEFAULT, CsvLayout.DEFAULT);
    private static final PaymentInputProfile CAMEL = PaymentInputProfile.flat("flat-camel-case-v1", CsvLayout.CAMEL_CASE);
    private static final PaymentInputProfile MULTI = new PaymentInputProfile("mock-multirecord-v1",
            PaymentInputProfile.Structure.MULTIRECORD_CSV, Set.of("csv"), Map.of(), Map.of(
            "PH", List.of("record_type", "batch_id", "initiating_party_name", "debtor_name", "debtor_iban", "debtor_bic", "execution_date", "transaction_count"),
            "TX", List.of("record_type", "batch_id", "end_to_end_id", "creditor_name", "creditor_iban", "creditor_bic", "amount", "currency", "remittance"),
            "IN", List.of("record_type", "end_to_end_id", "invoice_id", "invoice_date", "invoice_amount", "currency"),
            "FX", List.of("record_type", "end_to_end_id", "source_currency", "target_currency", "source_amount", "exchange_rate", "contract_id")), true);
    private static final Map<String, PaymentInputProfile> PROFILES = Map.of(
            DEFAULT, FLAT, "default", FLAT, CAMEL.id(), CAMEL, "camel-case", CAMEL, MULTI.id(), MULTI);
    private PaymentProfileRegistry() { }
    public static PaymentInputProfile resolve(String id) throws PaymentConversionException {
        PaymentInputProfile profile = id == null ? null : PROFILES.get(id);
        if (profile == null) throw new PaymentConversionException(PaymentConversionException.Kind.USAGE,
                "Unknown or blank input profile; use flat-default-v1, flat-camel-case-v1, or mock-multirecord-v1.");
        return profile;
    }
    public static PaymentFileHandler handler(PaymentInputProfile profile, Path path, Clock clock)
            throws PaymentConversionException {
        if (path == null) throw new PaymentConversionException(PaymentConversionException.Kind.USAGE, "A file path is required.");
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!profile.extensions().contains(extension)) throw new PaymentConversionException(PaymentConversionException.Kind.USAGE,
                "Unsupported input format for selected profile; expected a .csv file.");
        return switch (profile.structure()) {
            case FLAT_CSV -> new CsvPaymentFileHandler(profile, clock);
            case MULTIRECORD_CSV -> new MultiRecordCsvPaymentFileHandler(profile, clock);
        };
    }
}
