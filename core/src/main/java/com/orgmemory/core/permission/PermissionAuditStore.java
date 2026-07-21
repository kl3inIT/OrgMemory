package com.orgmemory.core.permission;

interface PermissionAuditStore {

    void append(PermissionAuditEvent event);
}
