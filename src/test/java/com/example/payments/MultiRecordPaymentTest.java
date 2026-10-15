package com.example.payments;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.conversion.PaymentFileConverter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.payments.XmlChecks.*;

class MultiRecordPaymentTest {
    static final String PROFILE = "mock-multirecord-v1";
    static final Path SAMPLES = Path.of("samples/csv/multirecord");
    private final PaymentFileConverter converter = new PaymentFileConverter(
            Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC));
    @TempDir Path temporary;

    static Stream<Path> valid() throws Exception {
        try (var files = Files.walk(SAMPLES)) {
            return files.filter(path -> path.toString().endsWith(".csv") && !path.startsWith(SAMPLES.resolve("invalid")))
                    .sorted().toList().stream();
        }
    }

    static Stream<Arguments> invalid() throws Exception {
        return Files.readAllLines(SAMPLES.resolve("invalid/expected.tsv")).stream().map(line -> {
            String[] parts = line.split("\t", 2);
            return Arguments.of(SAMPLES.resolve("invalid").resolve(parts[0]), parts[1]);
        });
    }

    @ParameterizedTest(name = "multi-record values: {0}")
    @MethodSource("valid")
    void selectedProfilePreservesPaymentsDetailsAndTotals(Path fixture) throws Exception {
        byte[] before = Files.readAllBytes(fixture);
        String xml = converter.convertToXml(fixture.toString(), PROFILE);
        MultiRecordAssertions.assertFixture(fixture, xml);
        assertEquals("2026-10-15T08:00:00Z", value(parse(xml), ROOT + "/p:GrpHdr/p:CreDtTm"));
        assertArrayEquals(before, Files.readAllBytes(fixture));
        assertNotNull(converter.convert(fixture, PROFILE).getCstmrCdtTrfInitn());
    }

    @ParameterizedTest(name = "multi-record rejection: {0}")
    @MethodSource("invalid")
    void invalidSourceFailsForItsDocumentedReason(Path fixture, String reason) {
        var failure = assertThrows(PaymentConversionException.class, () -> converter.convertToXml(fixture.toString(), PROFILE));
        assertEquals(PaymentConversionException.Kind.VALIDATION, failure.kind());
        assertTrue(failure.getMessage().contains(reason), failure.getMessage());
        assertFalse(failure.getMessage().contains("GB00MOCK"));
        assertFalse(failure.getMessage().contains("Mock Supplier"));
        assertFalse(failure.getMessage().contains("SECRET"));
        if (!reason.equals("schema validation failed") && !reason.equals("no payments") && !reason.equals("Malformed CSV")) {
            assertTrue(failure.getMessage().contains("CSV record"));
            assertTrue(failure.getMessage().contains("field"));
        }
    }

    @Test
    void defaultsAliasesAndWrongProfilesAreExplicit() throws Exception {
        Path basic = Path.of("samples/csv/valid/basic.csv");
        for (String profile : List.of("flat-default-v1", "default")) {
            assertEquals("0.30", value(parse(converter.convertToXml(basic.toString(), profile)), ROOT + "/p:GrpHdr/p:CtrlSum"));
        }
        for (String profile : List.of("flat-camel-case-v1", "camel-case")) {
            assertEquals("0.30", value(parse(converter.convertToXml("samples/csv/valid/alternate-headers.csv", profile)), ROOT + "/p:GrpHdr/p:CtrlSum"));
        }
        for (String profile : new String[]{null, "", " ", "unknown"}) {
            assertEquals(PaymentConversionException.Kind.USAGE,
                    assertThrows(PaymentConversionException.class, () -> converter.convertToXml(basic.toString(), profile)).kind());
        }
        assertEquals(PaymentConversionException.Kind.USAGE,
                assertThrows(PaymentConversionException.class, () -> converter.convert(Path.of("data.xlsx"), PROFILE)).kind());
        assertEquals(PaymentConversionException.Kind.VALIDATION,
                assertThrows(PaymentConversionException.class, () -> converter.convert(basic, PROFILE)).kind());
        for (String profile : List.of("flat-default-v1", "flat-camel-case-v1")) {
            assertEquals(PaymentConversionException.Kind.VALIDATION,
                    assertThrows(PaymentConversionException.class, () -> converter.convert(SAMPLES.resolve("01-single-payment.csv"), profile)).kind());
        }
    }

    @Test
    void transactionAndBatchOrderIsPreservedWithoutMerging() throws Exception {
        var xml = parse(converter.convertToXml(SAMPLES.resolve("valid/mixed-batch.csv").toString(), PROFILE));
        assertEquals("CASE-001", value(xml, ROOT + "/p:PmtInf[1]/p:CdtTrfTxInf[1]/p:PmtId/p:EndToEndId"));
        assertEquals("CASE-003", value(xml, ROOT + "/p:PmtInf[1]/p:CdtTrfTxInf[2]/p:PmtId/p:EndToEndId"));
        assertEquals("CASE-002", value(xml, ROOT + "/p:PmtInf[2]/p:CdtTrfTxInf/p:PmtId/p:EndToEndId"));
        var batches = parse(converter.convertToXml(SAMPLES.resolve("valid/separate-batches.csv").toString(), PROFILE));
        assertEquals("2", value(batches, "count(" + ROOT + "/p:PmtInf)"));
        assertEquals("250.00", value(batches, ROOT + "/p:GrpHdr/p:CtrlSum"));
    }

    @Test
    void optionalFieldsBlankRecordsAndDetailBoundariesAreHandled() throws Exception {
        String csv = Files.readString(SAMPLES.resolve("01-single-payment.csv"))
                .replace("MOCKGB2L,125.50", ",125.50").replace("Monthly services", "");
        Path input = temporary.resolve("optional.csv");
        Files.writeString(input, csv + "\n,,,\n");
        String xml = converter.convertToXml(input.toString(), PROFILE);
        assertEquals("", value(parse(xml), ROOT + "/p:PmtInf/p:CdtTrfTxInf/p:CdtrAgt"));
        assertEquals("", value(parse(xml), ROOT + "/p:PmtInf/p:CdtTrfTxInf/p:RmtInf"));
    }

    @Test
    void profilesShareAmountValidationAndSupportConcurrentCalls() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                boolean multi = i % 2 == 0;
                calls.add(() -> converter.convertToXml(multi ? SAMPLES.resolve("04-fx-and-invoices.csv").toString()
                        : "samples/csv/valid/basic.csv", multi ? PROFILE : "flat-default-v1"));
            }
            var ids = new java.util.HashSet<String>();
            for (var result : executor.invokeAll(calls)) {
                String xml = result.get();
                independentlyValidate(xml);
                ids.add(value(parse(xml), ROOT + "/p:GrpHdr/p:MsgId"));
            }
            assertEquals(8, ids.size());
        }
        for (String profile : List.of("flat-default-v1", PROFILE)) {
            Path input = profile.equals(PROFILE) ? SAMPLES.resolve("invalid/invalid-amount.csv")
                    : Path.of("samples/csv/invalid/invalid-amount.csv");
            assertTrue(assertThrows(PaymentConversionException.class,
                    () -> converter.convertToXml(input.toString(), profile)).getMessage().contains("positive dot-decimal"));
        }
    }
}
