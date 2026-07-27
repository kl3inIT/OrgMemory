package com.orgmemory.core.assetregistry;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public final class SkillPackageValidationException extends BusinessException {

    SkillPackageValidationException(String message) {
        super(
                BusinessErrorCategory.VALIDATION,
                "skill.package-invalid",
                message);
    }

    SkillPackageValidationException(String message, Throwable cause) {
        super(
                BusinessErrorCategory.VALIDATION,
                "skill.package-invalid",
                message,
                cause);
    }
}
