## Context

The workspace contains an earlier webMethods CSV-to-XML proof of concept and the implemented `file-to-pain001/` Java 21/JAXB 4 library with its bundled pain.001 schema. The library currently accepts two flat CSV layouts. Six synthetic PH/TX/IN/FX multi-record CSVs have been generated and tested: they satisfy their documented mock contract but are rejected by both existing flat layouts. The next extension adds explicit input profiles and a second structural handler. The old four-column CSV is not a suitable payment model. Retain Java 21/JAXB 4 as the user-selected baseline.

The user selected direct population of Java classes generated from a pain.001 XSD. Java owns source mapping and XML serialization. The primary use is a direct call from a webMethods Java service into our compiled library JAR, taking an input path and returning XML. The Maven/CLI runner is a local development tool. No bank submission destination has been specified.

This document records the implementation contract, including the planned profile/multi-record extension. Completed baseline work and pending extension work are distinguished in `tasks.md`; historical implementation evidence remains in the project's `docs/verification.md`. Updating this contract does not certify that the extension is implemented.

## Goals / Non-Goals

**Goals:** Extend the Java 21/JAXB 4 library so a webMethods Java service can supply a file-path string and optional versioned input profile and receive validated pain.001 XML. Preserve default flat conversion, select handlers by explicit profile, add the documented mock multi-record contract, and share payment validation and document assembly. Populate schema-generated Java objects directly. Include local runner support, adapter guidance, and acceptance tests proving payment and invoice/FX mappings.

**Non-Goals:** Arbitrary format inference, Excel, PDF, bank connectivity, recipient-bank usage profiles, automatic payment execution or FX dealing, live exchange-rate lookup, production idempotency, production file-size guarantees, multiple schema versions, runtime plugin discovery, a user-authored mapping language, and deployment into the live webMethods server. Client input profiles are in scope; actual client/bank contracts remain future additions. There is no intermediate canonical payment model, JSON round trip, or FLOW MAP requirement.

## Decisions

### 1. Schema and Java baseline

Propose `pain.001.001.09` as the single prototype schema. This is an explicit supported edition, not a claim that it is the latest or accepted by every bank. Obtain its original XSD from the official ISO 20022 archive, inspect its namespace and imports, and retain all required dependencies locally. Record the actual download URL, message identifier, retrieval date, SHA-256, and original notices in `src/main/resources/xsd/README.md`.

The schema itself belongs in `src/main/resources/xsd/pain.001.001.09.xsd`. Do not recreate a reduced schema from memory or modify it to make sample data pass. An XSD is the XML schema; a second schema file is only needed if the official XSD references one.

Target Java 21 using Maven compiler release `21` and a mutually compatible JAXB 4.x API/runtime/XJC stack, using `jakarta.xml.bind` APIs. Pin exact stable dependency and plugin versions after checking their build/runtime requirements at implementation time. Maven runs code generation in `generate-sources`; generated Java goes to `target/generated-sources/jaxb` under `com.example.payments.pain001.v09` and is compiled into the JAR. Generated files are not manually edited or duplicated under handwritten source directories. An optional sources JAR can make them convenient to inspect.

Java 17 with JAXB 4 is a fallback only if the actual target server requires it. A Java 21 build cannot run on Java 17; switching to that fallback requires an explicit compiler-target change and verification, not an automatic runtime fallback. JAXB 4 requires Java 11 or newer and does not require a separate Java-21-specific JAXB edition. Confirm the deployed Integration Server JVM is Java 21 or newer and check package classloading before live execution; general product support alone is not evidence of the configured JVM.

### 2. One Maven module with a reusable core

Proposed layout:

```text
file-to-pain001/
  pom.xml
  README.md
  docs/webmethods-integration.md
  .gitignore
  src/main/java/com/example/payments/
    cli/PaymentFileRunner.java
    conversion/PaymentFileConverter.java
    conversion/PaymentDocumentAssembler.java
    input/PaymentFileHandler.java
    input/PaymentInputProfile.java
    input/PaymentProfileRegistry.java
    input/csv/CsvPaymentFileHandler.java
    input/csv/CsvLayout.java
    input/csv/MultiRecordCsvPaymentFileHandler.java
    xml/Pain001XmlWriter.java
  src/main/resources/xsd/
    pain.001.001.09.xsd
    README.md
  src/test/java/com/example/payments/
  src/test/resources/expected/
  samples/csv/
    README.md
    valid/
    invalid/
    multirecord/
  target/generated-sources/jaxb/   # generated; ignored
```

Retain the small `PaymentFileHandler` interface accepting a `Path` and returning the generated pain.001 `Document`. `PaymentFileConverter` resolves the selected profile through a small registry and invokes its handler. `Pain001XmlWriter` serializes and validates the result. The public core accepts normal Java values, contains no webMethods dependencies, and does not terminate the process.

Keep `PaymentFileConverter.convertToXml(String inputPath)` returning fully validated XML with the existing default flat behavior. Add `convertToXml(String inputPath, String profileId)` and `convert(Path inputPath, String profileId)` for explicit profiles. Retain `convert(Path)`, `convert(Path, CsvLayout)`, and `toXml(Document)` for compatibility and model inspection. Unknown, null, or blank explicit profile IDs fail with usage errors; only omitted selection defaults. The webMethods interface exposes strings rather than generated classes or JAXB types.

### 2a. Profiles, structural handlers, and shared payment rules

A profile identifies a versioned client input contract, not just a filename extension. The built-in registry is defined in Java:

| Profile ID | Handler | Contract / compatibility |
| --- | --- | --- |
| `flat-default-v1` | Existing flat CSV handler | Snake-case headers; default when selection is omitted; legacy `default` layout alias |
| `flat-camel-case-v1` | Same flat CSV handler | Camel-case headers; legacy `camel-case` layout alias |
| `mock-multirecord-v1` | New multi-record CSV handler | Headerless, positional PH/TX/IN/FX mock contract in section 4a |

Profiles own explicit header/position mappings, required fields, accepted extensions, strict date/number conventions, and grouping policy. Initially all three accept `.csv`, ISO dates, and dot-decimal amounts. A new client sharing a structure reuses its handler with a new profile when its explicit mapping differs. Structurally different files get a different handler. The extension does not add speculative client profiles or alternate date formats without a concrete contract and fixtures. Never infer a profile from `.csv` or retry other handlers after validation fails.

The flat handler owns header lookup and row parsing. The multi-record handler owns the current PH/TX state, row ownership, declared counts, and invoice/FX reconciliation. Their mapping can remain internal; do not add separate parser/mapper interfaces that only forward calls. Introduce such a seam when parsing or mapping has a real independent variation.

Extract `PaymentDocumentAssembler` and small shared value-validation helpers from the current flat handler. Both handlers use that implementation for common payment rules, duplicate references across the entire file, party consistency, generated IDs, injected clock, grouping, actual counts, and exact totals. Pass generated payment objects and minimal source/group context internally; do not create a parallel canonical payment hierarchy. The handler still returns a complete generated `Document` through the existing interface. Source record/type/field context must survive shared validation so diagnostics remain useful and sanitized.

The registry and profile definitions may be reused if immutable. Each conversion must create fresh parsers, handler state, assembler state, generated objects, and marshaller. Different profiles must remain safe to process concurrently through the same converter.

Package a normal library JAR and a runtime dependency directory/distribution for webMethods, plus an executable dependency-inclusive `runner` classifier JAR for local use. Preserve JAXB service-provider resources in the runner. Document application/dependency placement in the Integration Server package and Designer compile classpath, checking package/server dependency conflicts before deployment. Do not package webMethods platform classes in our library. The Java service invokes an ordinary Java method inside the Integration Server JVM; it does not launch `java -jar`, invoke `main`, capture stdout, or start a subprocess.

### 2b. Primary webMethods integration contract

```text
webMethods FLOW supplies inputPath (String), optional profileId (String)
  -> thin Java service validates inputPath and any supplied profileId
  -> our compiled JAR: convertToXml(inputPath[, profileId])
       -> profile registry -> flat or multi-record handler
       -> shared payment validation/assembly -> generated pain.001 objects
       -> JAXB 4 serialization + bundled XSD validation
  -> Java service writes pain001Xml (String) to pipeline
  -> FLOW receives pain001Xml
```

`inputPath` refers to a file readable by the Integration Server operating-system user on the server's filesystem or a mounted share. A path on the caller's laptop is not automatically available to the server. Prefer an absolute path in FLOW; relative paths resolve against the server process working directory. No output file is required: the library returns XML directly.

The adapter validates inputPath and any supplied profileId, invokes the corresponding facade overload, and places `pain001Xml` into the pipeline only on success. Absent profileId retains the existing call; present blank, unknown, or non-string values fail without fallback. FLOW may set a profile from its configured client route. The adapter releases the pipeline cursor in a `finally` block and translates failures to `ServiceException`. Remove stale `pain001Xml` before validating either input, so an invalid profile cannot leave an earlier result. The library does not use stdout, process exit, or mutate pipeline data.

The documented adapter example must use the real platform API conventions, but live service installation and testing require access to the intended Integration Server. Standalone tests prove the same Java facade and XML behavior; they do not by themselves prove server deployment or classloading compatibility.

### 3. Build-time model generation and runtime conversion

```text
BUILD: bundled XSD -> XJC -> generated Java classes -> compiled JARs

RUN:   input path + profile -> registry -> structural handler
                                             |
                               shared validation/assembly
                                             |
                                  generated Document object
                                                   |
                                           JAXB serialization
                                                   |
                                      bundled-XSD validation
                                                   |
                                     XML String returned to caller
```

XJC runs during a build, not once per incoming file. Each conversion creates fresh model instances. Cache only reusable thread-safe infrastructure; do not share mutable documents, parsers, or JAXB marshallers across requests. A populated object is not evidence of schema validity; serialization explicitly enables XSD validation.

### 4. Explicit flat CSV contracts

Use Apache Commons CSV rather than splitting strings on commas. Default CSV is UTF-8, optionally BOM-prefixed, comma-delimited, with an obligatory header. Support quoted commas, escaped quotes, quoted newlines, LF and CRLF, and reordered columns by header lookup. Skip completely blank records. Reject duplicate, blank, missing required, and unknown headers; reject records with inconsistent field counts. Trim leading/trailing field whitespace while retaining embedded content. Do not infer date formats or delimiters.

One logical CSV record represents one credit-transfer instruction. Default columns and mapping:

| CSV column | Required in prototype | pain.001 destination or rule |
| --- | --- | --- |
| `initiating_party_name` | Yes | `GrpHdr/InitgPty/Nm`; one consistent value throughout file |
| `debtor_name` | Yes | `PmtInf/Dbtr/Nm` |
| `debtor_iban` | Yes | `PmtInf/DbtrAcct/Id/IBAN` |
| `debtor_bic` | Yes | `PmtInf/DbtrAgt/FinInstnId/BICFI` |
| `execution_date` | Yes | `PmtInf/ReqdExctnDt/Dt`, strict `YYYY-MM-DD` |
| `end_to_end_id` | Yes | `CdtTrfTxInf/PmtId/EndToEndId`; unique within file |
| `creditor_name` | Yes | `CdtTrfTxInf/Cdtr/Nm` |
| `creditor_iban` | Yes | `CdtTrfTxInf/CdtrAcct/Id/IBAN` |
| `creditor_bic` | No | `CdtTrfTxInf/CdtrAgt/FinInstnId/BICFI` when present |
| `amount` | Yes | `CdtTrfTxInf/Amt/InstdAmt`, positive decimal using `BigDecimal` |
| `currency` | Yes | `InstdAmt/@Ccy`, exactly three uppercase letters |
| `remittance` | No | One `CdtTrfTxInf/RmtInf/Ustrd` value when nonblank |

Require headers for required columns; optional columns may be absent or blank. Required values must be nonblank. Decimal syntax uses a dot and no grouping separators or exponent notation. Preserve decimal precision, reject rather than round values exceeding schema limits, and do not impose a universal two-decimal-place rule. Follow the bundled XSD for field lengths and lexical restrictions. Input IBANs/BICs are uppercase; reject invalid syntax. This prototype does not certify account existence, IBAN checksum validity, currency-specific minor units, or bank acceptance. Validate actual calendar dates without rejecting historical dates in mock fixtures.

The IBAN-only choice is a deliberately limited prototype input profile; pain.001 itself supports more account identification options. Local account identifiers and richer party/address information require a later profile.

Include a second explicit `camel-case` profile with the same semantics: `initiatingPartyName`, `debtorName`, `debtorIban`, `debtorBic`, `executionDate`, `endToEndId`, `creditorName`, `creditorIban`, `creditorBic`, `amount`, `currency`, and `remittance`. This demonstrates different layouts without guessing meanings or introducing a configuration language. Both profiles use ISO dates and dot-decimal amounts.

### 4a. Mock multi-record CSV contract

`mock-multirecord-v1` implements the six synthetic files in `samples/csv/multirecord/`. These tags and rules are defined for this mock contract; they are not universal CSV or bank record codes. Use Commons CSV without header inference. Support the same UTF-8/BOM, LF/CRLF, quoting, multiline content, trimming, and blank-record handling as flat input. Enforce each record's own width; do not pad rows or require a shared width.

| Record | Ordered fields including tag | Width |
| --- | --- | ---: |
| PH | `PH,batch_id,initiating_party_name,debtor_name,debtor_iban,debtor_bic,execution_date,transaction_count` | 8 |
| TX | `TX,batch_id,end_to_end_id,creditor_name,creditor_iban,creditor_bic,amount,currency,remittance` | 9 |
| IN | `IN,end_to_end_id,invoice_id,invoice_date,invoice_amount,currency` | 6 |
| FX | `FX,end_to_end_id,source_currency,target_currency,source_amount,exchange_rate,contract_id` | 7 |

Every field is mandatory except TX creditor_bic and remittance, whose positions remain even when empty. Text, identifiers, amounts, rates, and dates must fit their generated-model/XSD destinations; never truncate or round to fit. PH transaction_count must be a positive base-10 integer. End-to-end IDs are unique across the file, batch IDs across PH records, and invoice IDs within one TX. A batch must contain at least one TX; a file must contain at least one batch/payment. Consistent initiating party and debtor identity remain common file-wide rules.

State and closing rules:

- A PH opens a batch and closes the preceding transaction/batch. A TX requires an open PH and a batch_id matching that PH; it closes the previous transaction and opens the next.
- IN and FX require a current TX and its exact end_to_end_id. They may interleave within that TX's detail records; no detail may attach backward across a later TX/PH. Permit multiple IN records and at most one FX per TX.
- Validate invoice/FX reconciliation when a transaction closes and declared count when a batch closes, including end of file. Count only TX, before any output grouping by currency.
- Reject unknown tags, incorrect widths, duplicate identifiers/details, empty batches, out-of-order/orphan/mismatched records, bad values, and count/reconciliation failures. One error rejects the file.

Explicit field mapping:

| Source | Generated-model destination / rule |
| --- | --- |
| PH initiating party / debtor / execution date | Same `GrpHdr` / `PmtInf` destinations as the flat contract; inherited by each TX |
| PH batch_id | Grouping identity only; preserves source batch separation. Output `PmtInfId` remains generated, not a verbatim source batch ID. |
| PH transaction_count | Validation input; output counts are computed from TX records |
| TX fields | Same transaction destinations as flat rows; amount and currency describe the instructed creditor amount |
| IN invoice_id / invoice_date | One `RmtInf/Strd` per IN, with `RfrdDocInf/Nb` and `RltdDt` |
| IN invoice_amount / currency | That IN's `Strd/RfrdDocAmt/RmtdAmt` value and currency, retaining individual invoice amounts and source order |
| FX source_currency | `CdtTrfTxInf/XchgRateInf/UnitCcy` |
| FX exchange_rate | `XchgRateInf/XchgRate`; target-currency units per one source-currency unit |
| FX contract_id | `XchgRateInf/CtrctId`; do not invent `RateTp` |
| FX target_currency | Must match TX currency, already represented by `Amt/InstdAmt/@Ccy` |
| FX source_amount | Explicit reconciliation-only input; exact value is recoverable from instructed amount divided by the nonzero exchange rate. It is not emitted as a second instructed amount. |

Retain a nonblank TX remittance as `RmtInf/Ustrd` even when structured invoices exist; the bundled XSD permits both. Each IN represents a fully remitted invoice amount in this mock profile. When invoices exist, require their currencies to equal TX currency and their exact sum to equal TX amount; do not impose this rule on future profiles supporting partial invoices or adjustments.

For FX, require different source and target currencies, positive source amount/rate, and exact `source_amount * exchange_rate == TX amount` with BigDecimal. The mock profile specifies no rounding and makes no trade or live-rate request. For example, GBP 100.00 at 1.25 USD/GBP reconciles with an instructed USD 125.00. Preserve the rate direction in documentation/tests. These are explicit prototype mapping choices, not a recipient bank's FX processing rules. Do not ignore unknown or unmapped source semantics; a later client contract requires a documented mapping or explicit rejection.

### 5. Message construction and grouping

Produce one `Document/CstmrCdtTrfInitn` per file. Preserve transaction input order inside each payment group and group order by first occurrence. Flat profiles retain grouping by `(debtor_name, debtor_iban, debtor_bic, execution_date, currency)`. The mock multi-record profile additionally includes PH batch_id: separate source batches never merge even with otherwise identical keys. A PH containing multiple TX currencies splits into currency-specific groups, and its declared count is checked across all of them. Consistency checks reject contradictory debtor identity for the same account within the file. Multiple initiating parties are rejected rather than split across messages.

Set `PmtMtd` to `TRF` as the documented prototype payment method. Generate schema-length-compatible message and payment-information identifiers once per invocation; do not silently invent missing debtor/creditor/payment values. Generate `CreDtTm` from an injectable clock, using UTC. Do not set SEPA, service-level, charge-bearer, or bank-specific flags by assumption.

Calculate `NbOfTxs` from actual instructions at message and payment-group levels; PH/IN/FX never add transactions. Calculate each payment group's `CtrlSum` from instructed amounts with `BigDecimal`; never add invoice or FX source amounts again. Include the optional message-level `CtrlSum` only when the entire message uses one currency, avoiding an unexplained cross-currency sum. Document these grouping and total policies as application choices rather than universal ISO mandates.

Fresh message IDs do not provide duplicate-payment protection. Reprocessing a file is a fresh conversion; production retry/idempotency policies are deferred.

### 6. Runner and output contract

Commands, run from the Maven project directory (profile commands become available after extension implementation):

```sh
mvn clean verify
mvn generate-sources
mvn exec:java -Dexec.args="samples/csv/valid/basic.csv"
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/valid/basic.csv
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/valid/alternate-headers.csv --layout camel-case --output output/payment.xml
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/valid/alternate-headers.csv --profile flat-camel-case-v1
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/multirecord/04-fx-and-invoices.csv --profile mock-multirecord-v1
```

The first positional argument is an absolute path or a path relative to the process working directory. All initial profiles accept only `.csv`; validate extension compatibility after resolving a known profile. Add `--profile <profileId>` and retain legacy `--layout default|camel-case`. No selection means `flat-default-v1`. Reject repeated selectors and any combination of `--profile` with `--layout`, even when equivalent. Unknown or blank explicit IDs fail instead of defaulting. A known profile receiving the wrong CSV structure fails source validation. Without `--output`, write only fully validated UTF-8 XML to stdout. Send diagnostics to stderr. No stdin, network input, or automatic output path is implied.

With `--output`, require an existing parent directory and refuse to overwrite an existing destination. Validate the entire conversion before creating the destination; use create-new semantics and remove an incomplete new output if writing fails. Do not alter the input file. If input and output resolve to the same file, fail. This is all-or-nothing conversion: one invalid record prevents success and no valid subset is exported.

Exit statuses: `0` success; `2` usage, profile/layout, or unsupported-format error; `3` input path/read failure; `4` CSV/payment/schema validation failure; `5` output/write or unexpected processing failure. The library reports exceptions rather than CLI exit statuses. Source errors identify logical record, record type where known, and field without echoing full payment/account data. For malformed multiline CSV, include the parser's available location without promising an exact source field. Final schema errors identify the XML path or schema diagnostic and source record where traceable.

Load the schema from classpath resources in both Maven and packaged JAR execution; runtime schema validation must not require a network connection. Disable external DTD/schema access and resolve any necessary bundled imports explicitly.

### 7. Mock file variants and evidence

| Planned file | Demonstration / expected result |
| --- | --- |
| `valid/basic.csv` | Two payments sharing debtor/date/currency; one payment group |
| `valid/multiple-groups.csv` | Different debtors or execution dates; correct separation and totals |
| `valid/mixed-currencies.csv` | Separate currency groups, no message-level control sum |
| `valid/quoted-fields.csv` | Commas, embedded quotes, ampersands, Unicode, and quoted multiline remittance |
| `valid/reordered-columns.csv` | Same default field names in a different order |
| `valid/alternate-headers.csv` | Equivalent payments through explicit `camel-case` profile |
| `valid/optional-fields-omitted.csv` | Missing optional headers/blank optional values |
| `valid/bom-crlf.csv` | UTF-8 BOM and CRLF input |
| `invalid/missing-header.csv` | Required header absent |
| `invalid/missing-required-value.csv` | Blank required payment value |
| `invalid/invalid-amount.csv` | Nonnumeric, zero, or negative amount |
| `invalid/invalid-date.csv` | Invalid calendar date |
| `invalid/duplicate-reference.csv` | Duplicate end-to-end identifier |
| `invalid/inconsistent-parties.csv` | Conflicting initiating parties or debtor identity |
| `invalid/malformed.csv` | Unclosed quote or inconsistent record width |
| `invalid/schema-constraint.csv` | Value violating an actual XSD limit |
| `invalid/header-only.csv` | No payments |

All examples use clearly synthetic parties, references, and account identifiers with appropriate schema syntax; they are not instructions to execute real payments. `samples/csv/README.md` records the required profile, expected group/count/amount results, and failure reason for each fixture. These variants demonstrate supported contracts, not arbitrary client-format support.

Use focused tests for clean code generation; generated-model field values; grouping; exact decimal totals; namespace and XML escaping; validation against the bundled official XSD; negative cases; and execution of the packaged JAR from outside the project directory. XML comparisons ignore generated IDs, creation time, prefixes, and formatting while asserting meaningful structure and values. A separate XSD validation check must confirm output validity in addition to exercising the writer's own validation path.

Multi-record acceptance fixtures, all using `mock-multirecord-v1`:

| Existing file under `samples/csv/multirecord/` | TX / PH / output groups | Invoice / FX entries | Instructed totals |
| --- | --- | --- | --- |
| `01-single-payment.csv` | 1 / 1 / 1 | 0 / 0 | GBP 125.50 |
| `02-invoices.csv` | 2 / 1 / 1 | 3 / 0 | GBP 450.00; retain each invoice amount |
| `03-multiple-batches.csv` | 3 / 2 / 2 | 0 / 0 | GBP 300.00; EUR 75.25; omit message sum |
| `04-fx-and-invoices.csv` | 2 / 1 / 2 | 2 / 2 | USD 125.00; EUR 90.00; omit message sum |
| `05-quoted-unicode.csv` | 1 / 1 / 1 | 1 / 0 | GBP 10.25; preserve punctuation, Unicode and newline |
| `06-bom-crlf.csv` | 1 / 1 / 1 | 1 / 1 | USD 62.50; real BOM/CRLF bytes |

Add focused cases for two PH batches with identical common grouping values, mixed currencies within a PH, and every record ordering/ownership/count/invoice/FX failure rule. Assert exact mapping paths and values rather than only XSD validity, both through the library and packaged runner. Retain every existing flat regression case, legacy layout call, path-only call, wrong-profile rejection, source immutability, and all-or-nothing output protection. Exercise concurrent flat and multi-record calls on one converter instance.

Before implementation, the existing sample checker documents unsupported conversion under flat profiles. After implementation, update it and fixture guides to expect successful explicit multi-record conversion and field preservation while retaining wrong-profile rejection checks. Historical rejection evidence must not be presented as proof that support was implemented. Record the new `mvn clean verify` result only after running it against the implementation.

## Risks / Trade-offs

- [Schema-specific handlers] -> A schema change can change generated types and mapping code; pin the version and keep generated types in a versioned package.
- [Duplicated payment rules] -> Extract shared validation and assembly before adding the second handler; preserve the existing flat regression suite.
- [Ambiguous client meaning] -> Version input profiles explicitly, define rate direction and invoice rules, and reject unsupported semantics instead of guessing from codes or extension.
- [Accidental batch merging or amount double counting] -> Preserve PH batch identity in grouping; only TX instructed amounts contribute to totals.
- [Server dependencies and configured JVM] -> Target Java 21/JAXB 4 as requested, document package dependency loading, and verify the actual server JVM and JAXB classloading before deployment. No live server verification has been performed during planning.
- [Source syntax versus payment readiness] -> State the limited profile and validation scope; do not claim bank submission readiness.
- [Memory usage] -> Parse with a real CSV parser, but acknowledge the object tree and stdout XML buffering are in memory; large-file processing is a later requirement.
- [Dependency network access during first build] -> Bundle the XSD and pin dependencies. Maven still needs dependency downloads on a fresh machine; no-network runtime is the requirement.
- [Official download access] -> Direct scripted requests returned HTTP 403 during planning. Resolved during implementation through the official catalogue's browser XSD download control; the unmodified official file and its provenance/checksum are now bundled under resources.

## Migration Plan

Preserve the implemented baseline and completed task checkboxes. First extract shared payment rules under flat regression coverage, then add the registry and compatibility-preserving profile overloads, then implement the mock multi-record handler and mappings. Extend runner and adapter selection and replace the samples' rejection-only checker with selected-profile acceptance tests. Keep new extension tasks unchecked until implemented and verified. Supply revised integration guidance without changing the existing proof of concept or deploying to the live server.

## Open Questions

No clarification is required for this specified extension. Java 21/JAXB 4, direct generated-model population, optional explicit profile selection, and the mock record contract are settled for implementation. Before onboarding a real client, obtain its layout/version, record meanings, date/decimal rules, grouping requirements, and invoice/FX semantics; the mock contract cannot stand in for them. Before live integration, confirm the server JVM/classloading environment and accessible input locations. Recipient usage rules, file volumes, and duplicate-submission policy remain production considerations.

## References

- [Official ISO 20022 message archive](https://www.iso20022.org/catalogue-messages/iso-20022-messages-archive?search=pain.001): source of the selected message's XSD.
- [ISO 20022 FAQ](https://www.iso20022.org/faq): versioned messages and implementation-specific requirements.
- [JAXB 4 documentation](https://eclipse-ee4j.github.io/jaxb-ri/4.0.5/docs/release-documentation.html): schema-to-Java generation, Java requirements, and XML binding.
- [IBM Java service guidance](https://www.ibm.com/docs/en/webmethods-integration/wm-integration-server/12.1.0?topic=guide-building-java-services): Java service structure and dependency setup.
- [IBM class searching order](https://www.ibm.com/docs/en/webmethods-integration/wm-integration-server/11.1.0?topic=classpath-class-searching-order): package/server dependency precedence.
- [MojoHaus XJC goal](https://www.mojohaus.org/jaxb2-maven-plugin/xjc-mojo.html): Maven generation phase and output configuration; consult the chosen release's requirements before pinning it.
- [Apache Commons CSV documentation](https://commons.apache.org/proper/commons-csv/apidocs/index.html): headers, quoting, and BOM handling.
