# webMethods integration

The production call boundary is a direct Java call inside Integration Server:

```text
FLOW -> inputPath (String), optional profileId (String)
     -> Java service
     -> our library JAR: new PaymentFileConverter().convertToXml(inputPath[, profileId])
     <- pain001 XML (String)
     <- Java service sets pain001Xml in the pipeline
```

The library owns source parsing, generated-object population, XML serialization, and XSD validation. The service does not invoke the CLI, start a subprocess, or perform a JSON conversion or FLOW MAP operation.

## Artifacts and Java

Build with Java 21 using `mvn clean verify`. The preferred deployment is the single
fat library `target/file-to-pain001-1.0.0-SNAPSHOT-webmethods.jar`. Copy it into the
intended Integration Server package's `code/jars/` directory and add it to the Java
service's Designer compile classpath. It includes the application, generated model,
schema, Commons CSV, JAXB, activation, and all transitive runtime dependencies.
JAXB service-provider metadata is merged and preserved. It is a library without a
CLI Main-Class; the Java service continues to call `convertToXml` directly.

When migrating from the thin distribution, remove its old application JAR and the
ten separate dependency JARs previously added for this library from the package
classpath, and deploy the fat library instead. Preserve libraries owned by other
package functionality. Use the supported package reload procedure to refresh its
classloader. Do not install the `-runner.jar` alongside the fat library and do not
copy the vendor `wm-isclient.jar`; webMethods supplies its own platform classes.

The unclassified thin library plus `target/dependency/*.jar` remains an optional
alternative for dependency-managed deployments. Deploy one complete distribution,
never a mixture. A fat JAR prevents missing dependency files; it does not override
Integration Server's parent-first class-loading rules or remove existing conflicts.

Verify that the actual Integration Server JVM is Java 21 or newer. Product support for a JVM does not automatically change the configured server JVM. Our classes use Java 21 bytecode; a Java 17 JVM cannot load this build.

The generated model uses `jakarta.xml.bind` and the packaged dependency set provides JAXB 4. Inspect existing package/server JAXB and Commons dependencies before adding these artifacts. By default, server classes can take precedence over package classes. If required, use the supported package classloader configuration after reviewing the intended package's dependencies; do not change server-wide libraries just to run this example. Reload/restart requirements depend on the platform's deployment configuration.

### Missing runtime classes

`ClassNotFoundException: org.apache.commons.csv.CSVFormat` means Commons CSV is not
visible to the package at runtime. `NoClassDefFoundError: jakarta/xml/bind/JAXBException`
similarly indicates a missing JAXB API. Copying only the normal application JAR,
or adding dependencies only to Designer's compile classpath, is insufficient.

Use the single `-webmethods.jar` distribution described above. If deliberately using
the optional thin library, deploy all ten JARs in `target/dependency/` alongside it;
the thin library alone is insufficient. Reload the package through Designer or
Integration Server package administration so it refreshes its classloader, then
rerun the service. A server restart is only needed if the supported package reload
does not refresh relevant classes. Do not replace server-global JARs.

The example adapter preserves the underlying runtime exception as the cause of
`ServiceException`, while its public message includes only the error type and
deployment guidance. Copy that revised catch block into the Designer-managed Java
service to retain cause details in future failures; arbitrary runtime exception
messages are not appended to the public message.

### Schema initialization fails with the server's Xerces provider

If the cause chain includes `Cannot initialize bundled pain.001 schema` and
`SAXNotRecognizedException` for `accessExternalDTD`, the server's older Xerces
schema factory has been selected through JAXP provider discovery. This is a
provider compatibility failure, not a missing schema or invalid payment CSV.

The XML writer now uses `SchemaFactory.newDefaultInstance()` to select Java 21's
built-in validator for the bundled schema, while retaining secure processing and
empty external DTD/schema access lists. It does not change global XML properties
or require replacing the server's Xerces libraries. Deploy the rebuilt webMethods
fat JAR and reload the package to discard the old classloader's failed static
initialization. Retrying inside the same failed classloader is insufficient.

The optional server-provider regression can be run alongside the vendor API test:

```sh
mvn clean verify -Dwebmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar -Dwebmethods.xerces.jar=/Applications/wMServiceDesigner/common/lib/ext/xercesImpl.jar
```

## Create the Java service in Designer

1. Create a Java service, for example `payments:convertFile`, in your intended package. Keep the old CSV proof of concept separate.
2. Define required input `inputPath` and optional input `profileId` of type String, and output `pain001Xml` of type String. Omit `profileId` for the default flat layout. Use `flat-default-v1`, `flat-camel-case-v1`, or `mock-multirecord-v1` for explicit selection.
3. Add the imports shown in [PaymentFileServices.java](../examples/webmethods/PaymentFileServices.java).
4. Copy the `convertFile` method's body into the primary Java service method generated by Designer. Let Designer retain its own class name and generated wrapper.
5. Compile the service against the application/runtime artifacts and the normal platform API supplied by Designer.
6. In FLOW, assign `inputPath` and, when needed, the profile from the configured client route; invoke this Java service and consume `pain001Xml` after success. For multi-record mock input, set `profileId` to `mock-multirecord-v1`.

The complete standalone adapter example is kept outside the Maven main sources so the library build does not require proprietary platform APIs. It is not a preinstalled Integration Server service.

## Path and error behavior

`inputPath` must identify a `.csv` file readable by the Integration Server operating-system user. It can refer to a server-local file or mounted share. A file on the Designer user's laptop is not automatically available on the server. Prefer an absolute path; relative paths resolve against the server JVM's working directory.

The adapter clears any previous `pain001Xml` before validating either input, calls the Java facade, and sets output only after successful conversion. Absent `profileId` preserves the old path-only call; a present null, non-string, blank or unknown profile fails without fallback. It releases its pipeline cursor in `finally`. Conversion failures become `ServiceException` diagnostics with logical record/type/field information where available. No valid subset or partial XML is returned. Handle the exception through your FLOW error path.

No output file is required. File creation and stdout belong to the optional local runner, not this Java API.

## Verification boundary

The normal Maven tests exercise the default and profile-aware facades, independently
validate XML, and convert every valid fixture in an isolated classloader containing
only the webMethods fat JAR and platform Java classes. The optional command below
also compiles and invokes the adapter with only that fat library and the installed
vendor API visible, verifying flat and multi-record output. Additional vendor-API
checks cover invalid input/profile types and values, wrong-profile failures, null
profiles, missing dependencies in thin deployments, cause preservation and stale XML:

```sh
mvn verify -Dwebmethods.client.jar=/Applications/wMServiceDesigner/common/lib/wm-isclient.jar
```

This local adapter check is not a live FLOW or Integration Server deployment test. After deploying to your intended server, verify the configured JVM and package classloading, use a server-accessible mock CSV, and test both success and the FLOW error path. The original implementation did not change the server; subsequent local dependency repair is recorded in `docs/verification.md`.

## References

- [IBM: building Java services](https://www.ibm.com/docs/en/webmethods-integration/wm-integration-server/12.1.0?topic=guide-building-java-services)
- [IBM: class searching order](https://www.ibm.com/docs/en/webmethods-integration/wm-integration-server/11.1.0?topic=classpath-class-searching-order)
- [webMethods IDataUtil API](https://docs.webmethods.io/on-premises/wmsuites/en/10.1.0/integration-server-java-api-reference/com/wm/data/IDataUtil.html)
