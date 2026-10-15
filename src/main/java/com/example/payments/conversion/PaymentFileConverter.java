package com.example.payments.conversion;

import com.example.payments.input.csv.CsvLayout;
import com.example.payments.input.csv.CsvPaymentFileHandler;
import com.example.payments.pain001.v09.Document;
import com.example.payments.xml.Pain001XmlWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/** Reusable facade: webMethods passes a server-local path and receives validated XML. */
public final class PaymentFileConverter {
    private final Clock clock;
    private final Pain001XmlWriter writer;

    public PaymentFileConverter() {
        this(Clock.systemUTC());
    }

    public PaymentFileConverter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.writer = new Pain001XmlWriter();
    }

    public String convertToXml(String inputPath) throws PaymentConversionException {
        if (inputPath == null || inputPath.isBlank()) {
            throw new PaymentConversionException(PaymentConversionException.Kind.USAGE, "inputPath is required.");
        }
        try {
            return toXml(convert(Path.of(inputPath)));
        } catch (InvalidPathException e) {
            throw new PaymentConversionException(PaymentConversionException.Kind.INPUT, "inputPath is not a valid filesystem path.", e);
        }
    }

    public Document convert(Path path) throws PaymentConversionException {
        return convert(path, CsvLayout.DEFAULT);
    }

    public Document convert(Path path, CsvLayout layout) throws PaymentConversionException {
        if (path == null || layout == null) {
            throw new PaymentConversionException(PaymentConversionException.Kind.USAGE, "A file path and CSV layout are required.");
        }
        if (path.getFileName() == null || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new PaymentConversionException(PaymentConversionException.Kind.USAGE, "Unsupported input format; expected a .csv file.");
        }
        return new CsvPaymentFileHandler(layout, clock).read(path);
    }

    public String toXml(Document document) throws PaymentConversionException {
        if (document == null) {
            throw new PaymentConversionException(PaymentConversionException.Kind.VALIDATION, "A payment document is required.");
        }
        return writer.write(document);
    }
}
