package com.orgmemory.graphrag.multimodal;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Unambiguous UTF-8 length-prefixed framing for content-addressed identities. */
final class MultimodalFingerprintInput {

    private MultimodalFingerprintInput() {}

    static String frame(String... values) {
        StringBuilder framed = new StringBuilder();
        for (String value : Objects.requireNonNull(values, "values")) {
            String required = Objects.requireNonNull(value, "fingerprint value");
            framed.append(required.getBytes(StandardCharsets.UTF_8).length)
                    .append(':')
                    .append(required);
        }
        return framed.toString();
    }
}
