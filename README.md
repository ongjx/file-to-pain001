# File to pain.001

A Java 21/JAXB 4 library that converts supported CSV payment files into validated ISO 20022 `pain.001.001.09` XML. A webMethods Java service calls the library directly with an input file path and receives an XML string.

```text
FLOW inputPath -> Java service -> PaymentFileConverter.convertToXml(inputPath)
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

Maven can also run the CLI after compilation:

```sh
mvn -q compile exec:java -Dexec.args="samples/csv/valid/basic.csv"
```

## Java API

```java
import com.example.payments.conversion.PaymentFileConverter;

String pain001Xml = new PaymentFileConverter().convertToXml(inputPath);
```

`inputPath` is a nonblank string naming a `.csv` file accessible to the JVM. The method returns XML only after the complete input is processed and the result passes XSD validation. It does not create an output file, write to stdout, or launch another process. Failures throw `PaymentConversionException`, whose `kind()` distinguishes usage, input I/O, validation, and processing errors.

For model inspection or an explicit layout, use `convert(Path)`, `convert(Path, CsvLayout)`, and `toXml(Document)`. The `Document` is the generated pain.001 model; `convert` alone does not certify schema validity. `toXml` performs that validation. `new PaymentFileConverter(Clock)` supports reproducible timestamps in tests. Converter instances can be reused concurrently; each call creates its own model and marshaller.

## Schema and generated POJOs

The original ISO XSD is in `src/main/resources/xsd/pain.001.001.09.xsd`, with its source URL and checksum in [schema provenance](src/main/resources/xsd/README.md). It has no imports or includes.

```sh
mvn generate-sources
```

XJC generates `com.example.payments.pain001.v09` under `target/generated-sources/jaxb`. Maven compiles these Java files into the library. Generated source is available to inspect, but is recreated after `clean`; do not edit it. The XSD is the source of truth. Generation happens during builds, not for each incoming payment file.

## Build outputs

| Output | Purpose |
| --- | --- |
| `target/file-to-pain001-1.0.0-SNAPSHOT.jar` | Application/library and generated model for webMethods |
| `target/dependency/*.jar` | Pinned runtime dependencies to deploy alongside the library |
| `target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar` | Dependency-inclusive local CLI; preserves JAXB provider registration |
| `target/generated-sources/jaxb/` | Generated Java model |

Use the library plus runtime dependencies for Integration Server. The CLI JAR is for local use, and is not an additional JAR to install alongside those artifacts. The project does not bundle webMethods platform classes. See the [integration guide](docs/webmethods-integration.md) and [adapter source](examples/webmethods/PaymentFileServices.java).

## Supported CSV contract

See [mock files and column mapping](samples/csv/README.md) for 36 synthetic examples, the two supported layouts, expected totals, and rejection reasons. CSV support includes UTF-8/BOM, CRLF/LF, quoted commas, escaped quotes, quoted newlines, reordered columns, optional values, and blank records. A new client layout needs an explicit mapping; file extension alone does not define payment semantics.

One CSV record is one credit transfer. One file becomes one message. Payment groups are separated by debtor name/IBAN/BIC, execution date, and currency. All groups use payment method `TRF`; counts and per-group sums are computed with exact decimal arithmetic. Mixed-currency messages omit the optional message control sum. Duplicate end-to-end IDs and conflicting party information are rejected within a file. One invalid record rejects the entire file.

## Verification

`mvn clean verify` generates the model from scratch, runs model/conversion tests, packages both JARs, and executes the packaged runner outside the project directory against every valid and invalid fixture. Successful XML is also checked by an independent XSD validator. The packaged tests verify Java 21 bytecode, JAXB provider registration, clean stdout, output protection, and paths containing spaces.

When an installed `wm-isclient.jar` is available, an optional test compiles the adapter and exercises its pipeline success/error behavior using the real vendor API:

```sh
mvn verify -Dwebmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar
```

Without that property, this vendor-specific test is explicitly skipped. The core build needs no proprietary dependency. This check does not start, deploy to, or invoke the live Integration Server.

CLI statuses: `0` success, `2` usage/layout/unsupported extension, `3` input I/O, `4` CSV/payment/schema validation, `5` output I/O or unexpected processing failure.

See the [verification record](docs/verification.md) for the tested environment and results.

## Prototype scope

This version supports the documented IBAN-based input profile and one schema edition. Account/BIC/currency syntax and XSD constraints are checked; bank-specific rules, account existence, IBAN checksums, and currency minor-unit rules are outside this prototype. Fixtures deliberately use mock accounts and are not for payment execution. Reprocessing generates fresh message/group IDs; duplicate-submission protection is not implemented.

The generated model and XML string are held in memory. Large-file limits and production streaming are not promised. Excel and other formats can be added behind `PaymentFileHandler`; no generic format-inference system is included. Runtime schema validation uses only the bundled schema and disables external schema/DTD retrieval. A fresh Maven build still needs dependency downloads.
