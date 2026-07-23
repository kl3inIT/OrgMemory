package com.orgmemory.integrations.graphrag.springai;

import com.orgmemory.graphrag.multimodal.MultimodalBinaryArtifact;
import org.springframework.core.io.Resource;

/** Resolves an opaque core artifact reference without exposing its storage location. */
@FunctionalInterface
public interface MultimodalBinaryResourceLoader {

    Resource load(MultimodalBinaryArtifact artifact);
}
