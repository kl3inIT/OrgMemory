package com.orgmemory.core.organization;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class OrgMemoryAccessDeniedException extends BusinessException {

    public OrgMemoryAccessDeniedException(String message) {
        super(BusinessErrorCategory.FORBIDDEN, "access.denied", message);
    }
}
