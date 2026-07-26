package com.orgmemory.api;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

/**
 * A deliberate API-boundary rejection.
 *
 * <p>Controllers use this instead of relying on a broad
 * {@link IllegalArgumentException} handler, which would incorrectly turn
 * programming and invariant failures into client errors.
 */
public final class ApiRequestException extends BusinessException {

    public ApiRequestException(String publicMessage) {
        super(
                BusinessErrorCategory.VALIDATION,
                "request.invalid",
                publicMessage);
    }
}
