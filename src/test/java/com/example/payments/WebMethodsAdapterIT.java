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
        }
    }
}
