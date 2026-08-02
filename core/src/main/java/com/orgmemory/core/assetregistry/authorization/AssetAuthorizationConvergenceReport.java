package com.orgmemory.core.assetregistry.authorization;

public record AssetAuthorizationConvergenceReport(
        int candidates,
        int applied,
        int failed) {

    public AssetAuthorizationConvergenceReport {
        if (candidates < 0 || applied < 0 || failed < 0 || applied + failed > candidates) {
            throw new IllegalArgumentException("Invalid Asset authorization convergence counters");
        }
    }
}
