package com.orgmemory.core.assetregistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AssetTypeProfileRegistry {

    private final Map<AssetType, AssetTypeProfile> profiles;

    public AssetTypeProfileRegistry() {
        EnumMap<AssetType, AssetTypeProfile> registered = new EnumMap<>(AssetType.class);
        register(registered, new AssetTypeProfile(AssetType.PROMPT_TEMPLATE, Set.of("1")));
        register(registered, new AssetTypeProfile(AssetType.WORK_INSTRUCTION, Set.of("1")));
        register(registered, new AssetTypeProfile(AssetType.CAPABILITY_PACK, Set.of("1")));
        profiles = Map.copyOf(registered);
    }

    public AssetTypeProfile require(AssetType type) {
        AssetTypeProfile profile = profiles.get(type);
        if (profile == null) {
            throw new IllegalArgumentException("Asset type is not enabled");
        }
        return profile;
    }

    public Set<AssetType> enabledTypes() {
        return profiles.keySet();
    }

    private static void register(
            Map<AssetType, AssetTypeProfile> profiles, AssetTypeProfile profile) {
        if (profiles.put(profile.type(), profile) != null) {
            throw new IllegalStateException("Duplicate Asset type profile " + profile.type());
        }
    }
}
