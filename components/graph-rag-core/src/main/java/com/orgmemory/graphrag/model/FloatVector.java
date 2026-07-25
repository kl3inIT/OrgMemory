package com.orgmemory.graphrag.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable primitive vector without {@link Float} boxing.
 */
public final class FloatVector {

    private final float[] values;

    public FloatVector(float[] values) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("values must contain only finite numbers");
            }
        }
        this.values = values.clone();
    }

    public int dimensions() {
        return values.length;
    }

    public float valueAt(int index) {
        return values[index];
    }

    public float[] copyValues() {
        return values.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof FloatVector other && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "FloatVector[dimensions=" + values.length + "]";
    }
}
