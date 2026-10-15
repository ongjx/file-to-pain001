# Synthetic CSV examples

All files here are mock data for conversion tests. Party names and payment references are fictitious. `GB00MOCK...` and `DE00MOCK...` identifiers are deliberately synthetic: they satisfy the prototype's lexical/XSD profile, but are not verified IBANs or real payment destinations. Do not submit these files as payments.

Six additional [multi-record examples](multirecord/README.md) use a documented mock PH/TX/IN/FX layout. They are separate compatibility probes: the current converter rejects that layout and produces no XML.

## Columns (supported flat layouts)

One logical record represents one credit transfer. UTF-8, optional BOM, comma delimiter, and a header are required. Quoted commas, escaped quotes, quoted line breaks, LF/CRLF, reordered columns, and blank records are supported. Values are trimmed at their ends. Unknown, duplicate, and blank headers are rejected.

| Default header | Alternate `camel-case` header | Required | Meaning |
| --- | --- | --- | --- |
| `initiating_party_name` | `initiatingPartyName` | Yes | Party initiating the entire message; consistent across file |
| `debtor_name` | `debtorName` | Yes | Debtor party name |
| `debtor_iban` | `debtorIban` | Yes | Uppercase IBAN syntax for debtor account |
| `debtor_bic` | `debtorBic` | Yes | Debtor bank BIC, 8 or 11 uppercase characters |
| `execution_date` | `executionDate` | Yes | Real calendar date in `YYYY-MM-DD` format |
| `end_to_end_id` | `endToEndId` | Yes | Payment reference, maximum 35 characters, unique within file |
| `creditor_name` | `creditorName` | Yes | Creditor party name |
| `creditor_iban` | `creditorIban` | Yes | Uppercase IBAN syntax for creditor account |
| `creditor_bic` | `creditorBic` | No | Creditor bank BIC |
| `amount` | `amount` | Yes | Positive dot-decimal value, up to 18 total and 5 fractional significant decimal digits under the XSD |
| `currency` | `currency` | Yes | Three uppercase letters |
| `remittance` | `remittance` | No | Unstructured remittance, maximum 140 characters |

Required columns must have a header and nonblank values. Optional headers can be omitted, or their values left blank. Party names are limited to 140 characters. Exponent notation and grouping separators in amounts are rejected; no value is rounded. Historical mock execution dates are accepted. IBAN checksums, account existence, currency minor units, and bank-specific usage rules are not checked.

## Run commands

From the Maven project directory, after `mvn clean verify`:

```sh
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/valid/basic.csv
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/valid/alternate-headers.csv --layout camel-case
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/invalid/invalid-amount.csv
```

For an output file, append `--output target/new-payment.xml`; it must not already exist. The same default-profile files work with the Java API's path-string method. Alternate headers require explicit layout selection through the lower-level API or runner.

## Valid fixtures

Every successful result must pass the bundled official XSD. All groups have method `TRF`. Single-currency message sums equal their group sums; mixed-currency messages omit message-level `CtrlSum`. Group order follows first occurrence, and source order is retained inside each group.

| File | Profile | Expected payments / groups | Expected sums and behavior |
| --- | --- | --- | --- |
| [valid/basic.csv](valid/basic.csv) | default | 2 / 1 | GBP 0.30; message sum 0.30; MOCK-001 then MOCK-002 |
| [valid/multiple-groups.csv](valid/multiple-groups.csv) | default | 4 / 3 | Group 1 GBP 112.34 (MOCK-001, MOCK-002), group 2 EUR 25.00, group 3 GBP 10.00; no message sum |
| [valid/mixed-currencies.csv](valid/mixed-currencies.csv) | default | 3 / 2 | Group 1 GBP 0.30, group 2 USD 0.75; no message sum |
| [valid/quoted-fields.csv](valid/quoted-fields.csv) | default | 1 / 1 | GBP 0.10; preserve supplier punctuation/Unicode and multiline remittance |
| [valid/reordered-columns.csv](valid/reordered-columns.csv) | default | 2 / 1 | Same payments and GBP 0.30 as basic.csv |
| [valid/alternate-headers.csv](valid/alternate-headers.csv) | camel-case | 2 / 1 | Same payments and GBP 0.30 as basic.csv; default profile must reject it |
| [valid/optional-fields-omitted.csv](valid/optional-fields-omitted.csv) | default | 1 / 1 | GBP 0.10; creditor agent and remittance elements absent |
| [valid/optional-fields-blank.csv](valid/optional-fields-blank.csv) | default | 1 / 1 | GBP 0.10; blank optional values produce no empty optional elements |
| [valid/bom-crlf.csv](valid/bom-crlf.csv) | default | 2 / 1 | Same payments and GBP 0.30 as basic.csv; actual BOM/CRLF bytes |
| [valid/five-decimal-amount.csv](valid/five-decimal-amount.csv) | default | 1 / 1 | GBP 0.12345, preserved exactly; no universal two-place rounding |
| [valid/blank-lines.csv](valid/blank-lines.csv) | default | 2 / 1 | GBP 0.30; a completely blank record does not create a payment |

## Invalid fixtures

All use the default profile and must exit with status `4`, with a useful diagnostic on stderr, no XML on stdout, and no new output file. A single invalid record rejects the whole file; no valid subset is exported.

| File | Expected rejection |
| --- | --- |
| [invalid/missing-header.csv](invalid/missing-header.csv) | Missing currency header |
| [invalid/missing-required-value.csv](invalid/missing-required-value.csv) | Blank debtor_name |
| [invalid/invalid-amount.csv](invalid/invalid-amount.csv) | Nonnumeric amount |
| [invalid/zero-amount.csv](invalid/zero-amount.csv) | Amount is not positive |
| [invalid/negative-amount.csv](invalid/negative-amount.csv) | Negative amount |
| [invalid/exponent-amount.csv](invalid/exponent-amount.csv) | Exponent notation is unsupported |
| [invalid/excess-fraction.csv](invalid/excess-fraction.csv) | Amount exceeds XSD fractionDigits 5 |
| [invalid/excess-total-digits.csv](invalid/excess-total-digits.csv) | Amount exceeds XSD totalDigits 18 |
| [invalid/invalid-date.csv](invalid/invalid-date.csv) | 2026-02-30 is not a real calendar date |
| [invalid/ambiguous-date.csv](invalid/ambiguous-date.csv) | Date is not YYYY-MM-DD |
| [invalid/duplicate-reference.csv](invalid/duplicate-reference.csv) | Duplicate end_to_end_id |
| [invalid/inconsistent-parties.csv](invalid/inconsistent-parties.csv) | Different initiating parties in one file |
| [invalid/inconsistent-debtor.csv](invalid/inconsistent-debtor.csv) | Same debtor account has conflicting debtor identity |
| [invalid/malformed.csv](invalid/malformed.csv) | Unclosed quoted field; parser reports available location |
| [invalid/record-width.csv](invalid/record-width.csv) | Record width differs from header |
| [invalid/schema-constraint.csv](invalid/schema-constraint.csv) | Creditor name exceeds XSD maximum length 140 |
| [invalid/schema-aggregate-sum.csv](invalid/schema-aggregate-sum.csv) | Individually valid amounts yield a group/message sum exceeding the XSD limit; final schema validation rejects it |
| [invalid/header-only.csv](invalid/header-only.csv) | File contains no payments |
| [invalid/duplicate-header.csv](invalid/duplicate-header.csv) | Duplicate currency header |
| [invalid/blank-header.csv](invalid/blank-header.csv) | Blank header name |
| [invalid/unknown-header.csv](invalid/unknown-header.csv) | Unknown header for selected profile |
| [invalid/invalid-iban.csv](invalid/invalid-iban.csv) | Invalid uppercase IBAN syntax |
| [invalid/invalid-bic.csv](invalid/invalid-bic.csv) | Invalid BIC syntax |
| [invalid/invalid-currency.csv](invalid/invalid-currency.csv) | Currency is not three uppercase letters |
| [invalid/invalid-xml-character.csv](invalid/invalid-xml-character.csv) | Remittance contains a control character forbidden in XML 1.0 |

The fixtures illustrate explicitly supported layouts, not arbitrary client files. Schema validity establishes the chosen message structure; it does not establish bank acceptance. Each invocation generates fresh message and payment-group IDs and a current UTC creation timestamp. These values are excluded from payment-equivalence comparisons.
