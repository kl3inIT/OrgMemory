package com.orgmemory.integrations.graphrag.sidecar;

import com.orgmemory.graphrag.multimodal.MultimodalBinaryArtifact;

/** Converts a validated bundle-relative path to an opaque, content-addressed artifact. */
@FunctionalInterface
public interface LightRagArtifactResolver {

    MultimodalBinaryArtifact resolve(
            String itemId,
            String relativePath,
            String mediaType);
}
