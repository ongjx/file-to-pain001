## Why

Client payment files vary in structure and field meaning even when they share a `.csv` extension. The implemented Java library converts two flat CSV layouts to pain.001, and the profile extension now converts the six synthetic PH/TX/IN/FX multi-record samples with explicit selection. Extend it so a webMethods Java service can select a known versioned client input contract while retaining existing default behavior and shared payment rules.

## What Changes

- Add a separate `file-to-pain001/` Maven project targeting Java 21 with JAXB 4, with a reusable path-to-XML entry point and a command-line runner for local use.
- Define the primary integration as FLOW `inputPath` -> Java service -> direct in-process call to the compiled JAR -> FLOW `pain001Xml`, with documented adapter and dependency setup.
- Bundle an unmodified official pain.001 XSD under application resources and generate its Java model during Maven `generate-sources`.
- Parse supported CSV layouts and populate that generated model directly; serialize and validate XML without an intermediate JSON document or webMethods MAP step.
- Add a registry of explicit versioned input profiles selecting a reused flat CSV handler or a new multi-record CSV handler, with mappings and strict value conventions defined per contract.
- Extract shared payment validation and document assembly so handlers do not duplicate grouping, consistency checks, identifiers, counts, or exact totals.
- Implement `mock-multirecord-v1` with PH batch inheritance, TX payments, structured IN invoice remittance, FX exchange information, source ownership/count/reconciliation checks, and preserved batch grouping.
- Add optional profile selection to the library facade, runner, and webMethods pipeline while retaining the path-only facade and legacy flat layout options.
- Supply several documented synthetic CSV examples covering normal payments, grouping, CSV escaping, alternate headers, and rejection cases.
- Promote the six existing multi-record samples to successful selected-profile acceptance cases after implementation, with field-preservation, independent XSD, wrong-profile, and negative-case tests.
- Deliver a dedicated dependency-inclusive `-webmethods.jar` for single-JAR deployment, retaining the optional thin distribution and separate executable runner; test it in an isolated classloader and with the installed vendor API.
- Separate CSV structure checks from multi-record payment assembly in the conversion package, consolidate shared Shade configuration, and document the Maven/package layout while preserving existing behavior.
- Preserve the existing webMethods proof of concept and its sample files.

## Capabilities

### New Capabilities

- `schema-generated-payment-model`: Bundled schema provenance, repeatable XSD-to-Java generation, and schema-valid XML serialization.
- `payment-input-profiles`: Versioned input contracts, handler selection, mapping responsibilities, shared payment rules, and compatibility defaults.
- `csv-payment-conversion`: Flat and multi-record CSV contracts, source validation, batch-aware payment grouping, invoice/FX mappings, and generated-model population.
- `payment-file-runner`: Maven build, compatible path-only and profile-aware Java entry points, profile/layout options, diagnostics, and safe output behavior.
- `webmethods-java-service-integration`: String input path/optional profile/string XML interface, server-side file access, service error handling, and dependency deployment guidance.
- `mock-payment-files`: Flat regressions and multi-record acceptance/rejection fixtures with explicit mapping expectations.

### Modified Capabilities

No capabilities have been archived into a separate `openspec/specs/` baseline. This update revises the existing unarchived change and its implemented Maven project; completed baseline and extension tasks are checked after verification.

## Impact

The implementation remains within `file-to-pain001/`, reusing JAXB 4, Commons CSV, JUnit, and the bundled schema. Changes affect the converter, shared assembly, input profiles/handlers, runner, adapter example, fixture checker, tests, and guides. No additional mapping framework or canonical payment model is required. Preserve existing flat contracts and layout aliases. Real client/bank formats, bank submission rules, Excel, extra pain.001 versions, FX execution, and live Integration Server deployment remain outside scope. Profile/multi-record support is implemented and its verification evidence is recorded in `docs/verification.md`.

Java 21 with JAXB 4 and direct Java-service invocation are user-selected requirements. The schema edition `pain.001.001.09` and all-or-nothing conversion of one file into one XML document remain planning defaults. Java 17 with JAXB 4 is a fallback only if the actual deployment requires it; it is not a second mandatory build target. See `design.md` for the detailed contract.
