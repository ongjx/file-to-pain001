# Synthetic multi-record payment CSV examples

These six client payment files illustrate a **custom mock layout**. PH/TX/IN/FX
meanings below are defined for these fixtures only; they do not claim compatibility
with a bank's file specification. Names, accounts, contracts and rates are
synthetic. Accounts follow this project's mock syntax and are not for execution.

## Record contract: mock multi-record v1

There is no column-header row. The first field selects the positional layout.
Records have different field counts; do not pad them to the same width.

| Type | Fields, in order (including the record type) |
| --- | --- |
| PH — payment batch header | `PH,batch_id,initiating_party_name,debtor_name,debtor_iban,debtor_bic,execution_date,transaction_count` |
| TX — credit transfer | `TX,batch_id,end_to_end_id,creditor_name,creditor_iban,creditor_bic,amount,currency,remittance` |
| IN — invoice detail | `IN,end_to_end_id,invoice_id,invoice_date,invoice_amount,currency` |
| FX — exchange detail | `FX,end_to_end_id,source_currency,target_currency,source_amount,exchange_rate,contract_id` |

Dates use YYYY-MM-DD; decimals use a dot without grouping. Amounts and rates are
positive. TX creditor BIC and remittance may be blank, but their columns remain.
Currency codes contain three uppercase letters. CSV quoting escapes commas,
double quotes and embedded line breaks; text uses UTF-8.

A PH starts a batch; its TX rows follow until the next PH or end of file. A TX's
optional IN and FX records follow that TX before the next TX/PH. Details repeat
the owning TX's end-to-end ID. Batch IDs and payment IDs are unique within a file.
PH transaction_count counts TX records only. All batches in these examples share
the same initiating party.

Each TX amount is paid to the creditor in its currency. If IN records exist for a
TX, their amounts sum exactly to its amount and use its currency. At most one FX
record belongs to a TX; its target currency equals the TX currency, its source
currency differs, and `source_amount × exchange_rate = TX amount`. These examples
need no rounding. Totals are separate per currency. There is no trailer record.

## Files

| File | Batches | Payments | IN / FX records | Expected payment totals |
| --- | ---: | ---: | ---: | --- |
| [01-single-payment.csv](01-single-payment.csv) | 1 | 1 | 0 / 0 | GBP 125.50 |
| [02-invoices.csv](02-invoices.csv) | 1 | 2 | 3 / 0 | GBP 450.00 |
| [03-multiple-batches.csv](03-multiple-batches.csv) | 2 | 3 | 0 / 0 | GBP 300.00; EUR 75.25 |
| [04-fx-and-invoices.csv](04-fx-and-invoices.csv) | 1 | 2 | 2 / 2 | USD 125.00; EUR 90.00 |
| [05-quoted-unicode.csv](05-quoted-unicode.csv) | 1 | 1 | 1 / 0 | GBP 10.25; comma, escaped quotes, Unicode and multiline text |
| [06-bom-crlf.csv](06-bom-crlf.csv) | 1 | 1 | 1 / 1 | USD 62.50; actual UTF-8 BOM and CRLF bytes |

## Conversion and mapping

Select `mock-multirecord-v1` explicitly. The default and camel-case flat profiles
still reject these headerless files with status 4 and no output. PH/TX/IN/FX codes
are defined by this mock contract, not inferred from arbitrary client CSVs.

```sh
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/multirecord/04-fx-and-invoices.csv --profile mock-multirecord-v1
```

Each IN becomes one `RmtInf/Strd` entry with its invoice ID, date, amount and
currency. Nonblank TX remittance remains in `RmtInf/Ustrd`. FX rate direction is
target-currency units per one source-currency unit; source currency, rate and
contract map to `XchgRateInf/UnitCcy`, `XchgRate` and `CtrctId`. TX retains the
instructed target amount. FX source amount is reconciliation-only and is
recoverable from instructed amount/rate; it is not emitted as a second payment
amount. No rate type is invented. PH batch IDs control grouping, while output
payment-group IDs are generated. Different batches remain separate; mixed
currencies within a batch split into separate groups. IN/FX never add payment
counts or control sums.

## Additional focused fixtures

| File | Expected behavior |
| --- | --- |
| [valid/separate-batches.csv](valid/separate-batches.csv) | Two PH batches with the same debtor/date/currency stay as two groups; USD 250.00 total |
| [valid/mixed-batch.csv](valid/mixed-batch.csv) | One PH with three TX splits into USD 250.00 and GBP 125.00; noncontiguous USD payments retain order |
| [valid/interleaved-details.csv](valid/interleaved-details.csv) | FX before invoices, multiple invoices, and the same invoice ID on different TX are supported; USD 250.00 total |

The 47 focused invalid fixtures are enumerated with expected diagnostic fragments
in [invalid/expected.tsv](invalid/expected.tsv). They cover unknown tags, widths,
ordering/ownership, EOF and next-PH counts, empty batches/files, duplicate
identifiers/details, invoice reconciliation/date/amount, FX arithmetic/currencies/
rate limits, required/common payment values, party consistency, malformed quoting,
XML characters and aggregate XSD constraints. Java tests exercise every case
through both the facade and the packaged runner and check no partial output.

## Verification

`check_samples.py` independently checks the six core samples' record widths,
required values, ownership, references, dates, positive decimals, declared counts,
invoice reconciliation, FX arithmetic and documented per-currency totals. It then
converts each with `mock-multirecord-v1` and compares payment/header values,
structured invoice fields, exchange details, counts, groups and exact sums in XML.
It retains wrong-profile rejection checks for both flat layouts, input immutability
checks and the flat basic.csv positive control. This fixture checker is not a
bank validator. Independent XSD validation is performed by the Java test suite.

From the project directory:

```sh
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/file-to-pain001-temurin-21.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean verify
python3 samples/csv/multirecord/check_samples.py
```

The checker saves actual results to `target/multirecord-test-results.json`. It fails
if a core sample does not convert with its selected profile, its mapped fields or
totals disagree, a wrong profile emits output, or an input is modified.
