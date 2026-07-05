package com.orgmemory.api.capability;

import java.util.UUID;

record UsageResponse(UUID assetId, long usageCount) {
}
