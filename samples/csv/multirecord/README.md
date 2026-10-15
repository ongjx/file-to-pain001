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

## Compatibility test

**The existing converter does not support this layout.** Both `default` and
`camel-case` expect a column-header row and one complete payment per data row.
They interpret PH as a header row and reject these files for missing required
columns (exit status 4). No XML is produced. That rejection does not validate or
process invoice/FX details.

`check_samples.py` independently checks CSV record widths, required values,
batch/payment ownership, unique references, dates, positive decimals, declared
counts, invoice reconciliation, FX arithmetic and expected per-currency totals.
This limited fixture check is not a bank validator or a payment converter.
It probes both existing layouts for all six files, verifies rejections leave no
output, and runs the existing flat basic.csv as a positive control. Actual exit
codes and diagnostics are recorded; expected rejection is reported as unsupported
conversion, not successful conversion.

From the project directory, after building the runner:

```sh
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/file-to-pain001-temurin-21.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn verify
python3 samples/csv/multirecord/check_samples.py
```

Results are saved to `target/multirecord-test-results.json`. An assertion fails
if checked mock rules fail, the positive control fails, or converter behavior
differs from its current rejection contract. Update the check when multi-record
support is implemented. Proper support needs a record parser and explicit
mappings for batch, invoice and FX information into pain.001; dropping detail
rows would lose source information.
