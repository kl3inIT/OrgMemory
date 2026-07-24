package com.orgmemory.core.authorization;

/**
 * Reads how a permission is composed, for explanation rather than enforcement.
 *
 * <p>Nothing may grant access on the strength of an expansion. Enforcement stays with
 * {@link RelationshipAuthorizationPort} and {@link RelationshipAuthorizationSetPort}, which
 * answer the question the engine is authoritative for. An expansion only describes the
 * relationships behind an answer that was already obtained.
 */
public interface RelationshipExpansionPort {

    RelationshipExpansionResult expand(RelationshipExpansionQuery query);
}
