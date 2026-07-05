package com.orgmemory.api.capability;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record BackupOwnerRequest(@NotNull UUID backupOwnerUserId) {
}
