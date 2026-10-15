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
