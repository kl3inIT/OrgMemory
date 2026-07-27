package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

/**
 * Raised when a crawl batch declares a staging-contract payload version this build does
 * not understand. Fail closed: an unrecognized shape is never guessed at or partially
 * applied.
 */
public class UnsupportedConnectorPayloadException extends BusinessException {

    public UnsupportedConnectorPayloadException(String message) {
        super(
                BusinessErrorCategory.VALIDATION,
                "connector.payload-unsupported",
                message);
    }
}
