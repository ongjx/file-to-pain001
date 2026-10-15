package com.example.payments.input.csv;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.input.PaymentFileHandler;
import com.example.payments.pain001.v09.AccountIdentification4Choice;
import com.example.payments.pain001.v09.ActiveOrHistoricCurrencyAndAmount;
import com.example.payments.pain001.v09.AmountType4Choice;
import com.example.payments.pain001.v09.BranchAndFinancialInstitutionIdentification6;
import com.example.payments.pain001.v09.CashAccount38;
import com.example.payments.pain001.v09.CreditTransferTransaction34;
import com.example.payments.pain001.v09.CustomerCreditTransferInitiationV09;
import com.example.payments.pain001.v09.DateAndDateTime2Choice;
import com.example.payments.pain001.v09.Document;
import com.example.payments.pain001.v09.FinancialInstitutionIdentification18;
import com.example.payments.pain001.v09.GroupHeader85;
import com.example.payments.pain001.v09.PartyIdentification135;
import com.example.payments.pain001.v09.PaymentIdentification6;
import com.example.payments.pain001.v09.PaymentInstruction30;
import com.example.payments.pain001.v09.PaymentMethod3Code;
import com.example.payments.pain001.v09.RemittanceInformation16;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.xml.datatype.DatatypeFactory;
import org.apache.commons.csv.CSVException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;

public final class CsvPaymentFileHandler implements PaymentFileHandler {
    private final CsvLayout layout;
    private final Clock clock;

    public CsvPaymentFileHandler(CsvLayout layout, Clock clock) {
        this.layout = layout;
        this.clock = clock;
    }

    @Override
    public Document read(Path path) throws PaymentConversionException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new PaymentConversionException(PaymentConversionException.Kind.INPUT,
                    "Input must be a readable regular file on the host filesystem.");
        }
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                .setTrim(true).setIgnoreEmptyLines(true).setAllowMissingColumnNames(false)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get();
        try (PushbackReader reader = new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 1)) {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') {
                reader.unread(first);
            }
            try (CSVParser parser = format.parse(reader)) {
                validateHeaders(parser);
                return buildDocument(parser);
            }
        } catch (CSVException | UncheckedIOException e) {
            throw new PaymentConversionException(PaymentConversionException.Kind.VALIDATION,
                    "Malformed CSV; check quoting and record structure (parser location: " + parserLocation(e) + ").");
        } catch (IllegalArgumentException e) {
            throw new PaymentConversionException(PaymentConversionException.Kind.VALIDATION,
                    "Invalid CSV header; blank or duplicate column names are not permitted.");
        } catch (IOException e) {
            throw new PaymentConversionException(PaymentConversionException.Kind.INPUT,
                    "Cannot read input as UTF-8 CSV.", e);
        }
    }

    private void validateHeaders(CSVParser parser) throws PaymentConversionException {
        Set<String> actual = parser.getHeaderMap().keySet();
        Set<String> missing = new TreeSet<>(layout.requiredHeaders());
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw invalid(0, "header", "missing required columns: " + String.join(", ", missing));
        }
        if (!layout.headers().containsAll(actual)) {
            throw invalid(0, "header", "unknown columns for the selected layout");
        }
    }

    private Document buildDocument(CSVParser parser) throws PaymentConversionException {
        CustomerCreditTransferInitiationV09 message = new CustomerCreditTransferInitiationV09();
        GroupHeader85 header = new GroupHeader85();
        header.setMsgId(identifier());
        header.setCreDtTm(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(clock.instant().toString()));
        message.setGrpHdr(header);
        Map<GroupKey, PaymentInstruction30> groups = new LinkedHashMap<>();
        Map<String, DebtorIdentity> debtors = new HashMap<>();
        Set<String> references = new HashSet<>();
        Set<String> currencies = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        long count = 0;
        String initiatingParty = null;

        for (CSVRecord row : parser) {
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (!row.isConsistent()) {
                throw invalid(row.getRecordNumber(), "record", "field count differs from header");
            }
            for (String required : layout.requiredHeaders()) {
                if (row.get(required).isBlank()) {
                    throw invalid(row.getRecordNumber(), required, "required value is blank");
                }
            }
            String party = text(row, "initiating_party_name", 140);
            if (initiatingParty != null && !initiatingParty.equals(party)) {
                throw invalid(row.getRecordNumber(), layout.header("initiating_party_name"), "must be consistent throughout the file");
            }
            initiatingParty = party;
            String debtorName = text(row, "debtor_name", 140);
            String debtorIban = identifier(row, "debtor_iban", "[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}", "expected uppercase IBAN syntax");
            String debtorBic = identifier(row, "debtor_bic", "[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?", "expected uppercase 8- or 11-character BIC");
            DebtorIdentity identity = new DebtorIdentity(debtorName, debtorBic);
            DebtorIdentity previous = debtors.putIfAbsent(debtorIban, identity);
            if (previous != null && !previous.equals(identity)) {
                throw invalid(row.getRecordNumber(), layout.header("debtor_iban"), "conflicting debtor name or BIC for the same account");
            }
            LocalDate date = date(row);
            String reference = text(row, "end_to_end_id", 35);
            if (!references.add(reference)) {
                throw invalid(row.getRecordNumber(), layout.header("end_to_end_id"), "duplicate reference within file");
            }
            String currency = identifier(row, "currency", "[A-Z]{3}", "expected three uppercase letters");
            BigDecimal amount = amount(row);
            GroupKey key = new GroupKey(debtorName, debtorIban, debtorBic, date, currency);
            PaymentInstruction30 group = groups.computeIfAbsent(key, this::group);
            group.getCdtTrfTxInf().add(transaction(row, reference, amount, currency));
            group.setCtrlSum(group.getCtrlSum().add(amount));
            group.setNbOfTxs(Integer.toString(group.getCdtTrfTxInf().size()));
            total = total.add(amount);
            currencies.add(currency);
            count++;
        }
        if (count == 0) {
            throw invalid(0, "record", "CSV contains no payments");
        }
        header.setInitgPty(party(initiatingParty));
        header.setNbOfTxs(Long.toString(count));
        if (currencies.size() == 1) {
            header.setCtrlSum(total);
        }
        message.getPmtInf().addAll(groups.values());
        Document document = new Document();
        document.setCstmrCdtTrfInitn(message);
        return document;
    }

    private PaymentInstruction30 group(GroupKey key) {
        PaymentInstruction30 group = new PaymentInstruction30();
        group.setPmtInfId(identifier());
        group.setPmtMtd(PaymentMethod3Code.TRF);
        DateAndDateTime2Choice execution = new DateAndDateTime2Choice();
        execution.setDt(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(key.date().toString()));
        group.setReqdExctnDt(execution);
        group.setDbtr(party(key.name()));
        group.setDbtrAcct(account(key.iban()));
        group.setDbtrAgt(agent(key.bic()));
        group.setCtrlSum(BigDecimal.ZERO);
        return group;
    }

    private CreditTransferTransaction34 transaction(CSVRecord row, String reference, BigDecimal amount, String currency)
            throws PaymentConversionException {
        CreditTransferTransaction34 transaction = new CreditTransferTransaction34();
        PaymentIdentification6 paymentId = new PaymentIdentification6();
        paymentId.setEndToEndId(reference);
        transaction.setPmtId(paymentId);
        ActiveOrHistoricCurrencyAndAmount instructed = new ActiveOrHistoricCurrencyAndAmount();
        instructed.setValue(amount);
        instructed.setCcy(currency);
        AmountType4Choice amountChoice = new AmountType4Choice();
        amountChoice.setInstdAmt(instructed);
        transaction.setAmt(amountChoice);
        transaction.setCdtr(party(text(row, "creditor_name", 140)));
        transaction.setCdtrAcct(account(identifier(row, "creditor_iban", "[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}", "expected uppercase IBAN syntax")));
        String creditorBic = value(row, "creditor_bic");
        if (!creditorBic.isEmpty()) {
            transaction.setCdtrAgt(agent(identifier(row, "creditor_bic", "[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?", "expected uppercase 8- or 11-character BIC")));
        }
        String remittance = text(row, "remittance", 140);
        if (!remittance.isEmpty()) {
            RemittanceInformation16 information = new RemittanceInformation16();
            information.getUstrd().add(remittance);
            transaction.setRmtInf(information);
        }
        return transaction;
    }

    private String value(CSVRecord row, String field) {
        String header = layout.header(field);
        return row.isMapped(header) ? row.get(header).strip() : "";
    }

    private String text(CSVRecord row, String field, int max) throws PaymentConversionException {
        String value = value(row, field);
        if (value.codePointCount(0, value.length()) > max) {
            throw invalid(row.getRecordNumber(), layout.header(field), "exceeds schema maximum length " + max);
        }
        if (value.codePoints().anyMatch(c -> !(c == 9 || c == 10 || c == 13 || c >= 0x20 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF))) {
            throw invalid(row.getRecordNumber(), layout.header(field), "contains a character forbidden in XML 1.0");
        }
        return value;
    }

    private String identifier(CSVRecord row, String field, String pattern, String diagnostic) throws PaymentConversionException {
        String value = value(row, field);
        if (!value.matches(pattern)) {
            throw invalid(row.getRecordNumber(), layout.header(field), diagnostic);
        }
        return value;
    }

    private LocalDate date(CSVRecord row) throws PaymentConversionException {
        String value = value(row, "execution_date");
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
                throw new DateTimeParseException("Invalid ISO date", "", 0);
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(row.getRecordNumber(), layout.header("execution_date"), "expected a real calendar date in YYYY-MM-DD format");
        }
    }

    private BigDecimal amount(CSVRecord row) throws PaymentConversionException {
        String value = value(row, "amount");
        if (!value.matches("[0-9]+(\\.[0-9]+)?")) {
            throw invalid(row.getRecordNumber(), "amount", "expected a positive dot-decimal number without grouping or exponent");
        }
        BigDecimal amount = new BigDecimal(value);
        BigDecimal normalized = amount.stripTrailingZeros();
        int digits = normalized.precision() + Math.max(0, -normalized.scale());
        // Facets of the bundled ActiveOrHistoricCurrencyAndAmount_SimpleType.
        if (amount.signum() <= 0 || normalized.scale() > 5 || digits > 18) {
            throw invalid(row.getRecordNumber(), "amount", "must be positive with at most 18 total digits and 5 fractional digits");
        }
        return amount;
    }

    private static PartyIdentification135 party(String name) {
        PartyIdentification135 party = new PartyIdentification135();
        party.setNm(name);
        return party;
    }

    private static CashAccount38 account(String iban) {
        AccountIdentification4Choice identification = new AccountIdentification4Choice();
        identification.setIBAN(iban);
        CashAccount38 account = new CashAccount38();
        account.setId(identification);
        return account;
    }

    private static BranchAndFinancialInstitutionIdentification6 agent(String bic) {
        FinancialInstitutionIdentification18 institution = new FinancialInstitutionIdentification18();
        institution.setBICFI(bic);
        BranchAndFinancialInstitutionIdentification6 agent = new BranchAndFinancialInstitutionIdentification6();
        agent.setFinInstnId(institution);
        return agent;
    }

    private static String identifier() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static PaymentConversionException invalid(long record, String field, String reason) {
        return new PaymentConversionException(PaymentConversionException.Kind.VALIDATION,
                (record == 0 ? "CSV" : "CSV record " + record) + ", field " + field + ": " + reason + ".");
    }

    private static String parserLocation(Exception error) {
        Throwable cause = error instanceof UncheckedIOException ? error.getCause() : error;
        var matcher = java.util.regex.Pattern.compile("(?:line|position|record)[^0-9]*([0-9]+)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(String.valueOf(cause.getMessage()));
        return matcher.find() ? "line/position " + matcher.group(1) : "unavailable";
    }

    private record GroupKey(String name, String iban, String bic, LocalDate date, String currency) { }
    private record DebtorIdentity(String name, String bic) { }
}
