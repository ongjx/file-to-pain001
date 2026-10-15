## 1. Maven project and schema

- [x] 1.1 Locate or provision a Java 21 build JDK, create the isolated `file-to-pain001/` project with compiler release `21`, and pin mutually compatible JAXB 4 API/runtime/XJC dependencies and Maven plugins.
- [x] 1.2 Download the official pain.001.001.09 XSD and dependencies into resources; inspect mandatory elements and record source, date, notices, namespace, and checksums.
- [x] 1.3 Configure build-time XJC generation into the versioned package and confirm a clean build compiles and packages the generated model without handwritten substitutes.

## 2. CSV conversion core

- [x] 2.1 Define the primary `convertToXml(String inputPath)` instance facade returning validated XML, plus minimal internal/model APIs without webMethods or CLI dependencies.
- [x] 2.2 Implement the default and camel-case CSV profiles using Commons CSV, covering UTF-8/BOM, headers, quoting, optional fields, and record diagnostics.
- [x] 2.3 Implement required value validation, strict dates, exact positive decimal amounts, syntax checks, duplicate references, and party consistency checks.
- [x] 2.4 Map records directly into the generated model with documented grouping, counts, exact totals, payment method, identifiers, and injectable creation clock.

## 3. XML and runner

- [x] 3.1 Implement JAXB serialization with local classpath XSD validation, namespace correctness, XML escaping, and external resource access disabled.
- [x] 3.2 Implement the positional input path, layout selection, optional output path, stderr diagnostics, and documented exit statuses.
- [x] 3.3 Implement validation-before-output, create-new destinations, same-file protection, and incomplete-file cleanup.
- [x] 3.4 Package the library with an explicit runtime dependency distribution for webMethods and a separate executable local runner JAR, preserving JAXB service-provider metadata, and configure Maven execution.

## 4. Mock payment files

- [x] 4.1 Create synthetic valid fixtures for each design scenario with expected payment values, counts, grouping, currencies, and totals.
- [x] 4.2 Create focused invalid fixtures for each rejection rule, including actual bundled-XSD constraints and malformed multiline CSV.
- [x] 4.3 Document every fixture's profile, command, result, and the prototype's supported input and validation scope.

## 5. Verification and handoff

- [x] 5.1 Add focused tests for generated-model population, source validation, grouping, counts, exact totals, and schema-valid serialization.
- [x] 5.2 Test all fixtures and independently validate successful XML against the bundled XSD; assert field preservation as well as schema validity.
- [x] 5.3 Exercise the executable JAR outside the project directory, including local schema loading, stdout cleanliness, exit statuses, existing-output protection, and failure cleanup.
- [x] 5.4 Run `mvn clean verify` on Java 21, smoke-test the path-string-to-XML facade and documented Maven/JAR commands, and record actual tested versions and remaining server checks.

## 6. webMethods integration handoff

- [x] 6.1 Provide a thin Java service example using `inputPath` and `pain001Xml`, direct facade invocation, stale-output removal, cursor cleanup, and `ServiceException` translation without modifying the existing proof of concept.
- [x] 6.2 Document the FLOW invocation, server-accessible paths, Java 21 JVM check, package/Designer dependency setup, and JAXB classloading considerations.
- [x] 6.3 Review the adapter against platform API conventions and clearly distinguish tested library behavior from live Integration Server checks requiring deployment access.

## 7. Shared payment rules and input profiles (implemented extension)

Sections 1–6 describe the implemented flat-file baseline. Sections 7–10 are the implemented profile/multi-record extension. All six core samples now convert with their selected profile. Verification: 205 tests passed on Java 21, including the installed vendor-API adapter test; the updated sample checker passed. See `docs/verification.md` for evidence and the live-server limit.

- [x] 7.1 Extract shared payment-value validation and `PaymentDocumentAssembler` from the flat handler, retaining generated-model population, flat grouping, exact totals, common file checks, and source diagnostics under existing regression tests.
- [x] 7.2 Add immutable versioned profile definitions and a registry for `flat-default-v1`, `flat-camel-case-v1`, and `mock-multirecord-v1`; keep shared structural handlers and explicit field/value conventions without a mapping language or plugin loader.
- [x] 7.3 Add profile-aware string-to-XML and path-to-model overloads; preserve existing overloads and default/layout aliases; reject unknown/null/blank explicit profiles and incompatible extensions without inference or fallback.

## 8. Mock multi-record CSV handler and mappings

- [x] 8.1 Implement Commons CSV parsing without header inference and enforce PH/TX/IN/FX widths, required values, blank-record handling, UTF-8/BOM, LF/CRLF, quoting, Unicode, and multiline content.
- [x] 8.2 Implement PH/TX state, source ownership, unique batches and file-wide payment references, invoice uniqueness per TX, at most one FX per TX, positive declared counts, and transaction/batch closing checks including end of file.
- [x] 8.3 Map PH/TX into generated objects via shared assembly, preserve separate source batches in grouping, split mixed-currency batches, and count/sum only TX instructed amounts.
- [x] 8.4 Map each IN to its own structured remittance entry retaining invoice ID/date/amount/currency and TX unstructured remittance; validate exact mock-profile invoice reconciliation.
- [x] 8.5 Map FX unit currency/rate/contract with explicit target-per-source rate direction, retain instructed target amount, validate source amount/currency/exact arithmetic, and document reconciliation-only fields without inferred rate type or FX dealing.

## 9. Runner and webMethods profile selection

- [x] 9.1 Add `--profile`, retain legacy `--layout`, reject duplicate/mixed selectors, update usage and status diagnostics, and preserve all output/path protections.
- [x] 9.2 Extend the adapter example with optional string `profileId`, validate present invalid values without fallback, and clear stale XML before profile validation while retaining cursor/exception handling.
- [x] 9.3 Update library/runner/profile onboarding and webMethods guides with exact profile IDs, mappings, commands, pipeline behavior, and limited mock-versus-client scope.

## 10. Multi-record acceptance and regression verification

- [x] 10.1 Test all six existing multi-record samples through library and packaged runner using `mock-multirecord-v1`; independently validate XML and assert individual invoice/FX fields, instructed amounts, counts, currency groups, and exact totals.
- [x] 10.2 Add focused valid cases for matching PH grouping values across distinct batches and mixed currencies inside one batch; prove no merging or detail double counting.
- [x] 10.3 Add focused rejection fixtures for ordering/ownership, unknown tags, widths, required values, empty batches/files, count mismatches including EOF, duplicate identifiers/details, invoice currency/sum, and FX currency/rate/arithmetic errors.
- [x] 10.4 Test profile defaults/aliases, unknown or blank IDs, wrong-profile rejection, profile/layout conflicts, common invalid values through both handlers, and concurrent mixed-profile use of one converter.
- [x] 10.5 Update `check_samples.py` and fixture guides from old rejection-only evidence to selected-profile success and mapping checks while retaining wrong-profile rejection, input immutability, and no partial output assertions.
- [x] 10.6 Run `mvn clean verify` and the updated sample checker on Java 21, retain all flat regression coverage, and record actual extension results separately from historical baseline evidence and pending live server checks.

## 11. Single-JAR webMethods deployment

- [x] 11.1 Add a dedicated `webmethods` classifier fat library containing the application, generated model, schema and all runtime dependencies, preserving service providers without a CLI Main-Class or vendor platform classes.
- [x] 11.2 Retain the optional thin distribution and separate runner, and update the specs and guides to prefer only the webMethods fat JAR with migration/classloader guidance.
- [x] 11.3 Verify every valid fixture through the isolated single-JAR interface, compile/invoke the adapter with only the fat JAR and vendor API, and run a clean Java 21 build with all 228 tests passing.

## 12. Host XML-provider compatibility

- [x] 12.1 Reproduce schema initialization failure with the installed server Xerces and add portable/installed-provider regressions that fail against the old artifact.
- [x] 12.2 Select Java's built-in schema factory directly, retaining secure processing, external-resource restrictions and XSD validation without changing host-wide provider settings.
- [x] 12.3 Pass a clean 230-test run and the original server-JVM reproduction; rebuild and update the local package fat JAR with a backup, documenting the required package reload and unverified live FLOW retry.


## 13. Behavior-preserving simplification and structure cleanup

- [x] 13.1 Extract PH/TX/IN/FX state, ownership, generated-model detail mapping and reconciliation into `conversion/MultiRecordPaymentAssembler`, retaining CSV tag/width/required-column checks in the handler and preserving validation order and diagnostics.
- [x] 13.2 Consolidate common Shade settings for both fat JARs while retaining separate classifiers/manifests, JAXB provider resources and the runner-only CLI Main-Class.
- [x] 13.3 Document the implemented Maven/package responsibilities, update profile/design/packaging requirements, and preserve existing public APIs, tests and pre-existing working changes.
- [x] 13.4 Compare moved mapping/validation methods against the pre-refactor working files and run a clean Java 21 verification with the installed vendor API: 229 passed, zero failures/errors, one optional installed-Xerces test skipped because `webmethods.xerces.jar` was not supplied. Pass the six-sample checker, mapping/wrong-layout checks and flat control; see `docs/verification.md`.
- [x] 13.5 Validate the updated OpenSpec change with `openspec validate file-to-pain001 --strict --no-interactive --json` (passed with no issues) and run `git diff --check` (passed).


## 14. Shared field rules and readability cleanup — 2026-10-16

- [x] 14.1 Centralize optional transaction fields and required-field policy in `PaymentInputProfile`, reusing it in both handlers and the legacy layout inspection API without changing validation ordering.
- [x] 14.2 Centralize legacy layout-to-profile selection in `PaymentProfileRegistry.forLayout` and reuse it in the converter and CLI.
- [x] 14.3 Inspect unused compatibility candidates; retain the public layout-based handler constructor and `CsvLayout.requiredHeaders()` with explicit compatibility documentation because external usage cannot be ruled out. Remove their duplicated optional-field definition rather than public entry points.
- [x] 14.4 Use named required/optional text validation methods, retain the boolean-based public method, extract the XML-character predicate and expand compressed guard clauses.
- [x] 14.5 Introduce a file-level validation-error factory and remove dummy `PaymentFields` allocations while preserving diagnostic text.
- [x] 14.6 Format the POM consistently and compare its parsed XML elements, attributes and values with the pre-format version to confirm configuration equivalence.
- [x] 14.7 Pass clean Java 21 verification with the installed vendor API (229 passed, zero failures/errors, one optional installed-Xerces check skipped), all six standalone fixture/mapping checks and flat/wrong-layout controls, strict OpenSpec validation and `git diff --check`. Existing tests and unrelated research notes remain unchanged.
