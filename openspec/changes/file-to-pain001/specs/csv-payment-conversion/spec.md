## ADDED Requirements

### Requirement: Explicit CSV layout contracts
The converter SHALL support the default snake-case flat CSV contract in `design.md` and an explicitly selected camel-case equivalent through `flat-default-v1` and `flat-camel-case-v1`, retaining the legacy layout aliases. Flat profiles SHALL map by header name and reject unknown, duplicate, blank, or missing required headers and inconsistent record widths. All supported CSV profiles SHALL handle UTF-8 BOM, quoting, multiline fields, and LF/CRLF, ignore completely blank records, and retain embedded field content after trimming field ends.

#### Scenario: Header order and quoting vary
- **WHEN** valid default-profile data uses reordered headers, quoted commas, escaped quotes, Unicode, or quoted newlines
- **THEN** the handler retains the intended field values and creates the corresponding generated-model instructions

#### Scenario: Alternate layout is explicitly selected
- **WHEN** equivalent camel-case data is processed using the `camel-case` profile
- **THEN** it produces equivalent payment values to the default profile

#### Scenario: Unsupported headers are supplied
- **WHEN** a file has missing, unknown, blank, or duplicate headers for its selected profile
- **THEN** conversion fails with an actionable header diagnostic instead of guessing the mapping

### Requirement: Payment values are validated without invented data
Each payment SHALL contain the required values defined by its selected profile in `design.md`, obtained from one flat row or related multi-record rows. The initial profiles SHALL enforce strict ISO calendar dates, positive dot-decimal amounts using exact decimal arithmetic, uppercase currency/account/BIC syntax, at least one payment, unique end-to-end IDs across the file, consistent initiating party, and consistent debtor identity. The converter SHALL omit absent optional fields and reject values exceeding XSD limits rather than silently truncating or rounding them.

#### Scenario: Optional fields are absent
- **WHEN** valid input omits optional creditor BIC or remittance headers or values
- **THEN** conversion succeeds without creating empty optional elements

#### Scenario: A payment is invalid
- **WHEN** any record has an invalid date or amount, duplicate end-to-end ID, missing required value, conflicting party information, or schema-invalid content
- **THEN** the whole conversion fails and no successful subset is exported

### Requirement: Payments are grouped and counted consistently
The converter SHALL create one message per file and payment groups using debtor name, debtor IBAN, debtor BIC, execution date, and currency. Flat profiles SHALL retain that existing grouping key. The mock multi-record profile SHALL additionally include source batch identity so different PH batches never merge; a mixed-currency batch SHALL split into currency-specific payment groups. The converter SHALL preserve first-occurrence group order and transaction order within each group, calculate actual transaction counts and exact per-group control sums, set payment method `TRF`, and generate schema-compatible message/group identifiers and a UTC creation timestamp. It SHALL include a message control sum only for single-currency messages. Only actual payments (flat data rows or TX records) SHALL contribute to counts and sums; PH/IN/FX records SHALL not create additional payments.

#### Scenario: Multiple payment groups
- **WHEN** records differ in debtor, execution date, or currency
- **THEN** the corresponding groups remain separate and message/group counts and group sums match the source records

#### Scenario: Mixed currencies
- **WHEN** one input contains payments in multiple currencies
- **THEN** each payment retains its currency, payment groups are currency-specific, and the message-level control sum is absent

#### Scenario: Exact decimal arithmetic
- **WHEN** same-group records have amounts `0.10` and `0.20`
- **THEN** their group control sum equals decimal `0.30` with no binary floating-point artifact

#### Scenario: Separate source batches share payment characteristics
- **WHEN** two PH batches have the same debtor, execution date, and payment currency
- **THEN** their transactions remain in separate payment groups

### Requirement: Mock multi-record CSV has an explicit positional contract
The `mock-multirecord-v1` profile SHALL implement the headerless PH/TX/IN/FX record layouts defined in `design.md` and illustrated by `samples/csv/multirecord/`. It SHALL enforce exact field counts per record type, mandatory values, a PH before any TX, TX membership in the current batch, and IN/FX membership in the current transaction. It SHALL reject unknown record types, duplicate batch IDs, duplicate payment IDs, duplicate invoice IDs within one payment, duplicate FX details, and files or batches with no TX. A batch SHALL close at the next PH or end of file, a transaction SHALL close at the next TX/PH or end of file, and all closing validations SHALL run at end of file as well. Every PH declared transaction count SHALL equal its actual number of TX records before currency-based splitting.

#### Scenario: Variable-width records belong to one payment
- **WHEN** a valid PH is followed by a TX and its IN/FX details with their respective field counts
- **THEN** one credit transfer is constructed using the header and transaction information, with its details attached

#### Scenario: Record ownership or shape is invalid
- **WHEN** a TX precedes PH, a TX references a different batch, an IN/FX precedes TX or references another transaction, or a record type or field count is invalid
- **THEN** the entire conversion fails with logical record, record type where known, and field context

#### Scenario: Final batch count is wrong
- **WHEN** end of file is reached and the final PH declared count differs from its TX count
- **THEN** conversion fails before XML is returned or a destination is created

### Requirement: Invoice details are mapped and reconciled
For the mock profile, each IN SHALL become one `CdtTrfTxInf/RmtInf/Strd` entry containing `RfrdDocInf/Nb`, `RfrdDocInf/RltdDt`, and `RfrdDocAmt/RmtdAmt` with its currency. Multiple invoices SHALL retain source order and their individual amounts. Optional TX remittance SHALL remain in `RmtInf/Ustrd`. If IN records exist, their positive amounts SHALL sum exactly to the TX amount and use its currency. This full-payment reconciliation SHALL be a mock-profile rule, not a universal rule imposed on future client profiles.

#### Scenario: Two invoices settle one transfer
- **WHEN** one TX for GBP 300.00 has IN records for GBP 100.00 and GBP 200.00
- **THEN** the output has one payment and two structured invoice entries with their IDs, dates, currencies, and individual amounts intact

#### Scenario: Invoice amounts or currency disagree
- **WHEN** an invoice currency differs from its TX or its invoice sum differs from the TX amount
- **THEN** the entire conversion fails without dropping or combining invoice details to hide the mismatch

### Requirement: Foreign-exchange details have explicit rate semantics
For the mock profile, a TX amount SHALL be the instructed creditor amount in the target currency. At most one FX record SHALL belong to a TX. The FX target currency SHALL equal the TX currency; its source currency SHALL differ. `source_amount` and `exchange_rate` SHALL be positive dot-decimal values and `source_amount × exchange_rate` SHALL equal the TX amount exactly without rounding. Rate direction SHALL mean target-currency units per one source-currency unit. Mapping SHALL retain the TX in `Amt/InstdAmt` and place source currency, rate, and contract ID into `XchgRateInf/UnitCcy`, `XchgRateInf/XchgRate`, and `XchgRateInf/CtrctId`. Source amount SHALL be an explicitly documented reconciliation input rather than a separate transmitted XML amount; its value is recoverable from the exact instructed amount and nonzero rate. The converter SHALL not invent `RateTp`, execute an FX trade, fetch live rates, or silently discard an FX record.

#### Scenario: FX contract accompanies an instructed payment
- **WHEN** a USD 125.00 TX has an FX record for GBP 100.00 at 1.25 USD per GBP and a mock contract ID
- **THEN** XML retains USD 125.00 as instructed amount, GBP as unit currency, rate 1.25 and the contract ID, and reconciliation uses GBP 100.00

#### Scenario: FX arithmetic or ownership is inconsistent
- **WHEN** the target currency differs, the FX is duplicated, a rate is invalid, or source amount times rate differs from the TX amount
- **THEN** conversion fails rather than recalculating, rounding, or replacing the instructed amount
