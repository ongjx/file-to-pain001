## ADDED Requirements

### Requirement: Input contracts are selected by explicit versioned profile
The converter SHALL resolve input through a registry of known versioned profiles. Each profile SHALL identify its handler, accepted file extensions, field mapping, value conventions, and grouping policy. The initial registry SHALL contain `flat-default-v1`, `flat-camel-case-v1`, and `mock-multirecord-v1`. Existing `default` and `camel-case` layout names SHALL remain compatibility aliases for the corresponding flat profiles. Omitting profile selection SHALL retain `flat-default-v1` behavior. Explicit unknown, null, or blank profile IDs SHALL fail as usage errors without falling back or guessing from content or extension.

#### Scenario: Existing caller supplies only a path
- **WHEN** a caller invokes the existing path-only conversion method
- **THEN** the default flat profile is selected and existing payment values, grouping, and failure behavior are retained

#### Scenario: Multi-record contract is explicitly selected
- **WHEN** a caller selects `mock-multirecord-v1` for a valid PH/TX/IN/FX file
- **THEN** the registry selects the multi-record handler and conversion produces validated pain.001 XML

#### Scenario: Profile is unknown or incompatible
- **WHEN** an explicit profile ID is unknown or blank, or the path extension is unsupported by the selected profile
- **THEN** conversion fails with a usage/profile diagnostic rather than selecting a different handler

#### Scenario: A known profile receives a different contract
- **WHEN** a headerless multi-record file is submitted using a flat profile, or a flat file using the multi-record profile
- **THEN** source validation fails for that selected contract without automatic retries using other profiles

### Requirement: Structural handlers and field mappings have separate responsibilities
The implementation SHALL retain a small `PaymentFileHandler` interface that reads a path and returns the schema-generated pain.001 `Document`. The flat handler SHALL be reused for the two flat profiles; the multi-record CSV handler SHALL validate source record tags, widths and required columns before passing records to a conversion-layer assembler for record-order and parent-child interpretation. Profiles SHALL hold explicit field names or positions and strict value conventions. Multi-record generated-model mapping, batch state and invoice/FX reconciliation SHALL reside in the conversion layer, separate from CSV parsing, without requiring a generic mapper interface. Adding a profile with the same structure SHALL reuse its handler instead of copying its implementation. The initial registry SHALL be defined in Java without a runtime plugin loader or user-authored mapping language.

#### Scenario: Equivalent flat profiles are processed
- **WHEN** snake-case and camel-case inputs describe the same payments
- **THEN** they use the same structural handler and produce equivalent payment values through their explicit mappings

#### Scenario: Multi-record parsing delegates payment assembly
- **WHEN** the multi-record handler reads a structurally valid PH, TX, IN or FX record
- **THEN** its conversion-layer assembler applies ownership, mapping and reconciliation rules, preserving generated-model values, validation ordering and source diagnostics

### Requirement: Payment rules and document assembly are shared across handlers
Common payment-value checks, duplicate references, party consistency, identifiers, clocks, grouping, actual counts, and exact totals SHALL have one shared implementation used by both handlers. Source syntax SHALL remain with the relevant handler; profile-specific ownership and reconciliation SHALL reside in its per-conversion assembler. Mapping and shared assembly SHALL populate the existing generated model directly without introducing an intermediate canonical payment model or JSON representation. The existing XML writer SHALL remain responsible for serialization and XSD validation. Mutable documents, parsing state, and marshallers SHALL remain local to each conversion.

#### Scenario: Invalid payment is supplied through either handler
- **WHEN** a flat or multi-record input contains the same invalid common payment value
- **THEN** both handlers enforce the shared rule and report source record/field context without exposing full payment values

#### Scenario: Different profiles run concurrently
- **WHEN** one converter instance processes supported profiles concurrently
- **THEN** profile selection and mutable payment state do not leak between conversions
