package com.orgmemory.core.assetregistry.promptcontract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PromptPreparationResult(
        UUID assetId,
        UUID releaseId,
        String releaseDigest,
        String objective,
        String audience,
        List<Variable> variables,
        Map<String, Object> outputContract,
        List<String> knowledgeRequirements,
        String knownLimitations) {

    public PromptPreparationResult {
        variables = List.copyOf(variables);
        outputContract = immutableJsonMap(outputContract);
        knowledgeRequirements = List.copyOf(knowledgeRequirements);
    }

    public enum VariableType {
        STRING,
        INTEGER,
        NUMBER,
        BOOLEAN,
        STRING_LIST
    }

    public record Variable(
            String name,
            VariableType type,
            boolean required,
            Object defaultValue,
            boolean sensitive,
            String pattern,
            List<String> allowedValues) {

        public Variable {
            defaultValue = immutableJsonValue(defaultValue);
            allowedValues = List.copyOf(allowedValues);
        }
    }

    private static Map<String, Object> immutableJsonMap(Map<String, ?> source) {
        Objects.requireNonNull(source, "source");
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "JSON object key"),
                immutableJsonValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableJsonValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                copy.put(stringKey, immutableJsonValue(nested));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(nested -> copy.add(immutableJsonValue(nested)));
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException(
                "Unsupported JSON value type: " + value.getClass().getName());
    }
}
