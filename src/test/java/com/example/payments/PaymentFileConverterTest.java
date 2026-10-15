package com.example.payments;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.conversion.PaymentFileConverter;
import com.example.payments.input.csv.CsvLayout;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.payments.XmlChecks.*;

class PaymentFileConverterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
    private final PaymentFileConverter converter = new PaymentFileConverter(CLOCK);
    @TempDir Path temporary;

    static Stream<Path> valid() throws Exception { return fixtures("valid"); }
    static Stream<Path> invalid() throws Exception { return fixtures("invalid"); }

    private static Stream<Path> fixtures(String folder) throws Exception {
        try (var paths = Files.list(Path.of("samples/csv", folder))) {
            return paths.filter(p -> p.toString().endsWith(".csv")).sorted().toList().stream();
        }
    }

    static CsvLayout layout(Path path) {
        return path.getFileName().toString().equals("alternate-headers.csv") ? CsvLayout.CAMEL_CASE : CsvLayout.DEFAULT;
    }

    @ParameterizedTest(name = "valid: {0}")
    @MethodSource("valid")
    void allValidFixturesProduceIndependentlyValidatedXml(Path path) throws Exception {
        String xml = converter.toXml(converter.convert(path, layout(path)));
        independentlyValidate(xml);
        var parsed = parse(xml);
        assertEquals(NS, parsed.getDocumentElement().getNamespaceURI());
        assertEquals("Document", parsed.getDocumentElement().getLocalName());
        assertEquals("2026-10-15T08:00:00Z", value(parsed, ROOT + "/p:GrpHdr/p:CreDtTm"));
        Map<String, Integer> counts = Map.ofEntries(
                Map.entry("basic.csv", 2), Map.entry("multiple-groups.csv", 4), Map.entry("mixed-currencies.csv", 3),
                Map.entry("quoted-fields.csv", 1), Map.entry("reordered-columns.csv", 2), Map.entry("alternate-headers.csv", 2),
                Map.entry("optional-fields-omitted.csv", 1), Map.entry("optional-fields-blank.csv", 1),
                Map.entry("bom-crlf.csv", 2), Map.entry("five-decimal-amount.csv", 1), Map.entry("blank-lines.csv", 2));
        assertEquals(Integer.toString(counts.get(path.getFileName().toString())), value(parsed, ROOT + "/p:GrpHdr/p:NbOfTxs"));
        assertEquals(value(parsed, ROOT + "/p:GrpHdr/p:NbOfTxs"), value(parsed, "count(" + ROOT + "/p:PmtInf/p:CdtTrfTxInf)"));
    }

    @ParameterizedTest(name = "invalid: {0}")
    @MethodSource("invalid")
    void allInvalidFixturesFailWithUsefulSanitizedDiagnostics(Path path) {
        var failure = assertThrows(PaymentConversionException.class,
                () -> converter.toXml(converter.convert(path)));
        assertEquals(PaymentConversionException.Kind.VALIDATION, failure.kind());
        Map<String, String> reasons = Map.ofEntries(
                Map.entry("missing-header.csv", "header"), Map.entry("missing-required-value.csv", "debtor_name"),
                Map.entry("invalid-amount.csv", "amount"), Map.entry("zero-amount.csv", "amount"),
                Map.entry("negative-amount.csv", "amount"), Map.entry("exponent-amount.csv", "amount"),
                Map.entry("excess-fraction.csv", "amount"), Map.entry("excess-total-digits.csv", "amount"),
                Map.entry("invalid-date.csv", "execution_date"), Map.entry("ambiguous-date.csv", "execution_date"),
                Map.entry("duplicate-reference.csv", "end_to_end_id"), Map.entry("inconsistent-parties.csv", "initiating_party_name"),
                Map.entry("inconsistent-debtor.csv", "debtor_iban"), Map.entry("malformed.csv", "Malformed CSV"),
                Map.entry("record-width.csv", "field count"), Map.entry("schema-constraint.csv", "creditor_name"),
                Map.entry("schema-aggregate-sum.csv", "schema validation failed"), Map.entry("header-only.csv", "no payments"),
                Map.entry("duplicate-header.csv", "header"), Map.entry("blank-header.csv", "header"),
                Map.entry("unknown-header.csv", "unknown columns"), Map.entry("invalid-iban.csv", "debtor_iban"),
                Map.entry("invalid-bic.csv", "debtor_bic"), Map.entry("invalid-currency.csv", "currency"),
                Map.entry("invalid-xml-character.csv", "XML 1.0"));
        assertTrue(failure.getMessage().contains(reasons.get(path.getFileName().toString())), failure.getMessage());
        assertFalse(failure.getMessage().contains("GB00MOCK"));
        assertFalse(failure.getMessage().contains("Mock Supplier"));
    }

    @Test
    void publicStringFacadePreservesPaymentValuesAndExactTotals() throws Exception {
        var xml = parse(converter.convertToXml(Path.of("samples/csv/valid/basic.csv").toAbsolutePath().toString()));
        assertEquals("Mock Treasury", value(xml, ROOT + "/p:GrpHdr/p:InitgPty/p:Nm"));
        assertEquals("0.30", value(xml, ROOT + "/p:GrpHdr/p:CtrlSum"));
        assertEquals("0.30", value(xml, ROOT + "/p:PmtInf/p:CtrlSum"));
        assertEquals("Mock Debtor One", value(xml, ROOT + "/p:PmtInf/p:Dbtr/p:Nm"));
        assertEquals("GB00MOCK00000000000001", value(xml, ROOT + "/p:PmtInf/p:DbtrAcct/p:Id/p:IBAN"));
        assertEquals("MOCKGB2L", value(xml, ROOT + "/p:PmtInf/p:DbtrAgt/p:FinInstnId/p:BICFI"));
        assertEquals("2026-10-15", value(xml, ROOT + "/p:PmtInf/p:ReqdExctnDt/p:Dt"));
        assertEquals("TRF", value(xml, ROOT + "/p:PmtInf/p:PmtMtd"));
        for (int i = 1; i <= 2; i++) {
            String tx = ROOT + "/p:PmtInf/p:CdtTrfTxInf[" + i + "]";
            assertEquals("MOCK-00" + i, value(xml, tx + "/p:PmtId/p:EndToEndId"));
            assertEquals(i == 1 ? "0.10" : "0.20", value(xml, tx + "/p:Amt/p:InstdAmt"));
            assertEquals("GBP", value(xml, tx + "/p:Amt/p:InstdAmt/@Ccy"));
            assertEquals(i == 1 ? "Mock Supplier One" : "Mock Supplier Two", value(xml, tx + "/p:Cdtr/p:Nm"));
            assertEquals("GB00MOCK0000000000000" + (i + 1), value(xml, tx + "/p:CdtrAcct/p:Id/p:IBAN"));
            assertEquals("Mock invoice " + (i == 1 ? "A" : "B"), value(xml, tx + "/p:RmtInf/p:Ustrd"));
        }
        assertTrue(value(xml, ROOT + "/p:GrpHdr/p:MsgId").matches("[a-f0-9]{32}"));
    }

    @Test
    void generatedModelIsDirectlyInspectable() throws Exception {
        var document = converter.convert(Path.of("samples/csv/valid/basic.csv"));
        var message = document.getCstmrCdtTrfInitn();
        assertEquals(1, message.getPmtInf().size());
        assertEquals(new BigDecimal("0.30"), message.getPmtInf().getFirst().getCtrlSum());
        assertEquals("MOCK-001", message.getPmtInf().getFirst().getCdtTrfTxInf().getFirst().getPmtId().getEndToEndId());
    }

    @Test
    void noncontiguousGroupsPreserveOrderAndTotals() throws Exception {
        var xml = parse(converter.convertToXml("samples/csv/valid/multiple-groups.csv"));
        assertEquals("3", value(xml, "count(" + ROOT + "/p:PmtInf)"));
        assertEquals("MOCK-001", value(xml, ROOT + "/p:PmtInf[1]/p:CdtTrfTxInf[1]/p:PmtId/p:EndToEndId"));
        assertEquals("MOCK-002", value(xml, ROOT + "/p:PmtInf[1]/p:CdtTrfTxInf[2]/p:PmtId/p:EndToEndId"));
        assertEquals("112.34", value(xml, ROOT + "/p:PmtInf[1]/p:CtrlSum"));
        assertEquals("25.00", value(xml, ROOT + "/p:PmtInf[2]/p:CtrlSum"));
        assertEquals("10.00", value(xml, ROOT + "/p:PmtInf[3]/p:CtrlSum"));
        assertEquals("", value(xml, ROOT + "/p:GrpHdr/p:CtrlSum"));
    }

    @Test
    void mixedCurrenciesDoNotProduceCrossCurrencyMessageSum() throws Exception {
        var xml = parse(converter.convertToXml("samples/csv/valid/mixed-currencies.csv"));
        assertEquals("2", value(xml, "count(" + ROOT + "/p:PmtInf)"));
        assertEquals("", value(xml, ROOT + "/p:GrpHdr/p:CtrlSum"));
        assertEquals("0.30", value(xml, ROOT + "/p:PmtInf[1]/p:CtrlSum"));
        assertEquals("0.75", value(xml, ROOT + "/p:PmtInf[2]/p:CtrlSum"));
        assertEquals("USD", value(xml, ROOT + "/p:PmtInf[2]/p:CdtTrfTxInf/p:Amt/p:InstdAmt/@Ccy"));
    }

    @Test
    void escapingAndMultilineUnicodeAreLossless() throws Exception {
        String text = converter.convertToXml("samples/csv/valid/quoted-fields.csv");
        var xml = parse(text);
        assertTrue(text.contains("&amp;"));
        assertEquals("Mock Supplier, \"東京\" & Co", value(xml, ROOT + "/p:PmtInf/p:CdtTrfTxInf/p:Cdtr/p:Nm"));
        assertEquals("Mock invoice \"A\", part & parcel\nSecond line", value(xml, ROOT + "/p:PmtInf/p:CdtTrfTxInf/p:RmtInf/p:Ustrd"));
    }

    @Test
    void optionalFieldsDoNotCreateEmptyElements() throws Exception {
        var xml = parse(converter.convertToXml("samples/csv/valid/optional-fields-omitted.csv"));
        assertEquals("0", value(xml, "count(" + ROOT + "/p:PmtInf/p:CdtTrfTxInf/p:RmtInf)"));
        assertEquals("0", value(xml, "count(" + ROOT + "/p:PmtInf/p:CdtTrfTxInf/p:CdtrAgt)"));
    }

    @Test
    void schemaEnforcementRejectsDirectlyConstructedInvalidModel() throws Exception {
        var document = converter.convert(Path.of("samples/csv/valid/basic.csv"));
        document.getCstmrCdtTrfInitn().getPmtInf().getFirst().setDbtrAcct(null);
        assertEquals(PaymentConversionException.Kind.VALIDATION,
                assertThrows(PaymentConversionException.class, () -> converter.toXml(document)).kind());
    }

    @Test
    void wrongProfileAndBadPathsAreRejected() throws Exception {
        assertEquals(PaymentConversionException.Kind.VALIDATION,
                assertThrows(PaymentConversionException.class, () -> converter.convert(Path.of("samples/csv/valid/alternate-headers.csv"))).kind());
        for (String input : new String[]{null, "", "  "}) {
            assertEquals(PaymentConversionException.Kind.USAGE,
                    assertThrows(PaymentConversionException.class, () -> converter.convertToXml(input)).kind());
        }
        assertEquals(PaymentConversionException.Kind.INPUT,
                assertThrows(PaymentConversionException.class, () -> converter.convertToXml("missing.csv")).kind());
        assertEquals(PaymentConversionException.Kind.INPUT,
                assertThrows(PaymentConversionException.class, () -> converter.convertToXml("bad\0.csv")).kind());
        Path directory = Files.createDirectory(temporary.resolve("directory.csv"));
        assertEquals(PaymentConversionException.Kind.INPUT,
                assertThrows(PaymentConversionException.class, () -> converter.convert(directory)).kind());
        assertEquals(PaymentConversionException.Kind.USAGE,
                assertThrows(PaymentConversionException.class, () -> converter.convertToXml("anything.xlsx")).kind());
    }

    @Test
    void facadeIsSafeForConcurrentCallsWithoutSharedModelState() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> converter.convertToXml("samples/csv/valid/basic.csv"));
            }
            var ids = new java.util.HashSet<String>();
            for (var result : executor.invokeAll(calls)) {
                var xml = parse(result.get());
                assertEquals("2", value(xml, ROOT + "/p:GrpHdr/p:NbOfTxs"));
                ids.add(value(xml, ROOT + "/p:GrpHdr/p:MsgId"));
            }
            assertEquals(8, ids.size());
        }
    }

    @Test
    void officialSchemaBytesRemainUnmodified() throws Exception {
        byte[] schema = getClass().getResourceAsStream("/xsd/pain.001.001.09.xsd").readAllBytes();
        assertEquals("de038b373e47b0077b1832ddd81f4b2f1eb25d35721f62da1e38b7f5a09fda24",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(schema)));
    }
}
