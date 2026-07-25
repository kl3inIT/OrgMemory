package com.orgmemory.core.assetregistry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record PromptTemplateSpec(
        String objective,
        String audience,
        List<String> useWhen,
        List<String> doNotUseWhen,
        String textTemplate,
        List<Message> messages,
        List<Variable> variables,
        Map<String, Object> outputContract,
        DataPolicy dataPolicy,
        List<String> compatibility,
        List<String> knowledgeRequirements,
        List<EvaluationCase> evaluationCases,
        String knownLimitations) {

    private static final int MAX_TEMPLATE_CHARACTERS = 30_000;

    public PromptTemplateSpec {
        objective = required(objective, "objective", 2_000);
        audience = required(audience, "audience", 1_000);
        useWhen = strings(useWhen, "useWhen", 20, 1_000);
        doNotUseWhen = strings(doNotUseWhen, "doNotUseWhen", 20, 1_000);
        textTemplate = optional(textTemplate, MAX_TEMPLATE_CHARACTERS);
        messages = messages == null ? List.of() : List.copyOf(messages);
        variables = variables == null ? List.of() : List.copyOf(variables);
        outputContract = outputContract == null ? Map.of() : Map.copyOf(outputContract);
        dataPolicy = dataPolicy == null ? DataPolicy.defaults() : dataPolicy;
        compatibility = strings(compatibility, "compatibility", 20, 256);
        knowledgeRequirements = strings(
                knowledgeRequirements, "knowledgeRequirements", 20, 1_000);
        evaluationCases = evaluationCases == null ? List.of() : List.copyOf(evaluationCases);
        knownLimitations = optional(knownLimitations, 4_000);

        boolean hasText = !textTemplate.isBlank();
        boolean hasMessages = !messages.isEmpty();
        if (hasText == hasMessages) {
            throw new IllegalArgumentException(
                    "Prompt Template requires exactly one of textTemplate or messages");
        }
        if (messages.size() > 20
                || messages.stream().mapToInt(message -> message.content().length()).sum()
                        > MAX_TEMPLATE_CHARACTERS) {
            throw new IllegalArgumentException("Prompt messages exceed the bounded template size");
        }
        if (hasMessages && messages.stream().noneMatch(message -> message.role() == MessageRole.USER)) {
            throw new IllegalArgumentException("Prompt messages require at least one USER message");
        }
        if (variables.size() > 50) {
            throw new IllegalArgumentException("Prompt Template supports at most 50 variables");
        }
        Set<String> names = new HashSet<>();
        for (Variable variable : variables) {
            if (!names.add(variable.name())) {
                throw new IllegalArgumentException("Prompt variable names must be unique");
            }
        }
        if (evaluationCases.size() > 10) {
            throw new IllegalArgumentException("Prompt Template supports at most 10 evaluation cases");
        }
        Set<String> caseNames = new HashSet<>();
        for (EvaluationCase evaluationCase : evaluationCases) {
            if (!caseNames.add(evaluationCase.name())) {
                throw new IllegalArgumentException("Prompt evaluation case names must be unique");
            }
        }
    }

    public enum MessageRole {
        SYSTEM,
        USER
    }

    public enum VariableType {
        STRING,
        INTEGER,
        NUMBER,
        BOOLEAN,
        STRING_LIST
    }

    public record Message(MessageRole role, String content) {

        public Message {
            role = Objects.requireNonNull(role, "role");
            content = required(content, "message.content", MAX_TEMPLATE_CHARACTERS);
        }
    }

    public record Variable(
            String name,
            VariableType type,
            boolean required,
            Object defaultValue,
            boolean sensitive,
            String pattern,
            List<String> allowedValues) {

        private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

        public Variable {
            name = PromptTemplateSpec.required(name, "variable.name", 64);
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                        "Prompt variable names must use lowercase snake_case");
            }
            type = Objects.requireNonNull(type, "variable.type");
            pattern = optional(pattern, 512);
            if (!pattern.isBlank()) {
                Pattern.compile(pattern);
            }
            allowedValues = strings(allowedValues, "variable.allowedValues", 100, 1_000);
        }
    }

    public record DataPolicy(boolean retainRawVariables, boolean retainRawOutput) {

        public static DataPolicy defaults() {
            return new DataPolicy(false, false);
        }
    }

    public record EvaluationCase(
            String name,
            Map<String, Object> variables,
            List<String> expectedContains,
            List<String> forbiddenContains) {

        public EvaluationCase {
            name = required(name, "evaluationCase.name", 128);
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            expectedContains = strings(
                    expectedContains, "evaluationCase.expectedContains", 20, 1_000);
            forbiddenContains = strings(
                    forbiddenContains, "evaluationCase.forbiddenContains", 20, 1_000);
            if (expectedContains.isEmpty() && forbiddenContains.isEmpty()) {
                throw new IllegalArgumentException(
                        "An evaluation case requires at least one assertion");
            }
        }
    }

    private static List<String> strings(
            List<String> values, String field, int maximumItems, int maximumLength) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > maximumItems) {
            throw new IllegalArgumentException(field + " has too many items");
        }
        return values.stream()
                .map(value -> required(value, field, maximumLength))
                .toList();
    }

    private static String optional(String value, int maximumLength) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("Value exceeds maximum length");
        }
        return normalized;
    }

    private static String required(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is blank or exceeds maximum length");
        }
        return normalized;
    }
}
