package com.orgmemory.core.assetregistry;

public enum AssetRole {
    OWNER("owner"),
    BACKUP_OWNER("backup_owner"),
    STEWARD("steward"),
    VIEWER("viewer"),
    EDITOR("editor"),
    REVIEWER("reviewer"),
    PUBLISHER("publisher");

    private final String relation;

    AssetRole(String relation) {
        this.relation = relation;
    }

    public String relation() {
        return relation;
    }
}
