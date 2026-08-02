package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.profile.AssetPayloadProfile;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AssetTypeProfileRegistry {

    private final Map<AssetType, AssetTypeProfile> profiles;

    public AssetTypeProfileRegistry(List<AssetPayloadProfile> payloadProfiles) {
        EnumMap<AssetType, AssetTypeProfile> registered = new EnumMap<>(AssetType.class);
        for (AssetPayloadProfile payloadProfile : payloadProfiles) {
            register(
                    registered,
                    new AssetTypeProfile(
                            payloadProfile.type(),
                            payloadProfile.schemaVersions(),
                            payloadProfile));
        }
        if (!registered.keySet().equals(Set.of(AssetType.values()))) {
            throw new IllegalStateException("Every enabled Asset type requires one payload profile");
        }
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
