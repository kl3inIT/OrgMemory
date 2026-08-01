package com.orgmemory.core.knowledge.asset;

import com.orgmemory.graphrag.model.FloatVector;

/** PostgreSQL vector encoding used by Asset chunk persistence and Retrieval queries. */
public final class PgVectorLiteral {

    private PgVectorLiteral() {}

    public static String from(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 12).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(vector[index]);
        }
        return value.append(']').toString();
    }

    public static FloatVector parse(String encoded) {
        if (encoded == null || encoded.length() < 2
                || encoded.charAt(0) != '['
                || encoded.charAt(encoded.length() - 1) != ']') {
            throw new IllegalArgumentException("invalid pgvector literal");
        }
        String body = encoded.substring(1, encoded.length() - 1);
        if (body.isBlank()) {
            throw new IllegalArgumentException("pgvector literal must not be empty");
        }
        String[] parts = body.split(",", -1);
        float[] values = new float[parts.length];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isBlank()) {
                throw new IllegalArgumentException(
                        "pgvector literal components must not be empty");
            }
            values[index] = Float.parseFloat(parts[index]);
        }
        return new FloatVector(values);
    }
}
