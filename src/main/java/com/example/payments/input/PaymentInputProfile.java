package com.example.payments.input;

import com.example.payments.input.csv.CsvLayout;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable contract; initial profiles use strict ISO dates and dot decimals. */
public record PaymentInputProfile(String id, Structure structure, Set<String> extensions,
        Map<String, String> headers, Map<String, List<String>> records, boolean preserveSourceBatches) {
    public enum Structure { FLAT_CSV, MULTIRECORD_CSV }
    public PaymentInputProfile {
        extensions = Set.copyOf(extensions);
        headers = Map.copyOf(headers);
        records = records.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
    }
    public static PaymentInputProfile flat(String id, CsvLayout layout) {
        Map<String, String> headers = CsvLayout.DEFAULT.headers().stream()
                .collect(Collectors.toUnmodifiableMap(field -> field, layout::header));
        return new PaymentInputProfile(id, Structure.FLAT_CSV, Set.of("csv"), headers, Map.of(), false);
    }
}
