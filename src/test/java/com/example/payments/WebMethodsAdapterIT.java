package com.example.payments;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Optional check against a real installed vendor API, without starting or deploying to a server. */
@EnabledIfSystemProperty(named = "webmethods.client.jar", matches = ".+")
class WebMethodsAdapterIT {
    @TempDir Path classes;

    @Test
    void deploymentJarAloneCompilesAndRunsTheAdapterWithTheVendorApi() throws Exception {
        Path vendor = Path.of(System.getProperty("webmethods.client.jar"));
        Path deployment = Path.of("target/file-to-pain001-1.0.0-SNAPSHOT-webmethods.jar").toAbsolutePath();
        int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-classpath", deployment + java.io.File.pathSeparator + vendor,
                "-d", classes.toString(), "examples/webmethods/PaymentFileServices.java");
        assertEquals(0, compiled);
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        try (var isolated = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL(), vendor.toUri().toURL(),
                deployment.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(isolated);
            Class<?> data = isolated.loadClass("com.wm.data.IData");
            Class<?> cursorType = isolated.loadClass("com.wm.data.IDataCursor");
            Class<?> util = isolated.loadClass("com.wm.data.IDataUtil");
            Class<?> factory = isolated.loadClass("com.wm.data.IDataFactory");
            var service = isolated.loadClass("example.webmethods.PaymentFileServices").getMethod("convertFile", data);
            for (boolean multi : List.of(false, true)) {
                Path input = Path.of(multi ? "samples/csv/multirecord/04-fx-and-invoices.csv" : "samples/csv/valid/basic.csv");
                Object pipeline = factory.getMethod("create").invoke(null);
                Object cursor = data.getMethod("getCursor").invoke(pipeline);
                util.getMethod("put", cursorType, String.class, Object.class).invoke(null, cursor, "inputPath", input.toAbsolutePath().toString());
                if (multi) util.getMethod("put", cursorType, String.class, Object.class)
                        .invoke(null, cursor, "profileId", "mock-multirecord-v1");
                cursorType.getMethod("destroy").invoke(cursor);
                service.invoke(null, pipeline);
                cursor = data.getMethod("getCursor").invoke(pipeline);
                String xml = (String) util.getMethod("get", cursorType, String.class).invoke(null, cursor, "pain001Xml");
                cursorType.getMethod("destroy").invoke(cursor);
                if (multi) MultiRecordAssertions.assertFixture(input, xml);
                else XmlChecks.independentlyValidate(xml);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(context);
        }
    }

    @Test
    void exampleCompilesAndHandlesSuccessAndFailureUsingRealIData() throws Exception {
        Path vendor = Path.of(System.getProperty("webmethods.client.jar"));
        assertTrue(Files.isRegularFile(vendor), "webmethods.client.jar must name the installed wm-isclient.jar");
        int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-classpath", Path.of("target/classes").toAbsolutePath() + java.io.File.pathSeparator + vendor,
                "-d", classes.toString(), "examples/webmethods/PaymentFileServices.java");
        assertEquals(0, compiled);
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL(), vendor.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> data = loader.loadClass("com.wm.data.IData");
            Class<?> cursorType = loader.loadClass("com.wm.data.IDataCursor");
            Class<?> factory = loader.loadClass("com.wm.data.IDataFactory");
            Class<?> util = loader.loadClass("com.wm.data.IDataUtil");
            var service = loader.loadClass("example.webmethods.PaymentFileServices").getMethod("convertFile", data);
            for (Object input : List.of(Path.of("samples/csv/valid/basic.csv").toAbsolutePath().toString(),
                    Path.of("samples/csv/invalid/invalid-amount.csv").toAbsolutePath().toString(), 42, "")) {
                Object pipeline = factory.getMethod("create").invoke(null);
                Object cursor = data.getMethod("getCursor").invoke(pipeline);
                util.getMethod("put", cursorType, String.class, Object.class).invoke(null, cursor, "inputPath", input);
                util.getMethod("put", cursorType, String.class, Object.class).invoke(null, cursor, "pain001Xml", "STALE");
                cursorType.getMethod("destroy").invoke(cursor);
                boolean success = input instanceof String s && s.endsWith("/valid/basic.csv");
                if (success) {
                    service.invoke(null, pipeline);
                } else {
                    var failure = assertThrows(InvocationTargetException.class, () -> service.invoke(null, pipeline));
                    assertEquals("com.wm.app.b2b.server.ServiceException", failure.getCause().getClass().getName());
                }
                cursor = data.getMethod("getCursor").invoke(pipeline);
                Object xml = util.getMethod("get", cursorType, String.class).invoke(null, cursor, "pain001Xml");
                cursorType.getMethod("destroy").invoke(cursor);
                if (success) {
                    XmlChecks.independentlyValidate((String) xml);
                } else {
                    assertNull(xml, "Failure must remove an earlier XML result");
                }
            }
            for (Object profile : new Object[]{MultiRecordPaymentTest.PROFILE, "flat-default-v1", "unknown", "", " ", 42, null}) {
                Object pipeline = factory.getMethod("create").invoke(null);
                Object cursor = data.getMethod("getCursor").invoke(pipeline);
                String input = Path.of("samples/csv/multirecord/04-fx-and-invoices.csv").toAbsolutePath().toString();
                util.getMethod("put", cursorType, String.class, Object.class).invoke(null, cursor, "inputPath", input);
                util.getMethod("put", cursorType, String.class, Object.class).invoke(null, cursor, "profileId", profile);
                util.getMethod("put", cursorType, String.class, Object.class).invoke(null, cursor, "pain001Xml", "STALE");
                cursorType.getMethod("destroy").invoke(cursor);
                boolean success = MultiRecordPaymentTest.PROFILE.equals(profile);
                if (success) service.invoke(null, pipeline);
                else {
                    var failure = assertThrows(InvocationTargetException.class, () -> service.invoke(null, pipeline));
                    assertEquals("com.wm.app.b2b.server.ServiceException", failure.getCause().getClass().getName());
                }
                cursor = data.getMethod("getCursor").invoke(pipeline);
                Object xml = util.getMethod("get", cursorType, String.class).invoke(null, cursor, "pain001Xml");
                cursorType.getMethod("destroy").invoke(cursor);
                if (success) MultiRecordAssertions.assertFixture(Path.of(input), (String) xml);
                else assertNull(xml, "Profile failures must remove stale XML");
            }
        }
        // Hide Maven's classpath and omit one real runtime dependency at a time.
        for (String missing : List.of("jakarta.xml.bind-api", "commons-csv")) {
            var urls = new java.util.ArrayList<java.net.URL>(List.of(classes.toUri().toURL(), vendor.toUri().toURL(),
                    Path.of("target/file-to-pain001-1.0.0-SNAPSHOT.jar").toAbsolutePath().toUri().toURL()));
            try (var dependencies = Files.list(Path.of("target/dependency"))) {
                for (Path dependency : dependencies.filter(path -> path.toString().endsWith(".jar")
                        && !path.getFileName().toString().startsWith(missing)).toList()) urls.add(dependency.toUri().toURL());
            }
            try (var isolated = new URLClassLoader(urls.toArray(java.net.URL[]::new), ClassLoader.getPlatformClassLoader())) {
                Class<?> data = isolated.loadClass("com.wm.data.IData");
                Class<?> cursorType = isolated.loadClass("com.wm.data.IDataCursor");
                Class<?> util = isolated.loadClass("com.wm.data.IDataUtil");
                Object pipeline = isolated.loadClass("com.wm.data.IDataFactory").getMethod("create").invoke(null);
                Object cursor = data.getMethod("getCursor").invoke(pipeline);
                util.getMethod("put", cursorType, String.class, Object.class).invoke(null, cursor, "inputPath",
                        Path.of("samples/csv/valid/basic.csv").toAbsolutePath().toString());
                util.getMethod("put", cursorType, String.class, Object.class).invoke(null, cursor, "pain001Xml", "STALE");
                cursorType.getMethod("destroy").invoke(cursor);
                var service = isolated.loadClass("example.webmethods.PaymentFileServices").getMethod("convertFile", data);
                var invocation = assertThrows(InvocationTargetException.class, () -> service.invoke(null, pipeline));
                Throwable failure = invocation.getCause();
                assertEquals("com.wm.app.b2b.server.ServiceException", failure.getClass().getName());
                assertTrue(failure.getMessage().startsWith("Payment conversion could not run"));
                assertNotNull(failure.getCause(), "Runtime deployment failures must preserve their underlying cause");
                assertEquals("java.lang.NoClassDefFoundError", failure.getCause().getClass().getName());
                assertTrue(failure.getCause().getMessage().contains(missing.equals("commons-csv")
                        ? "org/apache/commons/csv/CSVFormat" : "jakarta/xml/bind/JAXBException"));
                cursor = data.getMethod("getCursor").invoke(pipeline);
                assertNull(util.getMethod("get", cursorType, String.class).invoke(null, cursor, "pain001Xml"));
                cursorType.getMethod("destroy").invoke(cursor);
            }
        }
    }
}
