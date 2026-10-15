# Input profiles and client onboarding

The business operation is client payment file to pain.001. An input profile names
the exact source contract; two clients can send CSVs with different structures or
different meanings. Extension alone never selects a profile.

| Profile | Structure | Selection |
| --- | --- | --- |
| `flat-default-v1` | One payment per row, snake-case headers | Default when omitted; alias `default` |
| `flat-camel-case-v1` | Same structure, camel-case headers | Explicit; alias `camel-case` |
| `mock-multirecord-v1` | Positional PH/TX/IN/FX rows | Explicit; synthetic contract only |

All three use UTF-8 CSV, strict YYYY-MM-DD dates, positive dot-decimal amounts and
the prototype's IBAN account identification. Unknown, null or blank explicit
profiles fail without fallback. Submitting the wrong structure to a known profile
is a source-validation error.

## Implementation responsibilities

`PaymentProfileRegistry` contains immutable Java definitions. Each
`PaymentInputProfile` declares its structure, extensions, field names or ordered
record fields, and whether source batch identity participates in grouping.
`CsvPaymentFileHandler` serves both flat profiles. `MultiRecordCsvPaymentFileHandler`
checks record tags, per-type widths and required columns, then passes records to
`MultiRecordPaymentAssembler` for PH/TX state, detail ownership, counts and reconciliation.
`CsvInput` shares CSV transport, BOM handling and sanitized parser diagnostics.

`PaymentFields` shares strict value checks and carries logical record/type/field
context, including flat header aliases. `PaymentDocumentAssembler` maps common
payment fields into generated objects and owns file-wide consistency, duplicate
references, IDs/clock, grouping, counts and exact totals. The assemblers populate
generated objects directly; no parallel canonical payment hierarchy is used.
`Pain001XmlWriter` serializes and validates against the unchanged bundled XSD.

Each conversion owns its mutable parser, multi-record state, assembler, generated
document and marshaller. Registry definitions are immutable and may be shared.

## Multi-record mapping choices

The full positional contract is in the [sample guide](../samples/csv/multirecord/README.md).
PH fields supply initiating party, debtor and execution date. Its batch ID is a
grouping input; generated `PmtInfId` is not a verbatim copy. PH transaction_count
is checked against TX count before currency splitting. Different PH batches never
merge; one mixed-currency PH produces multiple currency-specific groups.

TX supplies the instructed creditor amount and transaction details. Only TX
amounts contribute to counts and control sums. IN maps one entry per invoice to
`RmtInf/Strd`, using `RfrdDocInf/Nb`, `RltdDt`, and
`RfrdDocAmt/RmtdAmt` plus currency. Nonblank TX remittance remains in `Ustrd`.
For this mock contract, invoice amounts must fully reconcile to TX in its currency.

FX rates mean target-currency units per one source-currency unit. Source currency,
rate and contract map to `XchgRateInf/UnitCcy`, `XchgRate` and `CtrctId`.
The target currency remains on `Amt/InstdAmt`. Source amount is reconciliation-only:
`source_amount * rate` must exactly equal the instructed amount, and its value is
recoverable from instructed amount/rate. No second instructed amount or rate type
is invented. Money follows XSD limits of 18 total/5 fractional digits; rates follow
11 total/10 fractional digits. No rounding, live rate lookup or FX deal occurs.

## Add a real client contract

1. Obtain a versioned client specification and representative accepted/rejected
   samples. Define account identifiers, field meanings, dates, decimals, ordering,
   batching, invoice and FX policies before assigning a profile ID.
2. For the same flat structure and value conventions, register a new
   `PaymentInputProfile` with the canonical-to-client header mapping and reuse the
   existing flat handler. For positional multi-record structure, define explicit
   record fields and verify that the existing state/mapping rules actually match.
3. If structure or semantics differ, implement or extend the relevant handler and
   profile contract deliberately. Current date/decimal validation is fixed to the
   three documented contracts; declaring a new profile does not automatically add
   other date formats, delimiters, partial invoice rules or FX rounding policies.
4. Reuse shared payment validation and assembly. Keep source interpretation in the
   handler. Extract a separate mapper interface only when a second mapping needs
   to vary independently from parsing. Reject unsupported source semantics instead
   of silently dropping records.
5. Add value-preservation and negative tests through the facade and packaged runner,
   independently validate XML, run existing regressions, and document profile
   selection for the client route. Supply the exact profile ID to the Java overload,
   runner `--profile`, or webMethods `profileId`.

Profiles are compiled Java definitions. There is no runtime mapping language or
plugin loader. The mock profile demonstrates the extension pattern; it does not
certify a real bank's invoice/FX conventions or submission rules.
