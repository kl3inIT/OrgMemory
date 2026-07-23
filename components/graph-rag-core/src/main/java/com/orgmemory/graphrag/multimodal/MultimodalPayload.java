package com.orgmemory.graphrag.multimodal;

import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.util.Objects;

/** Parser-neutral content supplied to a modality-specific analyzer. */
public sealed interface MultimodalPayload
        permits MultimodalPayload.Image, MultimodalPayload.Table, MultimodalPayload.Equation {

    String contentSha256();

    record Image(MultimodalBinaryArtifact artifact) implements MultimodalPayload {

        public Image {
            Objects.requireNonNull(artifact, "artifact");
        }

        @Override
        public String contentSha256() {
            return artifact.contentSha256();
        }
    }

    record Table(String format, String content, Integer rows, Integer columns)
            implements MultimodalPayload {

        public Table {
            format = requireText(format, "format");
            content = requireText(content, "content");
            if ((rows == null) != (columns == null)) {
                throw new IllegalArgumentException(
                        "table dimensions must both be present or both absent");
            }
            if (rows != null && rows <= 0 || columns != null && columns <= 0) {
                throw new IllegalArgumentException(
                        "table dimensions must be positive when present");
            }
        }

        @Override
        public String contentSha256() {
            return ResolvedDocumentProcessingProfile.sha256(format + '\n' + content);
        }
    }

    record Equation(String expression) implements MultimodalPayload {

        public Equation {
            expression = requireText(expression, "expression");
        }

        @Override
        public String contentSha256() {
            return ResolvedDocumentProcessingProfile.sha256(expression);
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
