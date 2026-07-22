package com.orgmemory.core.knowledge;

final class PgVectorLiteral {

    private PgVectorLiteral() {}

    static String from(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 12).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(vector[index]);
        }
        return value.append(']').toString();
    }
}
