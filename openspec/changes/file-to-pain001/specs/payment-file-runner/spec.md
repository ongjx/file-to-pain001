## ADDED Requirements

### Requirement: Maven library and runnable application
The project SHALL target Java 21 and JAXB 4, producing a reusable library JAR with runtime dependencies available for webMethods and a dependency-inclusive executable runner JAR for local use. The primary library facade SHALL retain `convertToXml(String inputPath)` and add `convertToXml(String inputPath, String profileId)`, both returning validated XML as a `String`. Lower-level model conversion SHALL retain existing overloads and add explicit profile selection without webMethods dependencies or process termination. Java 17 SHALL be treated only as an explicitly selected, separately verified fallback target if deployment requires it.

#### Scenario: Java service caller obtains XML
- **WHEN** a Java 21 caller invokes `PaymentFileConverter.convertToXml(String inputPath)` on a converter instance with a valid default-layout CSV path
- **THEN** it receives fully validated pain.001 XML as a string, with no output file or subprocess required

#### Scenario: Java caller obtains a payment model
- **WHEN** a Java caller invokes the conversion API with a valid CSV path
- **THEN** it receives a populated generated pain.001 document suitable for inspection and XML serialization

#### Scenario: Java caller selects an input contract
- **WHEN** a caller invokes `convertToXml(inputPath, "mock-multirecord-v1")` for a valid mock multi-record CSV
- **THEN** it receives validated XML containing the mapped payments and invoice/FX details

#### Scenario: Packaged application runs independently
- **WHEN** the packaged runner is launched from outside the project directory with an absolute CSV path
- **THEN** it resolves its dependencies and schema from the packaged runtime and completes conversion

#### Scenario: Supported build baseline
- **WHEN** `mvn clean verify` is run with the selected Java 21 build JDK
- **THEN** it generates JAXB 4 model classes, compiles with release `21`, and verifies the conversion and packaging contracts

### Requirement: Input and output paths have explicit behavior
The runner SHALL accept a positional CSV path, optional `--profile <profileId>`, and an optional output path. Existing `--layout default|camel-case` SHALL remain supported as legacy flat-profile selection. It SHALL reject duplicate profile/layout selectors and any use of `--profile` together with `--layout`, including equivalent choices, as usage errors. Relative paths SHALL resolve against the working directory. It SHALL write fully validated XML to stdout by default; explicit output SHALL require an existing parent and create a new file without overwriting any existing destination or modifying the input.

#### Scenario: File path alone is supplied
- **WHEN** the caller provides a valid CSV path and no other options
- **THEN** the default profile is used, stdout contains only validated XML, and exit status is zero

#### Scenario: Destination already exists
- **WHEN** an explicit output path already exists, including the input file itself
- **THEN** the runner fails without changing that file

#### Scenario: Profile is explicitly selected
- **WHEN** the runner receives a valid multi-record path and `--profile mock-multirecord-v1`
- **THEN** it uses the selected handler and emits only validated XML on stdout or creates the requested new output file

#### Scenario: Profile options conflict
- **WHEN** `--profile` and `--layout` are both supplied, a selector repeats, or an unknown or blank profile ID is supplied
- **THEN** it reports usage on stderr, exits with status `2`, and emits no XML or output file

#### Scenario: Invalid source or failed write
- **WHEN** conversion fails or writing a new output file fails
- **THEN** the runner reports failure and leaves no incomplete new output file

### Requirement: Failures are diagnosable
The runner SHALL report diagnostics on stderr and use statuses `2` for usage/profile/layout/format, `3` for input I/O, `4` for source/payment/schema validation, and `5` for output I/O or unexpected processing failures. Diagnostics SHALL identify source record/field or available parser/schema location, including multi-record type where known, without dumping full payment data.

#### Scenario: Missing input path
- **WHEN** the runner is invoked without its required path
- **THEN** it prints usage to stderr, exits with status `2`, and emits no XML

#### Scenario: Input cannot be read
- **WHEN** the path is missing, unreadable, or not a regular file
- **THEN** it reports input failure with status `3` and emits no XML

#### Scenario: Unsupported format or invalid payment
- **WHEN** the input uses an unsupported extension or contains an invalid payment
- **THEN** it reports the appropriate format or validation diagnostic with status `2` or `4`, respectively
