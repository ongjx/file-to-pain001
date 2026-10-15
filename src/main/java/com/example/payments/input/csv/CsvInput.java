package com.example.payments.input.csv;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.input.PaymentFields;
import com.example.payments.pain001.v09.Document;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** CSV transport and source-location handling shared by both structural handlers. */
final class CsvInput {
    private CsvInput() { }
    @FunctionalInterface interface ReadDocument { Document read(CSVParser parser) throws PaymentConversionException; }
    static Document read(Path path, CSVFormat format, ReadDocument read) throws PaymentConversionException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new PaymentConversionException(PaymentConversionException.Kind.INPUT,
                    "Input must be a readable regular file on the host filesystem.");
        }
        try (PushbackReader reader = new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 1)) {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') reader.unread(first);
            try (CSVParser parser = format.parse(reader)) { return read.read(parser); }
        } catch (CSVException | UncheckedIOException e) {
            Throwable cause = e instanceof UncheckedIOException ? e.getCause() : e;
            var matcher = java.util.regex.Pattern.compile("(?:line|position|record)[^0-9]*([0-9]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(String.valueOf(cause.getMessage()));
            String location = matcher.find() ? "line/position " + matcher.group(1) : "unavailable";
            throw new PaymentConversionException(PaymentConversionException.Kind.VALIDATION,
                    "Malformed CSV; check quoting and record structure (parser location: " + location + ").");
        } catch (IllegalArgumentException e) {
            throw new PaymentConversionException(PaymentConversionException.Kind.VALIDATION,
                    "Invalid CSV header; blank or duplicate column names are not permitted.");
        } catch (IOException e) {
            throw new PaymentConversionException(PaymentConversionException.Kind.INPUT, "Cannot read input as UTF-8 CSV.", e);
        }
    }
    static PaymentFields flat(CSVRecord row, Map<String, String> headers) {
        Map<String, String> values = new HashMap<>();
        // A short record is diagnosed by its caller before field extraction.
        if (row.isConsistent()) headers.forEach((field, header) -> values.put(field, row.isMapped(header) ? row.get(header) : ""));
        return new PaymentFields(values, headers, row.getRecordNumber(), null);
    }
    static PaymentFields positional(CSVRecord row, List<String> fields, String type) {
        Map<String, String> values = new HashMap<>();
        if (fields != null && row.size() == fields.size()) {
            for (int i = 0; i < fields.size(); i++) values.put(fields.get(i), row.get(i));
        }
        return new PaymentFields(values, Map.of(), row.getRecordNumber(), type);
    }
}
