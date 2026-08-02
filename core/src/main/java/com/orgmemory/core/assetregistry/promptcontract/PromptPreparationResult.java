package com.orgmemory.core.assetregistry.promptcontract;

import java.util.List;
import java.util.Map;
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
        outputContract = Map.copyOf(outputContract);
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
            allowedValues = List.copyOf(allowedValues);
        }
    }
}
