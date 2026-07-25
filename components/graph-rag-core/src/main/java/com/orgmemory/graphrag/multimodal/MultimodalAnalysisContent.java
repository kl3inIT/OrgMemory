package com.orgmemory.graphrag.multimodal;

import java.util.Objects;

/** Validated model output for each supported modality. */
public sealed interface MultimodalAnalysisContent
        permits MultimodalAnalysisContent.Image,
                MultimodalAnalysisContent.Table,
                MultimodalAnalysisContent.Equation {

    String name();

    String description();

    record Image(String name, String imageType, String description)
            implements MultimodalAnalysisContent {

        public Image {
            name = requireText(name, "name");
            imageType = requireText(imageType, "imageType");
            description = requireText(description, "description");
        }
    }

    record Table(String name, String description) implements MultimodalAnalysisContent {

        public Table {
            name = requireText(name, "name");
            description = requireText(description, "description");
        }
    }

    record Equation(String name, String equation, String description)
            implements MultimodalAnalysisContent {

        public Equation {
            name = requireText(name, "name");
            equation = requireText(equation, "equation");
            description = requireText(description, "description");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
