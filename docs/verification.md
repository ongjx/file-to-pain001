# Verification record

Verified on 2026-10-15 in the implementation workspace.

## Environment

- macOS arm64
- Eclipse Temurin OpenJDK 21.0.12.1+1 (JDK archive checksum verified against the official Adoptium API)
- Maven 3.9.9
- Compiler release: 21
- Jakarta XML Binding API 4.0.5; JAXB runtime/XJC 4.0.9; XJC Maven plugin 4.2.0
- Commons CSV 1.14.1
- Vendor API used for optional adapter check: installed `wm-isclient.jar` under `/Applications/wMServiceDesigner/common/lib/`

The JDK was extracted under the user's JavaVirtualMachines directory without changing the system/default Java. Set `JAVA_HOME` as shown in the project guide before building.

## Results

The complete clean build passed, including regeneration from the official bundled XSD. A subsequent verification run added and passed an explicit normal-library-distribution check. Final result: **88 tests passed, zero failures/errors/skips** with the optional vendor API check enabled:

- 46 conversion/model/schema tests.
- 41 packaged application/distribution tests.
- 1 optional real-webMethods-API adapter test.

The vendor test is skipped on a normal build unless `webmethods.client.jar` is supplied. There is no proprietary dependency in the core project's Maven dependencies.

Checks covered all 11 valid and 25 invalid fixtures; actual payment fields; namespace; optional-element omission; exact decimals; noncontiguous grouping/order; mixed-currency totals; malformed CSV; source/schema rejection; concurrent facade calls; and the official schema's original checksum. Each successful fixture's output passed an independent XSD validator.

Packaged application checks used a temporary working directory outside the project, verified clean stdout and process statuses, protected existing input/output files, and confirmed failed conversions created no output. Both the dependency-inclusive runner and the normal library JAR with its runtime dependency directory were exercised. Packaged classes use Java 21 bytecode and retain JAXB provider registration.

The adapter source compiled against real installed vendor classes. Local invocation using real `IData` verified XML output on success, `ServiceException` behavior on invalid inputs/conversion failures, and stale-output removal. Cursor cleanup was also reviewed in the adapter's `finally` block.

The documented Maven execution command successfully produced well-formed pain.001 XML, and the standalone runner created `target/demo-pain001.xml` from `samples/csv/valid/basic.csv`. Model generation was also exercised directly using `mvn generate-sources`.

## Boundary

No FLOW service, package, classloader setting, or running Integration Server was modified or deployed. Live server JVM/classloading, filesystem permissions, FLOW success/error handling, and recipient-specific requirements still need verification when deploying to the intended server. Local vendor-API checks are not a live deployment test.

## Profile and multi-record extension — 2026-10-15

Implemented the profile registry and shared validation/document assembly, retaining
the two flat layouts and path-only facade. Added `mock-multirecord-v1`, explicit
library/CLI/profile pipeline selection, structured invoices, exchange-rate
information, batch-aware grouping and exact reconciliation rules.

Final clean verification command, using the Java 21 environment above:

```sh
mvn clean verify -q -Dwebmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar
python3 samples/csv/multirecord/check_samples.py
openspec validate file-to-pain001 --strict --no-interactive --json
```

Results: **205 tests passed, zero failures/errors/skips**:

- 46 existing flat conversion/model/schema tests.
- 60 profile/multi-record conversion tests.
- 98 packaged runner/distribution tests, including the existing flat regressions.
- 1 installed-vendor-API adapter test covering both facades and invalid profiles.

All six original multi-record samples and three additional valid fixtures passed
independent XSD validation and source-to-XML value checks through both the library
and packaged runner. Assertions cover individual invoice IDs/dates/amounts/currencies,
FX unit currency/rate/contract, instructed target amounts, debtor fields, remittance,
PH separation, mixed-currency splitting, order, counts and exact totals. The rate
orientation and reconciliation-only FX source amount follow the documented mock
contract; this is not verification of a recipient bank's FX rules.

All 47 focused multi-record rejection fixtures failed for their documented reason
through the library and runner. Runner failures created no output and left inputs
unchanged. Coverage includes malformed/unknown/incorrect-width records, orphan and
mismatched ownership, duplicate IDs/details, EOF and next-PH count errors, empty
batches, invoice/FX mismatches, common values, XML characters and aggregate schema
constraints. Wrong-profile, unknown/blank profile, mixed/repeated CLI selectors,
output protection and concurrent mixed-profile conversions also passed.

The installed vendor-API test compiled the revised adapter and invoked it using
real IData. It verified default flat conversion, explicit multi-record conversion,
wrong-profile/unknown/blank/non-string/null profile failure, and stale-output
removal. The adapter retains cursor cleanup in `finally`. No live Integration
Server or FLOW deployment was performed.

The standalone checker passed all six core fixtures, selected-profile mapping
checks, both flat-layout rejection checks, flat positive control and source
immutability. It writes `target/multirecord-test-results.json`. Its XML checks are
field comparisons; independent XSD validation is provided by the Java test suite.
Strict OpenSpec validation passed with no issues. New implementation tasks were
marked complete after these checks.

## Local runtime dependency repair — 2026-10-15

The reported live FLOW failure was `ClassNotFoundException:
org.apache.commons.csv.CSVFormat`, hidden by the adapter's generic runtime message.
Inspection found only the application JAR in the local `test/code/jars` directory;
none of the ten built runtime dependency JARs were present. The application JAR
matched the current build and contained the schema. The installed server JVM is
IBM Semeru/OpenJ9 Java 21.0.10.

A throwaway harness invoked the deployed `javaflow.fileToPainXml` locally using the
installed server JVM, its real compiled adapter, application JAR, vendor IData API
and the flat basic.csv fixture. This reproduced the exact generic ServiceException.
With no server-global dependency visibility in that isolated process, its first
missing class was JAXBException. Adding the exact built dependency set made the
same invocation return XML. The live user's CSVFormat cause independently confirms
a missing Commons CSV dependency; it need not be the first missing class in the
isolated process.

Copied all ten runtime dependency JARs from `target/dependency/` into
`/Applications/wMServiceDesigner/IntegrationServer/packages/test/code/jars/`,
without overwriting any existing file, and verified their hashes. The original
harness then returned XML using only the repaired package classpath and vendor
IData API, with no project dependency folder on its classpath. The application JAR,
Designer-managed Java source and compiled adapter were left unchanged. No package
reload, server restart or live FLOW invocation was performed; reload `test` and
rerun the FLOW to confirm its running classloader has picked up the JARs.

The repository example now preserves runtime/linkage failure causes. Added a real
vendor-API regression that isolates the application from Maven's classpath and
omits JAXB API or Commons CSV in turn. It asserts ServiceException retains the
specific missing-class cause and clears stale XML. The test first failed because
the cause was absent, then passed after the fix. Verification command:

```sh
mvn -q test-compile failsafe:integration-test failsafe:verify -Dit.test=WebMethodsAdapterIT -Dwebmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar
```

This targeted test passed, including existing success/profile/error paths and both
missing-dependency cases. The broader conversion implementation was unchanged;
its earlier 205-test clean-build evidence remains recorded above. The temporary
reproduction harness was removed after use. Future deployments must include the
normal application JAR plus the complete dependency set in the package, rather
than only setting Designer's compile classpath.

## Single-JAR webMethods packaging — 2026-10-15

The preferred deployment is now
`target/file-to-pain001-1.0.0-SNAPSHOT-webmethods.jar`: one fat library containing
our classes, generated model, schema, and all ten runtime dependency artifacts.
The build preserves JAXB provider metadata and dependency notices, excludes vendor
platform classes, and leaves out the CLI Main-Class. The optional thin distribution
and separate runner remain available, but must not be mixed in one server package.

Clean verification passed **228 tests, zero failures/errors/skips**, using:

```sh
mvn clean verify -q -Dwebmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar
```

The previous 205 tests remain, plus 22 isolated single-JAR distribution tests and
one additional installed-vendor-API test. The isolated tests have only the fat
library and Java platform classes visible and process all 11 valid flat and nine
valid multi-record fixtures, independently validate XML, and compare multi-record
source/detail values. They also check validation failures, bundled dependency
classes/schema/provider metadata, no vendor classes, and no CLI Main-Class.

The added vendor test compiles and invokes the example adapter with only the fat
library and installed vendor API visible and checks both flat and multi-record
output. A separate throwaway local probe used IBM Semeru/OpenJ9 Java 21.0.10, the
actual deployed javaflow adapter class, only the vendor API and new fat library,
and basic.csv: conversion returned pain.001 successfully. The probe was removed
afterward. Strict OpenSpec validation and the six-sample checker also passed.

This packaging update did not replace JARs in the running package or reload the
server. To migrate the local test package, replace the thin application JAR and
its ten previously added dependency copies with the one `-webmethods.jar`, update
Designer's compile classpath to that JAR, and reload the package. Preserve unrelated
package dependencies. The fat JAR resolves missing deployment artifacts; server
class-loading precedence still needs consideration if incompatible global classes
are present. Live FLOW execution after migration remains to be checked.

## Host Xerces schema-provider compatibility fix — 2026-10-15

The live failure's deepest recorded cause was `SAXNotRecognizedException` for
`http://javax.xml.XMLConstants/property/accessExternalDTD`, thrown by the server's
`org.apache.xerces.jaxp.validation.XMLSchemaFactory`. JAXP provider discovery was
selecting that older server factory, which does not support the external-access
properties used by the bundled-schema initializer. The schema itself was present.

A minimal throwaway probe using the server's IBM Semeru/OpenJ9 Java 21 runtime,
the fat library and `/Applications/wMServiceDesigner/common/lib/ext/xercesImpl.jar`
reproduced the exact `ExceptionInInitializerError` -> `Cannot initialize bundled
pain.001 schema` -> `SAXNotRecognizedException` chain. No vendor adapter, FLOW or
other server dependencies were needed to reproduce it.

Added a portable regression provider that rejects the same security properties and
an optional regression using the actual installed server Xerces JAR. Both failed
against the old artifact with the exact initializer/cause chain. The production
fix changes only schema-factory selection to `SchemaFactory.newDefaultInstance()`;
secure processing, external DTD/schema restrictions, XSD bytes and validation remain
unchanged. Tests confirm host provider configuration remains unchanged, valid
conversion works, and XSD-invalid aggregate amounts are still rejected.

Clean verification passed **230 tests, zero failures/errors/skips**, using:

```sh
mvn clean verify -q -Dwebmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar -Dwebmethods.xerces.jar=/Applications/wMServiceDesigner/common/lib/ext/xercesImpl.jar
```

The original probe then passed on the server JVM with the same external Xerces
still selected for ambient JAXP callers. It also passed when using the repaired
JAR from the actual local test package path.

Replaced the local package's `code/jars/file-to-pain001-1.0.0-SNAPSHOT-webmethods.jar`
after verifying that its application-class differences were limited to the XML
writer and nested binding class. Backed up the prior JAR outside the server
classpath under `target/deployment-backups/schema-provider/`. Server-wide XML
settings and libraries were unchanged. No live FLOW invocation or package reload
was performed: reload `test` to create a fresh classloader and clear the failed
static initializer, then rerun the service. Both throwaway probe files were removed.

The previous isolated packaging tests lacked the host's provider registration.
The new portable and installed-Xerces regressions cover that missing environment
condition without changing the architecture or disabling XML security.

## Behavior-preserving structure cleanup — 2026-10-15

Separated CSV record parsing from multi-record payment assembly. The new
`MultiRecordPaymentAssembler` owns batch/detail state, generated-model mapping and
reconciliation; the CSV handler retains tag, width and required-column checks.
Moved validation/mapping method bodies were compared with the pre-refactor working
files after normalizing indentation and identifier renames. Shared Shade settings
now serve both packaged JAR variants, retaining separate manifests.

Using the documented Java 21 installation, `mvn clean verify -q` with
`webmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar`
completed with 229 passed, no failures/errors and one optional server-Xerces test
skipped because `webmethods.xerces.jar` was not supplied. Existing tests were not
modified. The standalone multi-record sample checker also passed all six core
samples, mapping checks, wrong-layout rejection and the flat positive control.
`git diff --check` passed. No deployment was performed.
