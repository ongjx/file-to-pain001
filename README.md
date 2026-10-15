# File to pain.001

A Java 21/JAXB 4 library that converts supported flat and multi-record CSV payment files into validated ISO 20022 `pain.001.001.09` XML. A webMethods Java service calls the library directly with an input file path and optional versioned input profile and receives an XML string.

Project proposals, specifications, and implementation tasks are tracked in [OpenSpec](openspec/changes/file-to-pain001/proposal.md) inside this repository.

```text
FLOW inputPath, optional profileId -> Java service -> PaymentFileConverter.convertToXml(inputPath[, profileId])
                             <- validated XML String
FLOW pain001Xml <- Java service
```

## Build and try it

Use JDK 21 and Maven 3.9 or newer. The implementation setup installed a checksum-verified Temurin JDK for this project without changing your default Java:

```sh
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/file-to-pain001-temurin-21.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
cd /Users/junxiang/Development/IBMWebMethods/file-to-pain001
mvn clean verify
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/valid/basic.csv
```

On another machine, set `JAVA_HOME` to its own JDK 21 installation. All application/generated classes target Java 21. Java 17 would require an explicit target change and a new verified build.

To create a new XML file in an existing directory:

```sh
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/valid/basic.csv --output target/basic.xml
```

An existing destination is never overwritten. Without `--output`, only XML goes to stdout; diagnostics go to stderr. Relative paths are relative to the process working directory. For the alternate CSV layout:

```sh
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/valid/alternate-headers.csv --layout camel-case
```

For PH/TX/IN/FX multi-record input, select its profile explicitly:

```sh
java -jar target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar samples/csv/multirecord/04-fx-and-invoices.csv --profile mock-multirecord-v1
```

`--profile flat-default-v1` and `--profile flat-camel-case-v1` select the flat contracts. Legacy `--layout default|camel-case` remains supported; combining `--layout` and `--profile` or repeating either selector is a usage error.

Maven can also run the CLI after compilation:

```sh
mvn -q compile exec:java -Dexec.args="samples/csv/valid/basic.csv"
```

## Java API

```java
import com.example.payments.conversion.PaymentFileConverter;

String pain001Xml = new PaymentFileConverter().convertToXml(inputPath);
String multiRecordXml = new PaymentFileConverter().convertToXml(inputPath, "mock-multirecord-v1");
```

`inputPath` is a nonblank string naming a `.csv` file accessible to the JVM. The method returns XML only after the complete input is processed and the result passes XSD validation. It does not create an output file, write to stdout, or launch another process. Failures throw `PaymentConversionException`, whose `kind()` distinguishes usage, input I/O, validation, and processing errors.

For model inspection or explicit selection, use `convert(Path)`, `convert(Path, CsvLayout)`, `convert(Path, String profileId)`, and `toXml(Document)`. The `Document` is the generated pain.001 model; `convert` alone does not certify schema validity. `toXml` performs that validation. Omitted selection defaults to `flat-default-v1`; explicit null/blank/unknown IDs fail without guessing. `new PaymentFileConverter(Clock)` supports reproducible timestamps in tests. Converter instances can be reused concurrently across profiles; each call creates its own parsing/assembly state, model and marshaller.

## Project structure

The project uses Maven's standard source layout. Handwritten Java code lives under
`src/main/java/com/example/payments/`, grouped by responsibility:

| Package | Responsibility |
| --- | --- |
| `cli` | Command arguments, exit statuses and output files |
| `input` | Profile contracts, handler selection, source values and field validation |
| `input.csv` | CSV transport, headers, positional columns and record structure |
| `conversion` | Public facade, payment mapping, grouping, batch ownership and reconciliation |
| `xml` | XML serialization and XSD validation |

`PaymentDocumentAssembler` maps common payment fields and maintains message totals.
`MultiRecordPaymentAssembler` adds PH/TX/IN/FX ownership and detail reconciliation;
the CSV handlers pass validated source records to these assemblers.

Other directories have distinct roles:

- `src/main/resources/xsd/`: bundled schema and provenance.
- `src/test/java/`: unit tests, packaged integration tests and test helpers.
- `samples/csv/`: valid and invalid input fixtures and the standalone sample checker.
- `examples/webmethods/`: adapter source compiled inside webMethods.
- `docs/`: integration, profile onboarding and verification guides.
- `openspec/`: requirements and design history.
- `target/`: generated model, compiled classes, reports and packaged artifacts; recreated by Maven.

## Schema and generated POJOs

The original ISO XSD is in `src/main/resources/xsd/pain.001.001.09.xsd`, with its source URL and checksum in [schema provenance](src/main/resources/xsd/README.md). It has no imports or includes.

```sh
mvn generate-sources
```

XJC generates `com.example.payments.pain001.v09` under `target/generated-sources/jaxb`. Maven compiles these Java files into the library. Generated source is available to inspect, but is recreated after `clean`; do not edit it. The XSD is the source of truth. Generation happens during builds, not for each incoming payment file.

## Build outputs

| Output | Purpose |
| --- | --- |
| `target/file-to-pain001-1.0.0-SNAPSHOT-webmethods.jar` | Preferred webMethods deployment: one fat library JAR containing the application, generated model, schema and all runtime dependencies |
| `target/file-to-pain001-1.0.0-SNAPSHOT.jar` | Optional thin library for dependency-managed callers |
| `target/dependency/*.jar` | Dependencies required only when using the thin library |
| `target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar` | Dependency-inclusive local CLI; preserves JAXB provider registration |
| `target/generated-sources/jaxb/` | Generated Java model |

For Integration Server, deploy **only the `-webmethods.jar` fat library** in your package's `code/jars/` and add it to Designer's compile classpath. It includes Commons CSV, JAXB and their transitive dependencies and preserves JAXB provider metadata. When migrating from the thin distribution, remove the old application/dependency copies from that package's classpath before reloading; do not mix both distributions. The `-runner.jar` is the separate CLI artifact. webMethods platform classes are supplied by the server and are not bundled. See the [integration guide](docs/webmethods-integration.md) and [adapter source](examples/webmethods/PaymentFileServices.java).

## Supported CSV contracts

See [flat mock files and column mapping](samples/csv/README.md) for the original 36 flat fixtures and [multi-record examples](samples/csv/multirecord/README.md) for six core samples, three additional valid cases and 47 focused invalid cases. All CSV profiles support UTF-8/BOM, CRLF/LF, quoted commas, escaped quotes, quoted newlines, optional values, and blank records. Flat profiles also support reordered headers. A new client layout needs an explicit contract; file extension alone does not define payment semantics.

One flat data row or multi-record TX is one credit transfer. One file becomes one message. Payment groups are separated by debtor name/IBAN/BIC, execution date, and currency; multi-record groups additionally preserve PH batch identity. IN rows produce individual structured invoice entries; FX rows retain source unit currency, rate and contract. Counts and sums include only instructed payment amounts, never invoice or FX source amounts. All groups use `TRF` and exact decimal arithmetic. Mixed-currency messages omit the optional message control sum. Duplicate end-to-end IDs and conflicting party information are rejected within a file. One invalid record rejects the entire file.

See [input profiles and client onboarding](docs/input-profiles.md) for handler/mapping responsibilities, the profile registry, and steps for adding a client contract. The mock multi-record tags, invoice reconciliation and FX rate direction are explicit prototype choices, not universal bank conventions.

## Verification

`mvn clean verify` generates the model from scratch, runs model/conversion tests, packages all three JAR variants, and executes the packaged runner outside the project directory against every valid and invalid fixture. Separate isolated-classloader tests convert every valid flat and multi-record fixture using only the webMethods fat JAR, with no Maven runtime dependencies visible. Successful XML is also checked by an independent XSD validator. Packaged tests verify Java 21 bytecode, JAXB provider registration, clean stdout, output protection, and paths containing spaces.

When an installed `wm-isclient.jar` is available, an optional test compiles the adapter and exercises its pipeline success/error behavior using the real vendor API:

```sh
mvn verify -Dwebmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar
```

Without that property, this vendor-specific test is explicitly skipped. The core build needs no proprietary dependency. This check does not start, deploy to, or invoke the live Integration Server.

CLI statuses: `0` success, `2` usage/profile/layout/unsupported extension, `3` input I/O, `4` CSV/payment/schema validation, `5` output I/O or unexpected processing failure.

See the [verification record](docs/verification.md) for the tested environment and results.

## Prototype scope

This version supports the documented IBAN-based input profile and one schema edition. Account/BIC/currency syntax and XSD constraints are checked; bank-specific rules, account existence, IBAN checksums, and currency minor-unit rules are outside this prototype. Fixtures deliberately use mock accounts and are not for payment execution. Reprocessing generates fresh message/group IDs; duplicate-submission protection is not implemented.

The generated model and XML string are held in memory. Large-file limits and production streaming are not promised. Excel and other formats can be added behind `PaymentFileHandler`; no generic format-inference system is included. Runtime schema validation uses only the bundled schema and disables external schema/DTD retrieval. A fresh Maven build still needs dependency downloads.
