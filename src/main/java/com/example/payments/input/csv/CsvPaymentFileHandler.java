package com.example.payments.input.csv;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.conversion.PaymentDocumentAssembler;
import com.example.payments.input.PaymentFields;
import com.example.payments.input.PaymentFileHandler;
import com.example.payments.input.PaymentInputProfile;
import com.example.payments.pain001.v09.Document;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.DuplicateHeaderMode;

public final class CsvPaymentFileHandler implements PaymentFileHandler {
    private final PaymentInputProfile profile;
    private final Clock clock;

    /** Compatibility constructor for callers selecting a legacy flat layout. */
    public CsvPaymentFileHandler(CsvLayout layout, Clock clock) {
        this(PaymentInputProfile.flat("legacy", layout), clock);
    }

    public CsvPaymentFileHandler(PaymentInputProfile profile, Clock clock) {
        this.profile = profile;
        this.clock = clock;
    }

    @Override
    public Document read(Path path) throws PaymentConversionException {
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                .setTrim(true).setIgnoreEmptyLines(true).setAllowMissingColumnNames(false)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get();
        return CsvInput.read(path, format, parser -> {
            Set<String> missing = new TreeSet<>();
            profile.headers().forEach((field, header) -> {
                if (profile.isRequiredField(field)) {
                    missing.add(header);
                }
            });
            missing.removeAll(parser.getHeaderMap().keySet());
            if (!missing.isEmpty()) {
                throw PaymentFields.invalidFile("header", "missing required columns: " + String.join(", ", missing));
            }
            if (!profile.headers().values().containsAll(parser.getHeaderMap().keySet())) {
                throw PaymentFields.invalidFile("header", "unknown columns for the selected layout");
            }
            PaymentDocumentAssembler assembler = new PaymentDocumentAssembler(clock);
            for (var row : parser) {
                if (row.stream().allMatch(String::isBlank)) {
                    continue;
                }
                PaymentFields fields = CsvInput.flat(row, profile.headers());
                if (!row.isConsistent()) {
                    throw fields.invalid("record", "field count differs from header");
                }
                for (String field : profile.headers().keySet()) {
                    if (profile.isRequiredField(field) && fields.value(field).isBlank()) {
                        throw fields.invalid(field, "required value is blank");
                    }
                }
                assembler.add(assembler.header(fields), assembler.transaction(fields), null);
            }
            return assembler.finish();
        });
    }
}
