package com.example.payments;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import static com.example.payments.XmlChecks.*;
import static org.junit.jupiter.api.Assertions.*;

/** Compare source facts with independently parsed/validated output, including detail values. */
final class MultiRecordAssertions {
    private MultiRecordAssertions() { }

    static void assertFixture(Path fixture, String xml) throws Exception {
        independentlyValidate(xml);
        var output = parse(xml);
        String source = Files.readString(fixture);
        if (source.startsWith("\uFEFF")) source = source.substring(1);
        String[] header = null;
        String current = null;
        int invoiceIndex = 0;
        int transactions = 0;
        int invoices = 0;
        int exchanges = 0;
        Map<String, Integer> groupCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> groupSums = new LinkedHashMap<>();
        Map<String, BigDecimal> currencySums = new LinkedHashMap<>();
        try (var records = CSVFormat.RFC4180.parse(new java.io.StringReader(source))) {
            for (var row : records) {
                if (row.stream().allMatch(String::isBlank)) continue;
                switch (row.get(0)) {
                    case "PH" -> header = row.values();
                    case "TX" -> {
                        assertNotNull(header);
                        transactions++;
                        invoiceIndex = 0;
                        current = ROOT + "/p:PmtInf/p:CdtTrfTxInf[p:PmtId/p:EndToEndId='" + row.get(2) + "']";
                        assertEquals("1", value(output, "count(" + current + ")"));
                        assertEquals(row.get(3), value(output, current + "/p:Cdtr/p:Nm"));
                        assertEquals(row.get(4), value(output, current + "/p:CdtrAcct/p:Id/p:IBAN"));
                        assertEquals(row.get(5), value(output, current + "/p:CdtrAgt/p:FinInstnId/p:BICFI"));
                        assertEquals(row.get(6), value(output, current + "/p:Amt/p:InstdAmt"));
                        assertEquals(row.get(7), value(output, current + "/p:Amt/p:InstdAmt/@Ccy"));
                        assertEquals(row.get(8), value(output, current + "/p:RmtInf/p:Ustrd"));
                        String parent = current + "/..";
                        assertEquals(header[2], value(output, ROOT + "/p:GrpHdr/p:InitgPty/p:Nm"));
                        assertEquals(header[3], value(output, parent + "/p:Dbtr/p:Nm"));
                        assertEquals(header[4], value(output, parent + "/p:DbtrAcct/p:Id/p:IBAN"));
                        assertEquals(header[5], value(output, parent + "/p:DbtrAgt/p:FinInstnId/p:BICFI"));
                        assertEquals(header[6], value(output, parent + "/p:ReqdExctnDt/p:Dt"));
                        assertEquals("TRF", value(output, parent + "/p:PmtMtd"));
                        String key = header[1] + ":" + row.get(7);
                        groupCounts.merge(key, 1, Integer::sum);
                        groupSums.merge(key, new BigDecimal(row.get(6)), BigDecimal::add);
                        currencySums.merge(row.get(7), new BigDecimal(row.get(6)), BigDecimal::add);
                    }
                    case "IN" -> {
                        invoices++;
                        String invoice = current + "/p:RmtInf/p:Strd[" + (++invoiceIndex) + "]";
                        assertEquals(row.get(2), value(output, invoice + "/p:RfrdDocInf/p:Nb"));
                        assertEquals(row.get(3), value(output, invoice + "/p:RfrdDocInf/p:RltdDt"));
                        assertEquals(row.get(4), value(output, invoice + "/p:RfrdDocAmt/p:RmtdAmt"));
                        assertEquals(row.get(5), value(output, invoice + "/p:RfrdDocAmt/p:RmtdAmt/@Ccy"));
                    }
                    case "FX" -> {
                        exchanges++;
                        assertEquals(row.get(2), value(output, current + "/p:XchgRateInf/p:UnitCcy"));
                        assertEquals(row.get(3), value(output, current + "/p:Amt/p:InstdAmt/@Ccy"));
                        assertEquals(row.get(5), value(output, current + "/p:XchgRateInf/p:XchgRate"));
                        assertEquals(row.get(6), value(output, current + "/p:XchgRateInf/p:CtrctId"));
                        BigDecimal instructed = new BigDecimal(value(output, current + "/p:Amt/p:InstdAmt"));
                        BigDecimal rate = new BigDecimal(value(output, current + "/p:XchgRateInf/p:XchgRate"));
                        assertEquals(0, instructed.compareTo(new BigDecimal(row.get(4)).multiply(rate)));
                        assertEquals("", value(output, current + "/p:XchgRateInf/p:RateTp"));
                    }
                    default -> fail("Unexpected fixture tag");
                }
            }
        }
        assertEquals(Integer.toString(transactions), value(output, ROOT + "/p:GrpHdr/p:NbOfTxs"));
        assertEquals(Integer.toString(transactions), value(output, "count(" + ROOT + "/p:PmtInf/p:CdtTrfTxInf)"));
        assertEquals(Integer.toString(invoices), value(output, "count(" + ROOT + "/p:PmtInf/p:CdtTrfTxInf/p:RmtInf/p:Strd)"));
        assertEquals(Integer.toString(exchanges), value(output, "count(" + ROOT + "/p:PmtInf/p:CdtTrfTxInf/p:XchgRateInf)"));
        assertEquals(Integer.toString(groupCounts.size()), value(output, "count(" + ROOT + "/p:PmtInf)"));
        int index = 0;
        for (String key : groupCounts.keySet()) {
            String group = ROOT + "/p:PmtInf[" + (++index) + "]";
            assertEquals(groupCounts.get(key).toString(), value(output, group + "/p:NbOfTxs"));
            assertEquals(0, groupSums.get(key).compareTo(new BigDecimal(value(output, group + "/p:CtrlSum"))));
        }
        if (currencySums.size() > 1) assertEquals("", value(output, ROOT + "/p:GrpHdr/p:CtrlSum"));
        else assertEquals(0, currencySums.values().iterator().next().compareTo(new BigDecimal(value(output, ROOT + "/p:GrpHdr/p:CtrlSum"))));
    }
}
