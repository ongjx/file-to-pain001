package com.example.payments;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.payments.XmlChecks.*;

class PaymentFileRunnerIT {
    private static final Path PROJECT = Path.of("").toAbsolutePath();
    private static final Path JAR = PROJECT.resolve("target/file-to-pain001-1.0.0-SNAPSHOT-runner.jar");
    private static final Path JAVA = Path.of(System.getProperty("java.home"), "bin", "java");
    @TempDir Path temporary;

    static Stream<Path> valid() throws Exception { return PaymentFileConverterTest.valid(); }
    static Stream<Path> invalid() throws Exception { return PaymentFileConverterTest.invalid(); }

    @ParameterizedTest(name = "packaged valid: {0}")
    @MethodSource("valid")
    void packagedRunnerResolvesSchemaAndDependenciesOutsideProject(Path fixture) throws Exception {
        List<String> args = new ArrayList<>(List.of(fixture.toAbsolutePath().toString()));
        if (PaymentFileConverterTest.layout(fixture).name().equals("CAMEL_CASE")) {
            args.addAll(List.of("--layout", "camel-case"));
        }
        Result result = run(args.toArray(String[]::new));
        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().startsWith("<?xml"));
        assertEquals("", result.err());
        independentlyValidate(result.out());
    }

    @ParameterizedTest(name = "packaged invalid: {0}")
    @MethodSource("invalid")
    void packagedFailuresLeaveNoOutput(Path fixture) throws Exception {
        Path output = temporary.resolve("must-not-exist.xml");
        Result result = run(fixture.toAbsolutePath().toString(), "--output", output.toString());
        assertEquals(4, result.status(), result.err());
        assertEquals("", result.out());
        assertFalse(result.err().isBlank());
        assertFalse(Files.exists(output));
    }

    @ParameterizedTest(name = "packaged multi-record: {0}")
    @MethodSource("com.example.payments.MultiRecordPaymentTest#valid")
    void packagedMultiRecordPreservesSourceValues(Path fixture) throws Exception {
        Result result = run(fixture.toAbsolutePath().toString(), "--profile", MultiRecordPaymentTest.PROFILE);
        assertEquals(0, result.status(), result.err());
        assertEquals("", result.err());
        assertTrue(result.out().startsWith("<?xml"));
        MultiRecordAssertions.assertFixture(fixture, result.out());
    }

    @ParameterizedTest(name = "packaged multi-record rejection: {0}")
    @MethodSource("com.example.payments.MultiRecordPaymentTest#invalid")
    void packagedMultiRecordFailuresLeaveNoOutput(Path fixture, String reason) throws Exception {
        byte[] original = Files.readAllBytes(fixture);
        Path output = temporary.resolve("must-not-exist.xml");
        Result result = run(fixture.toAbsolutePath().toString(), "--profile", MultiRecordPaymentTest.PROFILE,
                "--output", output.toString());
        assertEquals(4, result.status(), result.err());
        assertEquals("", result.out());
        assertTrue(result.err().contains(reason), result.err());
        assertFalse(Files.exists(output));
        assertArrayEquals(original, Files.readAllBytes(fixture));
    }

    @Test
    void explicitProfilesAndLegacySelectorsHaveClearBehavior() throws Exception {
        String basic = PROJECT.resolve("samples/csv/valid/basic.csv").toString();
        for (String profile : List.of("default", "flat-default-v1")) {
            assertEquals(0, run(basic, "--profile", profile).status());
        }
        String camel = PROJECT.resolve("samples/csv/valid/alternate-headers.csv").toString();
        assertEquals(0, run(camel, "--profile", "flat-camel-case-v1").status());
        for (String[] options : List.of(
                new String[]{"--profile", "unknown"}, new String[]{"--profile", ""},
                new String[]{"--profile", " "}, new String[]{"--profile", "flat-default-v1", "--profile", "flat-default-v1"},
                new String[]{"--profile", "flat-default-v1", "--layout", "default"},
                new String[]{"--layout", "default", "--profile", "flat-default-v1"})) {
            List<String> args = new ArrayList<>(List.of(basic));
            args.addAll(List.of(options));
            args.addAll(List.of("--output", temporary.resolve("invalid-options.xml").toString()));
            Result result = run(args.toArray(String[]::new));
            assertEquals(2, result.status(), result.err());
            assertEquals("", result.out());
            assertFalse(Files.exists(temporary.resolve("invalid-options.xml")));
        }
        assertEquals(4, run(basic, "--profile", MultiRecordPaymentTest.PROFILE).status());
        assertEquals(3, run("missing.csv", "--profile", MultiRecordPaymentTest.PROFILE).status());
        assertEquals(2, run("data.xlsx", "--profile", MultiRecordPaymentTest.PROFILE).status());
        Path multi = PROJECT.resolve("samples/csv/multirecord/04-fx-and-invoices.csv");
        for (String layout : List.of("default", "camel-case")) assertEquals(4, run(multi.toString(), "--layout", layout).status());
        Path output = temporary.resolve("multi.xml");
        Result first = run(multi.toString(), "--profile", MultiRecordPaymentTest.PROFILE, "--output", output.toString());
        assertEquals(0, first.status(), first.err());
        assertEquals("", first.out());
        String xml = Files.readString(output);
        MultiRecordAssertions.assertFixture(multi, xml);
        assertEquals(5, run(multi.toString(), "--profile", MultiRecordPaymentTest.PROFILE, "--output", output.toString()).status());
        assertEquals(xml, Files.readString(output));
    }

    @Test
    void outputIsCreatedWithoutChangingInputOrOverwritingExistingFile() throws Exception {
        Path input = PROJECT.resolve("samples/csv/valid/basic.csv");
        byte[] before = Files.readAllBytes(input);
        Path output = temporary.resolve("result.xml");
        Result first = run(input.toString(), "--output", output.toString());
        assertEquals(0, first.status(), first.err());
        assertEquals("", first.out());
        String xml = Files.readString(output);
        independentlyValidate(xml);
        assertEquals(5, run(input.toString(), "--output", output.toString()).status());
        assertEquals(xml, Files.readString(output));
        assertArrayEquals(before, Files.readAllBytes(input));
        assertEquals(5, run(input.toString(), "--output", input.toString()).status());
        assertArrayEquals(before, Files.readAllBytes(input));
        assertEquals(5, run(input.toString(), "--output", temporary.resolve("missing-parent/out.xml").toString()).status());
    }

    @Test
    void relativePathsAndPathsWithSpacesWork() throws Exception {
        Path input = temporary.resolve("mock payments.csv");
        Files.copy(PROJECT.resolve("samples/csv/valid/basic.csv"), input);
        Result result = run("mock payments.csv", "--output", "mock result.xml");
        assertEquals(0, result.status(), result.err());
        independentlyValidate(Files.readString(temporary.resolve("mock result.xml")));
    }

    @Test
    void usageInputAndFormatHaveDistinctStatuses() throws Exception {
        assertEquals(2, run().status());
        assertEquals(2, run("data.xlsx").status());
        assertEquals(2, run("data.csv", "--layout", "unknown").status());
        assertEquals(2, run("data.csv", "--output").status());
        assertEquals(2, run("data.csv", "--layout", "default", "--layout", "default").status());
        assertEquals(3, run("missing.csv").status());
        Files.createDirectory(temporary.resolve("directory.csv"));
        assertEquals(3, run("directory.csv").status());
    }

    @Test
    void normalLibraryDistributionReturnsXmlWithoutTheRunnerJar() throws Exception {
        Path source = temporary.resolve("LibraryCaller.java");
        Files.writeString(source, """
                public class LibraryCaller {
                    public static void main(String[] args) throws Exception {
                        System.out.print(new com.example.payments.conversion.PaymentFileConverter().convertToXml(args[0]));
                    }
                }
                """);
        Path library = PROJECT.resolve("target/file-to-pain001-1.0.0-SNAPSHOT.jar");
        Process compile = new ProcessBuilder(JAVA.resolveSibling("javac").toString(), "--release", "21",
                "-classpath", library.toString(), source.toString()).redirectErrorStream(true).start();
        assertTrue(compile.waitFor(15, TimeUnit.SECONDS));
        assertEquals(0, compile.exitValue(), new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        String classpath = library + java.io.File.pathSeparator + PROJECT.resolve("target/dependency/*")
                + java.io.File.pathSeparator + temporary;
        Path stdout = temporary.resolve("library-output.xml");
        Path stderr = temporary.resolve("library-error.txt");
        Process caller = new ProcessBuilder(JAVA.toString(), "-cp", classpath, "LibraryCaller",
                PROJECT.resolve("samples/csv/valid/basic.csv").toString()).directory(temporary.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        assertTrue(caller.waitFor(15, TimeUnit.SECONDS));
        assertEquals(0, caller.exitValue(), Files.readString(stderr));
        assertEquals("", Files.readString(stderr));
        independentlyValidate(Files.readString(stdout));
    }

    @Test
    void packagedClassesTargetJava21() throws Exception {
        try (var jar = new java.util.jar.JarFile(JAR.toFile());
             var data = new java.io.DataInputStream(jar.getInputStream(jar.getJarEntry("com/example/payments/conversion/PaymentFileConverter.class")))) {
            assertEquals(0xCAFEBABE, data.readInt());
            data.readUnsignedShort();
            assertEquals(65, data.readUnsignedShort());
            assertNotNull(jar.getJarEntry("xsd/pain.001.001.09.xsd"));
            assertNotNull(jar.getJarEntry("META-INF/services/jakarta.xml.bind.JAXBContextFactory"));
            assertNull(jar.getJarEntry("com/wm/data/IData.class"));
        }
        assertTrue(Files.isDirectory(PROJECT.resolve("target/dependency")));
    }

    private Result run(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(JAVA.toString(), "-jar", JAR.toString()));
        command.addAll(List.of(args));
        Path stdout = temporary.resolve("stdout.txt");
        Path stderr = temporary.resolve("stderr.txt");
        Process process = new ProcessBuilder(command).directory(temporary.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("Runner did not finish within " + Duration.ofSeconds(15));
        }
        return new Result(process.exitValue(), Files.readString(stdout, StandardCharsets.UTF_8), Files.readString(stderr, StandardCharsets.UTF_8));
    }

    private record Result(int status, String out, String err) { }
}
