package com.orgmemory.core.organization;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false)
    private String name;

    protected Organization() {
    }

    public Organization(String name) {
        super(UUID.randomUUID());
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
