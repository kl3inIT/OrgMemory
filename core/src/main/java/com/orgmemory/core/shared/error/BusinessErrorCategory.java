package com.orgmemory.core.shared.error;

/**
 * Transport-neutral failure semantics exposed by an application use case.
 *
 * <p>Delivery adapters map these categories to their own protocol. Core does
 * not depend on HTTP status codes or Spring Web types.
 */
public enum BusinessErrorCategory {
    VALIDATION,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    UNAVAILABLE
}
