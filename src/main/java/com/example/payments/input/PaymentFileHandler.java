package com.example.payments.input;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.pain001.v09.Document;
import java.nio.file.Path;

/** Each supported format interprets its source directly into the versioned payment model. */
public interface PaymentFileHandler {
    Document read(Path path) throws PaymentConversionException;
}
