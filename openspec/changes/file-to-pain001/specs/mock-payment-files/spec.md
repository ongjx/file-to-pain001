## ADDED Requirements

### Requirement: Synthetic valid input variants
The project SHALL retain documented synthetic flat CSV examples for basic payments, multiple groups, mixed currencies, quoted/Unicode/multiline fields, reordered headers, explicit alternate headers, omitted optional fields, and BOM/CRLF input. It SHALL use the six existing `samples/csv/multirecord/01-*.csv` through `06-*.csv` fixtures as acceptance inputs for `mock-multirecord-v1`, with expected payment counts, source batches, payment groups, invoice/FX details, and per-currency totals defined in `design.md`.

#### Scenario: Valid fixture conversion
- **WHEN** each documented valid fixture is processed using its required profile
- **THEN** conversion succeeds, XML passes XSD validation, and payment values, counts, grouping, currencies, and totals match its documented expectations

### Requirement: Synthetic invalid input variants
The project SHALL include fixtures for missing headers, missing required values, invalid amounts and dates, duplicate references, conflicting parties, malformed CSV, actual schema-constraint violations, and files containing only a header. Multi-record cases SHALL additionally cover unknown types, per-type width, TX before PH, IN/FX before TX, mismatched batch/payment references, empty batches, PH count mismatch including at end of file, duplicate batch/payment/invoice/FX identifiers as applicable, invoice currency/sum mismatch, and FX currency/rate/arithmetic mismatch. Separate focused cases SHALL cover distinct failure conditions where combining them would hide a defect.

#### Scenario: Invalid fixture conversion
- **WHEN** each documented invalid fixture is processed
- **THEN** it fails for the documented reason, produces the expected diagnostic category, and emits no successful XML or partial output file

### Requirement: Fixture documentation defines the supported scope
The fixture guide SHALL identify each file as synthetic, describe its layout/profile and expected outcome, and state that schema-valid examples do not establish bank submission readiness or support for arbitrary client layouts.

#### Scenario: A developer selects an example
- **WHEN** the developer reads `samples/csv/README.md`
- **THEN** they can identify the correct run command, required profile, expected result, and validation scope for each fixture

### Requirement: Profile acceptance tests prove mapping rather than only schema shape
The automated suite SHALL run the six multi-record samples through the profile-aware library and packaged runner, independently validate successful XML against the bundled XSD, and assert payment values, structured invoice IDs/dates/individual amounts, FX currencies/rates/contracts, group separation, counts, and exact sums. It SHALL retain existing flat regression tests, wrong-profile rejection tests, all-or-nothing output checks, and concurrency coverage across profiles. A sample or checker that merely confirms rejection by the old converter SHALL not count as implemented multi-record support.

#### Scenario: Multi-record implementation is delivered
- **WHEN** `mvn clean verify` runs after profile support is implemented
- **THEN** all six samples convert successfully with `mock-multirecord-v1`, retain their documented details, and remain rejected under inappropriate flat profiles

#### Scenario: Existing compatibility checker is updated
- **WHEN** multi-record support replaces the current unsupported behavior
- **THEN** fixture documentation and `check_samples.py` are updated to verify successful selected-profile conversion and field preservation while retaining wrong-profile rejection evidence
