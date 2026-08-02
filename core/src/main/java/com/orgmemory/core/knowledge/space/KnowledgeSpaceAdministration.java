package com.orgmemory.core.knowledge.space;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** What an administrator sees and changes about a Knowledge Space. */
public final class KnowledgeSpaceAdministration {

    private KnowledgeSpaceAdministration() {
    }

    /**
     * One stored grant, reported as it is stored.
     *
     * <p>{@code subject} is the raw OpenFGA user reference rather than a
     * {@link KnowledgeSpaceSubject}, because grants written at bootstrap use shapes the write API
     * does not offer — {@code organization:<id>#knowledge_contributor} among them. Reporting only
     * the shapes this service can author would hide the rest.
     */
    public record Grant(String relation, String subject, boolean effective) {

        public Grant {
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(subject, "subject");
        }
    }

    /**
     * A space with the grants currently on it.
     *
     * <p>{@code grantsComplete} is false when the space held more tuples than the listing was
     * willing to read, so a partial list is never presented as the whole of who has access.
     */
    public record Space(
            UUID id,
            String key,
            String name,
            KnowledgeSpaceAudienceMode audienceMode,
            long audienceVersion,
            UUID departmentId,
            boolean active,
            List<Grant> grants,
            boolean grantsComplete,
            String policyVersion) {

        public Space {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(audienceMode, "audienceMode");
            if (audienceVersion < 1) {
                throw new IllegalArgumentException("audienceVersion must be positive");
            }
            grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
            Objects.requireNonNull(policyVersion, "policyVersion");
        }
    }
}
