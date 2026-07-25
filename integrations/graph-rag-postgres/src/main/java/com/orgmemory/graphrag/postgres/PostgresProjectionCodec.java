package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.model.FloatVector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class PostgresProjectionCodec {

    private PostgresProjectionCodec() {}

    static String encodeMap(Map<String, String> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + ":" + encode(entry.getValue()))
                .collect(Collectors.joining("\n"));
    }

    static String searchableValues(Map<String, String> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.joining(" "));
    }

    static Map<String, String> decodeMap(String encoded) {
        if (encoded.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        encoded.lines().forEach(line -> {
            int separator = line.indexOf(':');
            if (separator < 0) {
                throw new IllegalArgumentException("invalid encoded map");
            }
            values.put(
                    decode(line.substring(0, separator)),
                    decode(line.substring(separator + 1)));
        });
        return Map.copyOf(values);
    }

    static String encodeList(List<String> values) {
        return values.stream().map(PostgresProjectionCodec::encode)
                .collect(Collectors.joining("\n"));
    }

    static List<String> decodeList(String encoded) {
        if (encoded.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        encoded.lines().map(PostgresProjectionCodec::decode).forEach(values::add);
        return List.copyOf(values);
    }

    static String encodeVector(FloatVector vector) {
        return IntStream.range(0, vector.dimensions())
                .mapToObj(index -> Float.toString(vector.valueAt(index)))
                .collect(Collectors.joining(",", "[", "]"));
    }

    static FloatVector decodeVector(String encoded) {
        String body = encoded.substring(1, encoded.length() - 1);
        String[] parts = body.split(",");
        float[] values = new float[parts.length];
        for (int index = 0; index < parts.length; index++) {
            values[index] = Float.parseFloat(parts[index]);
        }
        return new FloatVector(values);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(
                Base64.getUrlDecoder().decode(value),
                StandardCharsets.UTF_8);
    }
}
