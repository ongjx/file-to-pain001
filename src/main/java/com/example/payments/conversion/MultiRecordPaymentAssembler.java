package com.example.payments.conversion;

import com.example.payments.input.PaymentFields;
import com.example.payments.pain001.v09.CreditTransferTransaction34;
import com.example.payments.pain001.v09.Document;
import com.example.payments.pain001.v09.ExchangeRate1;
import com.example.payments.pain001.v09.PaymentInstruction30;
import com.example.payments.pain001.v09.ReferredDocumentInformation7;
import com.example.payments.pain001.v09.RemittanceAmount2;
import com.example.payments.pain001.v09.RemittanceInformation16;
import com.example.payments.pain001.v09.StructuredRemittanceInformation16;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import javax.xml.datatype.DatatypeFactory;

/** Per-file batch ownership, detail mapping and reconciliation for PH/TX/IN/FX records. */
public final class MultiRecordPaymentAssembler {
    private final PaymentDocumentAssembler assembler;
    private final boolean preserveSourceBatches;
    private final Set<String> batchIds = new HashSet<>();
    private final Set<String> invoiceIds = new HashSet<>();
    private PaymentFields headerFields;
    private PaymentFields transactionFields;
    private PaymentInstruction30 header;
    private CreditTransferTransaction34 transaction;
    private String batch;
    private BigInteger expectedTransactions;
    private BigInteger actualTransactions = BigInteger.ZERO;
    private BigDecimal invoiceSum = BigDecimal.ZERO;

    public MultiRecordPaymentAssembler(Clock clock, boolean preserveSourceBatches) {
        this.assembler = new PaymentDocumentAssembler(clock);
        this.preserveSourceBatches = preserveSourceBatches;
    }

    public void accept(String type, PaymentFields fields) throws PaymentConversionException {
        switch (type) {
            case "PH" -> header(fields);
            case "TX" -> transaction(fields);
            case "IN" -> invoice(fields);
            case "FX" -> exchange(fields);
            default -> throw fields.invalid("record_type", "unsupported record type");
        }
    }

    public Document finish() throws PaymentConversionException {
        closeBatch();
        return assembler.finish();
    }

    private void header(PaymentFields fields) throws PaymentConversionException {
        closeBatch();
        batch = fields.text("batch_id", Integer.MAX_VALUE, true);
        if (!batchIds.add(batch)) throw fields.invalid("batch_id", "duplicate batch ID within file");
        expectedTransactions = fields.count("transaction_count");
        header = assembler.header(fields);
        headerFields = fields;
        actualTransactions = BigInteger.ZERO;
    }

    private void transaction(PaymentFields fields) throws PaymentConversionException {
        if (header == null) throw fields.invalid("batch_id", "TX requires a preceding PH");
        if (!fields.value("batch_id").equals(batch)) throw fields.invalid("batch_id", "must match the current PH batch");
        closeTransaction();
        transaction = assembler.transaction(fields);
        transactionFields = fields;
        actualTransactions = actualTransactions.add(BigInteger.ONE);
        invoiceIds.clear();
        invoiceSum = BigDecimal.ZERO;
    }

    private void requireOwner(PaymentFields fields) throws PaymentConversionException {
        if (transaction == null) throw fields.invalid("end_to_end_id", "detail requires a preceding TX in the current batch");
        if (!fields.value("end_to_end_id").equals(transaction.getPmtId().getEndToEndId())) {
            throw fields.invalid("end_to_end_id", "must match the current TX");
        }
    }

    private void invoice(PaymentFields fields) throws PaymentConversionException {
        requireOwner(fields);
        String id = fields.text("invoice_id", 35, true);
        if (!invoiceIds.add(id)) throw fields.invalid("invoice_id", "duplicate invoice ID within payment");
        String currency = fields.currency("currency");
        if (!currency.equals(transaction.getAmt().getInstdAmt().getCcy())) {
            throw fields.invalid("currency", "invoice currency must match the current TX");
        }
        BigDecimal amount = fields.amount("invoice_amount");
        ReferredDocumentInformation7 document = new ReferredDocumentInformation7();
        document.setNb(id);
        document.setRltdDt(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(fields.date("invoice_date").toString()));
        RemittanceAmount2 remitted = new RemittanceAmount2();
        remitted.setRmtdAmt(PaymentDocumentAssembler.amount(amount, currency));
        StructuredRemittanceInformation16 structured = new StructuredRemittanceInformation16();
        structured.getRfrdDocInf().add(document);
        structured.setRfrdDocAmt(remitted);
        if (transaction.getRmtInf() == null) transaction.setRmtInf(new RemittanceInformation16());
        transaction.getRmtInf().getStrd().add(structured);
        invoiceSum = invoiceSum.add(amount);
    }

    private void exchange(PaymentFields fields) throws PaymentConversionException {
        requireOwner(fields);
        if (transaction.getXchgRateInf() != null) throw fields.invalid("record", "duplicate FX record for payment");
        String source = fields.currency("source_currency");
        String target = fields.currency("target_currency");
        if (!target.equals(transaction.getAmt().getInstdAmt().getCcy()) || source.equals(target)) {
            throw fields.invalid("target_currency", "must match TX currency and differ from source currency");
        }
        BigDecimal sourceAmount = fields.amount("source_amount");
        BigDecimal rate = fields.rate("exchange_rate");
        if (sourceAmount.multiply(rate).compareTo(transaction.getAmt().getInstdAmt().getValue()) != 0) {
            throw fields.invalid("source_amount", "source amount times exchange rate must equal the TX amount exactly");
        }
        ExchangeRate1 exchange = new ExchangeRate1();
        exchange.setUnitCcy(source);
        exchange.setXchgRate(rate);
        exchange.setCtrctId(fields.text("contract_id", 35, true));
        transaction.setXchgRateInf(exchange);
    }

    private void closeTransaction() throws PaymentConversionException {
        if (transaction == null) return;
        if (!invoiceIds.isEmpty() && invoiceSum.compareTo(transaction.getAmt().getInstdAmt().getValue()) != 0) {
            throw transactionFields.invalid("amount", "invoice amounts must sum exactly to the TX amount");
        }
        assembler.add(header, transaction, preserveSourceBatches ? batch : null);
        transaction = null;
    }

    private void closeBatch() throws PaymentConversionException {
        closeTransaction();
        if (header == null) return;
        if (!actualTransactions.equals(expectedTransactions)) throw headerFields.invalid("transaction_count", "declared count must equal actual TX count; batches cannot be empty");
        header = null;
    }
}
