package com.example.payments.cli;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.conversion.PaymentFileConverter;
import com.example.payments.input.csv.CsvLayout;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Optional local runner. Integration Server calls the library API instead. */
public final class PaymentFileRunner {
    private PaymentFileRunner() { }

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
    }

    private static int run(String[] args) {
        try {
            Options options = options(args);
            PaymentFileConverter converter = new PaymentFileConverter();
            String xml = converter.toXml(converter.convert(options.input(), options.layout()));
            try {
                if (options.output() == null) {
                    System.out.write(xml.getBytes(StandardCharsets.UTF_8));
                    System.out.flush();
                    if (System.out.checkError()) {
                        throw new IOException("stdout write failed");
                    }
                } else {
                    writeNew(options.output(), xml);
                }
            } catch (IOException e) {
                System.err.println("Output failure: destination must be new and writable with an existing parent directory.");
                return 5;
            }
            return 0;
        } catch (PaymentConversionException e) {
            System.err.println(e.getMessage());
            if (e.kind() == PaymentConversionException.Kind.USAGE) {
                System.err.println("Usage: <input.csv> [--layout default|camel-case] [--output new-file.xml]");
            }
            return switch (e.kind()) {
                case USAGE -> 2;
                case INPUT -> 3;
                case VALIDATION -> 4;
                case PROCESSING -> 5;
            };
        } catch (InvalidPathException e) {
            System.err.println("Invalid filesystem path in command arguments.");
            return 2;
        } catch (RuntimeException | LinkageError e) {
            System.err.println("Processing failure: check the Java runtime and application dependencies.");
            return 5;
        }
    }

    private static Options options(String[] args) throws PaymentConversionException {
        if (args.length == 0 || args[0].isBlank() || args[0].startsWith("--") || args.length % 2 == 0) {
            throw usage();
        }
        Path input = Path.of(args[0]);
        CsvLayout layout = CsvLayout.DEFAULT;
        Path output = null;
        boolean layoutSeen = false;
        for (int i = 1; i < args.length; i += 2) {
            if (args[i + 1].isBlank() || args[i + 1].startsWith("--")) {
                throw usage();
            }
            switch (args[i]) {
                case "--layout" -> {
                    if (layoutSeen) {
                        throw usage();
                    }
                    layout = CsvLayout.fromOption(args[i + 1]);
                    layoutSeen = true;
                }
                case "--output" -> {
                    if (output != null) {
                        throw usage();
                    }
                    output = Path.of(args[i + 1]);
                }
                default -> throw usage();
            }
        }
        return new Options(input, layout, output);
    }

    private static void writeNew(Path path, String xml) throws IOException {
        // Open separately: a CREATE_NEW failure must never trigger deletion of an existing file.
        OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (stream) {
            stream.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static PaymentConversionException usage() {
        return new PaymentConversionException(PaymentConversionException.Kind.USAGE, "Expected one input path and supported option/value pairs.");
    }

    private record Options(Path input, CsvLayout layout, Path output) { }
}
