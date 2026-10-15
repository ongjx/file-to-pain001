## ADDED Requirements

### Requirement: Official schema is bundled with provenance
The project SHALL bundle the unmodified official `pain.001.001.09` XSD and any required schema dependencies under `src/main/resources/xsd`. It SHALL record the source URL, retrieval date, message identifier, original notices, and checksum.

#### Scenario: Verify schema provenance
- **WHEN** a developer inspects the bundled schema and provenance record
- **THEN** the message identifier and target namespace agree and the recorded checksum matches the file

### Requirement: Maven generates the Java payment model
The Maven build SHALL generate and compile Java classes from the bundled XSD during `generate-sources`, in a versioned package and generated source directory. Handwritten mapping code SHALL populate this generated model directly.

#### Scenario: Clean build generates the model
- **WHEN** `mvn clean verify` runs with compatible Java and resolved dependencies
- **THEN** XJC generates Java classes in `target/generated-sources/jaxb`, they compile, and the application JAR contains the generated classes

### Requirement: XML is serialized and schema validated
The XML writer SHALL serialize generated model instances as UTF-8 pain.001 XML in the chosen namespace and validate them against the bundled XSD. Runtime schema access SHALL be local, with external DTD and schema retrieval disabled.

#### Scenario: Valid object produces conforming XML
- **WHEN** a supported, complete payment document is serialized
- **THEN** its XML passes a separate validation against the bundled official XSD and preserves its payment values

#### Scenario: Generated object violates schema
- **WHEN** a document is missing an XSD-required value or exceeds an XSD constraint
- **THEN** serialization reports a validation failure and the runner emits no successful XML output

#### Scenario: Packaged runtime has no schema network access
- **WHEN** the runner executes outside the source directory without network access, with dependencies already packaged
- **THEN** it resolves the bundled schema and produces validated XML without downloading schema resources
