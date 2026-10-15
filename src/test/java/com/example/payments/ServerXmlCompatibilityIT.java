package com.example.payments;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

class ServerXmlCompatibilityIT {
    private static final String PROPERTY = "javax.xml.validation.SchemaFactory:" + XMLConstants.W3C_XML_SCHEMA_NS_URI;

    @Test
    void hostProviderRejectingExternalAccessPropertiesDoesNotBreakBundledSchema() throws Exception {
        String xml = convertWithHostProvider("com.example.payments.LegacySchemaFactory",
                List.of(Path.of("target/test-classes").toAbsolutePath().toUri().toURL()));
        XmlChecks.independentlyValidate(xml);
    }

    @Test
    @EnabledIfSystemProperty(named = "webmethods.xerces.jar", matches = ".+")
    void installedServerXercesDoesNotBreakBundledSchema() throws Exception {
        String xml = convertWithHostProvider("org.apache.xerces.jaxp.validation.XMLSchemaFactory",
                List.of(Path.of(System.getProperty("webmethods.xerces.jar")).toUri().toURL()));
        XmlChecks.independentlyValidate(xml);
    }

    private String convertWithHostProvider(String provider, List<URL> hostLibraries) throws Exception {
        String previous = System.getProperty(PROPERTY);
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        var urls = new ArrayList<>(hostLibraries);
        urls.add(Path.of("target/file-to-pain001-1.0.0-SNAPSHOT-webmethods.jar").toAbsolutePath().toUri().toURL());
        try (var isolated = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(isolated);
            System.setProperty(PROPERTY, provider);
            // Prove the same incompatible ambient provider is still selected for ordinary callers.
            SchemaFactory ambient = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            assertEquals(provider, ambient.getClass().getName());
            assertThrows(org.xml.sax.SAXNotRecognizedException.class,
                    () -> ambient.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""));
            Class<?> converter = isolated.loadClass("com.example.payments.conversion.PaymentFileConverter");
            String xml = assertDoesNotThrow(() -> (String) converter.getMethod("convertToXml", String.class)
                    .invoke(converter.getConstructor().newInstance(), Path.of("samples/csv/valid/basic.csv").toAbsolutePath().toString()));
            var invalid = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> converter.getMethod("convertToXml", String.class).invoke(converter.getConstructor().newInstance(),
                            Path.of("samples/csv/invalid/schema-aggregate-sum.csv").toAbsolutePath().toString()));
            assertEquals("com.example.payments.conversion.PaymentConversionException", invalid.getCause().getClass().getName());
            assertTrue(invalid.getCause().getMessage().contains("schema validation failed"), "XSD validation must remain enabled");
            assertEquals(provider, System.getProperty(PROPERTY), "Conversion must not change host-wide XML provider settings");
            return xml;
        } finally {
            if (previous == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, previous);
            Thread.currentThread().setContextClassLoader(context);
        }
    }
}
