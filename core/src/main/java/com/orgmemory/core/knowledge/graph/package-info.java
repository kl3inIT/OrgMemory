/**
 * Knowledge graph indexing, processing profiles, exploration, curation, and export.
 *
 * <p>Asset, Source Ledger, ACL, Space, Retrieval evidence, and embedding-profile state crosses
 * owned query, verifier, or registry boundaries instead of persistence and implementation types.
 * The closed boundary exposes only root-package Graph contracts and declares every outgoing
 * application-module dependency explicitly.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
            "ai",
            "authorization",
            "knowledge.acl",
            "knowledge.asset",
            "knowledge::evidence",
            "knowledge.retrieval",
            "knowledge.sourceledger",
            "knowledge.space",
            "organization",
            "permission",
            "shared",
            "shared::error"
        })
package com.orgmemory.core.knowledge.graph;
