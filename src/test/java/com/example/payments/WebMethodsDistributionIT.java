package com.example.payments;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** The deployable library must work with neither Maven dependencies nor the runner visible. */
class WebMethodsDistributionIT {
    private static final Path JAR = Path.of("target/file-to-pain001-1.0.0-SNAPSHOT-webmethods.jar");

    @ParameterizedTest(name = "single-JAR flat conversion: {0}")
    @MethodSource("com.example.payments.PaymentFileConverterTest#valid")
    void flatInputsWorkFromOnlyTheDeploymentJar(Path fixture) throws Exception {
        String profile = fixture.getFileName().toString().equals("alternate-headers.csv")
                ? "flat-camel-case-v1" : "flat-default-v1";
        String xml = convert(fixture, profile);
        XmlChecks.independentlyValidate(xml);
        assertEquals("Document", XmlChecks.parse(xml).getDocumentElement().getLocalName());
    }

    @ParameterizedTest(name = "single-JAR multi-record conversion: {0}")
    @MethodSource("com.example.payments.MultiRecordPaymentTest#valid")
    void multiRecordInputsRetainAllTheirDetailsFromOnlyTheDeploymentJar(Path fixture) throws Exception {
        MultiRecordAssertions.assertFixture(fixture, convert(fixture, "mock-multirecord-v1"));
    }

    @Test
    void invalidSourceRetainsTheValidationExceptionAcrossTheDeploymentInterface() {
        var invocation = assertThrows(InvocationTargetException.class,
                () -> convert(Path.of("samples/csv/multirecord/invalid/invoice-sum.csv"), "mock-multirecord-v1"));
        Throwable cause = invocation.getCause();
        assertEquals("com.example.payments.conversion.PaymentConversionException", cause.getClass().getName());
        assertTrue(cause.getMessage().contains("invoice amounts"));
    }

    @Test
    void libraryContainsSchemaDependenciesAndProviderRegistrationWithoutVendorClasses() throws Exception {
        assertTrue(Files.isRegularFile(JAR));
        try (JarFile jar = new JarFile(JAR.toFile())) {
            for (String entry : List.of("com/example/payments/conversion/PaymentFileConverter.class",
                    "xsd/pain.001.001.09.xsd", "org/apache/commons/csv/CSVFormat.class",
                    "org/apache/commons/io/IOUtils.class", "org/apache/commons/codec/binary/Base64.class",
                    "jakarta/xml/bind/JAXBException.class", "jakarta/activation/DataSource.class",
                    "org/glassfish/jaxb/runtime/v2/ContextFactory.class",
                    "org/glassfish/jaxb/core/v2/model/core/TypeInfo.class",
                    "com/sun/istack/Pool.class", "com/sun/xml/txw2/TXW.class",
                    "org/eclipse/angus/activation/MailcapRegistryProviderImpl.class",
                    "META-INF/services/jakarta.xml.bind.JAXBContextFactory")) {
                assertNotNull(jar.getJarEntry(entry), entry);
            }
            assertNull(jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("com/wm/")));
        }
    }

    private String convert(Path fixture, String profile) throws Exception {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        try (var isolated = new URLClassLoader(new java.net.URL[]{JAR.toAbsolutePath().toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(isolated);
            Class<?> converter = isolated.loadClass("com.example.payments.conversion.PaymentFileConverter");
            assertSame(isolated, converter.getClassLoader());
            return (String) converter.getMethod("convertToXml", String.class, String.class)
                    .invoke(converter.getConstructor().newInstance(), fixture.toAbsolutePath().toString(), profile);
        } finally {
            Thread.currentThread().setContextClassLoader(context);
        }
    }
}
