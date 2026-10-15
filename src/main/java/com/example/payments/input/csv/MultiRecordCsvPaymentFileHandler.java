package com.example.payments.input.csv;

import com.example.payments.conversion.MultiRecordPaymentAssembler;
import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.input.PaymentFields;
import com.example.payments.input.PaymentFileHandler;
import com.example.payments.input.PaymentInputProfile;
import com.example.payments.pain001.v09.Document;
import java.nio.file.Path;
import java.time.Clock;
import org.apache.commons.csv.CSVFormat;

/** Headerless, positional PH/TX/IN/FX contract. Every read owns its parsing state. */
public final class MultiRecordCsvPaymentFileHandler implements PaymentFileHandler {
    private final PaymentInputProfile profile;
    private final Clock clock;

    public MultiRecordCsvPaymentFileHandler(PaymentInputProfile profile, Clock clock) {
        this.profile = profile;
        this.clock = clock;
    }

    @Override
    public Document read(Path path) throws PaymentConversionException {
        CSVFormat format = CSVFormat.RFC4180.builder().setTrim(true).setIgnoreEmptyLines(true).get();
        return CsvInput.read(path, format, parser -> {
            MultiRecordPaymentAssembler assembler = new MultiRecordPaymentAssembler(clock, profile.preserveSourceBatches());
            for (var row : parser) {
                if (row.stream().allMatch(String::isBlank)) {
                    continue;
                }
                String type = row.get(0).strip();
                var columns = profile.records().get(type);
                // Unknown source tags are not echoed into diagnostics.
                PaymentFields fields = CsvInput.positional(row, columns, columns == null ? null : type);
                if (columns == null) {
                    throw fields.invalid("record_type", "unknown record type for selected profile");
                }
                if (row.size() != columns.size()) {
                    throw fields.invalid("record", "field count differs from record type");
                }
                for (String field : columns) {
                    if (!profile.isRequiredField(type, field)) {
                        continue;
                    }
                    if (fields.value(field).isBlank()) {
                        throw fields.invalid(field, "required value is blank");
                    }
                }
                assembler.accept(type, fields);
            }
            return assembler.finish();
        });
    }
}
