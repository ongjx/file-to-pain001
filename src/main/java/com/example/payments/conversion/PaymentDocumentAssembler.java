package com.example.payments.conversion;

import com.example.payments.input.PaymentFields;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.xml.datatype.DatatypeFactory;

/** Per-conversion common mapping, payment invariants, grouping and exact totals. */
public final class PaymentDocumentAssembler {
    private final Clock clock;
    private final Map<GroupKey, PaymentInstruction30> groups = new LinkedHashMap<>();
    private final Map<String, DebtorIdentity> debtors = new HashMap<>();
    private final Set<String> references = new HashSet<>();
    private final Set<String> currencies = new HashSet<>();
    private BigDecimal total = BigDecimal.ZERO;
    private String initiatingParty;
    private long count;

    public PaymentDocumentAssembler(Clock clock) {
        this.clock = clock;
    }

    public PaymentInstruction30 header(PaymentFields fields) throws PaymentConversionException {
        String party = fields.requiredText("initiating_party_name", 140);
        if (initiatingParty != null && !initiatingParty.equals(party)) {
            throw fields.invalid("initiating_party_name", "must be consistent throughout the file");
        }
        initiatingParty = party;
        String name = fields.requiredText("debtor_name", 140);
        String iban = fields.iban("debtor_iban");
        String bic = fields.bic("debtor_bic");
        DebtorIdentity identity = new DebtorIdentity(name, bic);
        DebtorIdentity previous = debtors.putIfAbsent(iban, identity);
        if (previous != null && !previous.equals(identity)) {
            throw fields.invalid("debtor_iban", "conflicting debtor name or BIC for the same account");
        }
        PaymentInstruction30 group = new PaymentInstruction30();
        group.setDbtr(party(name));
        group.setDbtrAcct(account(iban));
        group.setDbtrAgt(agent(bic));
        DateAndDateTime2Choice execution = new DateAndDateTime2Choice();
        execution.setDt(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(fields.date("execution_date").toString()));
        group.setReqdExctnDt(execution);
        return group;
    }

    public CreditTransferTransaction34 transaction(PaymentFields fields) throws PaymentConversionException {
        String reference = fields.requiredText("end_to_end_id", 35);
        if (!references.add(reference)) {
            throw fields.invalid("end_to_end_id", "duplicate reference within file");
        }
        CreditTransferTransaction34 transaction = new CreditTransferTransaction34();
        PaymentIdentification6 paymentId = new PaymentIdentification6();
        paymentId.setEndToEndId(reference);
        transaction.setPmtId(paymentId);
        AmountType4Choice choice = new AmountType4Choice();
        choice.setInstdAmt(amount(fields.amount("amount"), fields.currency("currency")));
        transaction.setAmt(choice);
        transaction.setCdtr(party(fields.requiredText("creditor_name", 140)));
        transaction.setCdtrAcct(account(fields.iban("creditor_iban")));
        if (!fields.value("creditor_bic").isEmpty()) {
            transaction.setCdtrAgt(agent(fields.bic("creditor_bic")));
        }
        String remittance = fields.optionalText("remittance", 140);
        if (!remittance.isEmpty()) {
            RemittanceInformation16 information = new RemittanceInformation16();
            information.getUstrd().add(remittance);
            transaction.setRmtInf(information);
        }
        return transaction;
    }

    public void add(PaymentInstruction30 source, CreditTransferTransaction34 transaction, String batch) {
        var amount = transaction.getAmt().getInstdAmt();
        GroupKey key = new GroupKey(batch, source.getDbtr().getNm(), source.getDbtrAcct().getId().getIBAN(),
                source.getDbtrAgt().getFinInstnId().getBICFI(), source.getReqdExctnDt().getDt().toXMLFormat(), amount.getCcy());
        PaymentInstruction30 group = groups.computeIfAbsent(key, ignored -> {
            // Never mutate the source header: one PH can contribute multiple currency groups.
            PaymentInstruction30 result = new PaymentInstruction30();
            result.setDbtr(source.getDbtr());
            result.setDbtrAcct(source.getDbtrAcct());
            result.setDbtrAgt(source.getDbtrAgt());
            result.setReqdExctnDt(source.getReqdExctnDt());
            result.setPmtInfId(identifier());
            result.setPmtMtd(PaymentMethod3Code.TRF);
            result.setCtrlSum(BigDecimal.ZERO);
            return result;
        });
        group.getCdtTrfTxInf().add(transaction);
        group.setCtrlSum(group.getCtrlSum().add(amount.getValue()));
        group.setNbOfTxs(Integer.toString(group.getCdtTrfTxInf().size()));
        currencies.add(amount.getCcy());
        total = total.add(amount.getValue());
        count++;
    }

    public Document finish() throws PaymentConversionException {
        if (count == 0) {
            throw PaymentFields.invalidFile("record", "CSV contains no payments");
        }
        GroupHeader85 header = new GroupHeader85();
        header.setMsgId(identifier());
        header.setCreDtTm(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(clock.instant().toString()));
        header.setInitgPty(party(initiatingParty));
        header.setNbOfTxs(Long.toString(count));
        if (currencies.size() == 1) {
            header.setCtrlSum(total);
        }
        CustomerCreditTransferInitiationV09 message = new CustomerCreditTransferInitiationV09();
        message.setGrpHdr(header);
        message.getPmtInf().addAll(groups.values());
        Document document = new Document();
        document.setCstmrCdtTrfInitn(message);
        return document;
    }

    public static ActiveOrHistoricCurrencyAndAmount amount(BigDecimal value, String currency) {
        ActiveOrHistoricCurrencyAndAmount amount = new ActiveOrHistoricCurrencyAndAmount();
        amount.setValue(value);
        amount.setCcy(currency);
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

    private record GroupKey(String batch, String name, String iban, String bic, String date, String currency) { }
    private record DebtorIdentity(String name, String bic) { }
}
