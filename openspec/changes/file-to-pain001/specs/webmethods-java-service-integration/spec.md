## ADDED Requirements

### Requirement: Direct Java invocation returns XML to FLOW
The integration contract SHALL be FLOW `inputPath` plus optional `profileId` -> webMethods Java service -> in-process call to the compiled library -> FLOW `pain001Xml`. All three pipeline fields SHALL be strings when present. Absent `profileId` SHALL retain the path-only facade and default flat behavior; a present profile SHALL use `convertToXml(inputPath, profileId)`. A present blank, unknown, or non-string profile SHALL fail rather than fall back. The library SHALL own parsing, model population, serialization, and XSD validation; the service SHALL require no JSON conversion or FLOW mapping step.

#### Scenario: Successful service invocation
- **WHEN** the Java service receives an `inputPath` referring to a valid supported CSV file
- **THEN** it calls the library facade and returns its fully validated XML as `pain001Xml` in the pipeline

#### Scenario: Invocation does not depend on the CLI
- **WHEN** the Java service invokes conversion
- **THEN** the conversion executes as an ordinary method call inside Integration Server's JVM without launching a process or capturing stdout

#### Scenario: Client route selects its input profile
- **WHEN** FLOW supplies `inputPath` and `profileId` set to `mock-multirecord-v1`
- **THEN** the adapter forwards the profile to the library and returns its validated XML with mapped invoice and FX information

#### Scenario: Existing route has no profile field
- **WHEN** FLOW supplies only a valid default flat CSV `inputPath`
- **THEN** conversion retains the existing call and output behavior

#### Scenario: Invalid profile follows an earlier successful call
- **WHEN** a pipeline contains an old `pain001Xml` and supplies a blank, unknown, or non-string `profileId`
- **THEN** the service reports a `ServiceException`, removes the old XML, and releases its cursor

### Requirement: Input paths identify server-accessible files
The integration guide SHALL state that the file must be readable by the Integration Server operating-system user. It SHALL recommend absolute paths and explain the server working-directory meaning of relative paths.

#### Scenario: Client-only path is passed
- **WHEN** a supplied path does not exist or cannot be read on the server
- **THEN** conversion reports an input failure rather than attempting to retrieve a file from the caller's computer

### Requirement: Service failures do not expose stale output
The adapter example SHALL validate its required pipeline input, remove any previous `pain001Xml`, write the output only after successful conversion, translate conversion failures into a webMethods `ServiceException`, and release the pipeline cursor on all execution paths.

#### Scenario: Conversion fails after a previous result
- **WHEN** a pipeline contains an older `pain001Xml` and the next conversion fails
- **THEN** the service reports failure, leaves no previous or partial XML result, and releases its cursor

### Requirement: Deployment guidance identifies runtime dependencies
The project SHALL supply a thin Java service example and instructions for adding our library and compatible JAXB 4/runtime dependencies to the intended Integration Server package and Designer classpath. It SHALL identify Java 21 as the selected runtime and explain dependency/classloader checks. It SHALL keep webMethods platform classes out of the core JAR and distinguish local API verification from live server verification.

#### Scenario: Integration handoff
- **WHEN** a developer follows the integration guide
- **THEN** they can identify the facade call, pipeline fields, application/dependency artifacts, server JVM requirement, and checks still requiring the intended server
