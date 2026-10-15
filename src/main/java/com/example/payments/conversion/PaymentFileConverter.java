package com.example.payments.conversion;

import com.example.payments.input.csv.CsvLayout;
import com.example.payments.input.PaymentProfileRegistry;
import com.example.payments.pain001.v09.Document;
import com.example.payments.xml.Pain001XmlWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
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
        return convertToXml(inputPath, PaymentProfileRegistry.DEFAULT);
    }

    public String convertToXml(String inputPath, String profileId) throws PaymentConversionException {
        if (inputPath == null || inputPath.isBlank()) {
            throw new PaymentConversionException(PaymentConversionException.Kind.USAGE, "inputPath is required.");
        }
        try {
            return toXml(convert(Path.of(inputPath), profileId));
        } catch (InvalidPathException e) {
            throw new PaymentConversionException(PaymentConversionException.Kind.INPUT, "inputPath is not a valid filesystem path.", e);
        }
    }

    public Document convert(Path path) throws PaymentConversionException {
        return convert(path, PaymentProfileRegistry.DEFAULT);
    }

    public Document convert(Path path, CsvLayout layout) throws PaymentConversionException {
        if (path == null || layout == null) {
            throw new PaymentConversionException(PaymentConversionException.Kind.USAGE, "A file path and CSV layout are required.");
        }
        return convert(path, layout == CsvLayout.DEFAULT ? "flat-default-v1" : "flat-camel-case-v1");
    }

    public Document convert(Path path, String profileId) throws PaymentConversionException {
        var profile = PaymentProfileRegistry.resolve(profileId);
        return PaymentProfileRegistry.handler(profile, path, clock).read(path);
    }

    public String toXml(Document document) throws PaymentConversionException {
        if (document == null) {
            throw new PaymentConversionException(PaymentConversionException.Kind.VALIDATION, "A payment document is required.");
        }
        return writer.write(document);
    }
}
