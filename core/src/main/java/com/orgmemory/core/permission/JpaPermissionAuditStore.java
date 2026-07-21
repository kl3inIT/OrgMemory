package com.orgmemory.core.permission;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
class JpaPermissionAuditStore implements PermissionAuditStore {

    private final EntityManager entityManager;

    JpaPermissionAuditStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(PermissionAuditEvent event) {
        entityManager.persist(event);
    }
}
