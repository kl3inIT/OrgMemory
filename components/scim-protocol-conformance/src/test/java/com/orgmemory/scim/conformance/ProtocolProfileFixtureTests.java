package com.orgmemory.scim.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ProtocolProfileFixtureTests {

    private static final Set<String> ERROR_TYPES =
            Set.of("invalidFilter", "invalidValue", "mutability");

    @Test
    void supportedUserFixtureDoesNotCarryAuthorizationOrPassword() {
        JsonNode user = FixtureSupport.json("/scim/users/supported-create.json");

        assertFalse(user.has("password"));
        assertFalse(user.has("roles"));
        assertFalse(user.has("entitlements"));
        assertTrue(user.get("emails").get(0).get("primary").asBoolean());
    }

    @Test
    void passwordInputMapsToAnExplicitInvalidValueSnapshot() {
        JsonNode user = FixtureSupport.json("/scim/users/password-rejected.json");
        JsonNode error = FixtureSupport.json("/scim/errors/password-invalid-value.json");

        assertTrue(user.has("password"));
        assertEquals("invalidValue", error.get("scimType").asString());
        assertEquals("400", error.get("status").asString());
        assertTrue(error.get("detail").asString().contains("Disable password mapping"));
    }

    @Test
    void errorSnapshotsUseTheScimErrorSchemaAndApprovedTypes() {
        for (String fixture : Set.of(
                "/scim/errors/invalid-filter.json",
                "/scim/errors/password-invalid-value.json",
                "/scim/errors/read-only.json")) {
            JsonNode error = FixtureSupport.json(fixture);
            assertEquals("urn:ietf:params:scim:api:messages:2.0:Error",
                    error.get("schemas").get(0).asString());
            assertTrue(ERROR_TYPES.contains(error.get("scimType").asString()));
            assertEquals("400", error.get("status").asString());
        }
    }
}
