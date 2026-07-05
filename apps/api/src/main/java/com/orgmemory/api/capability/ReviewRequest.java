package com.orgmemory.api.capability;

import java.util.UUID;

record ReviewRequest(UUID reviewerUserId, String comment) {
}
