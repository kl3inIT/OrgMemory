package com.orgmemory.core.assetregistry.kernel;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetValidationTests {

    @Test
    void coordinatesRejectValuesLongerThanTheirDatabaseColumns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Asset(
                        UUID.randomUUID(),
                        AssetType.PROMPT_TEMPLATE,
                        "a".repeat(129),
                        "valid-slug",
                        UUID.randomUUID(),
                        UUID.randomUUID()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Asset(
                        UUID.randomUUID(),
                        AssetType.PROMPT_TEMPLATE,
                        "valid.namespace",
                        "a".repeat(129),
                        UUID.randomUUID(),
                        UUID.randomUUID()));
    }
}
