package com.orgmemory.scim.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.unboundid.scim2.common.messages.PatchOpType;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.utils.JsonUtils;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PingPatchConformanceTests {

    @ParameterizedTest
    @MethodSource("patchFixtures")
    void parsesProviderPatchFixturesWithoutRegex(String fixture) {
        PatchRequest request = JsonUtils.getObjectReader()
                .forType(PatchRequest.class)
                .readValue(FixtureSupport.text(fixture));

        assertFalse(request.getOperations().isEmpty());
    }

    @Test
    void acceptsCaseInsensitiveOperationsAndMembershipValuePaths() {
        PatchRequest entra = read("/scim/patch/entra-user.json");
        PatchRequest okta = read("/scim/patch/okta-group.json");

        assertEquals(PatchOpType.REPLACE, entra.getOperations().getFirst().getOpType());
        assertEquals("emails[type eq \"work\"].value",
                entra.getOperations().getFirst().getPath().toString());
        assertEquals(PatchOpType.REMOVE, okta.getOperations().getFirst().getOpType());
        assertEquals("members[value eq \"2819c223-7f76-453a-919d-413861904646\"]",
                okta.getOperations().getFirst().getPath().toString());
    }

    @Test
    void preservesPathlessPatchAsAnExplicitRootOperation() {
        PatchRequest request = read("/scim/patch/pathless-user.json");

        assertNull(request.getOperations().getFirst().getPath());
        assertEquals(PatchOpType.REPLACE, request.getOperations().getFirst().getOpType());
    }

    private static PatchRequest read(String fixture) {
        return JsonUtils.getObjectReader()
                .forType(PatchRequest.class)
                .readValue(FixtureSupport.text(fixture));
    }

    private static Stream<String> patchFixtures() {
        return Stream.of(
                "/scim/patch/entra-user.json",
                "/scim/patch/okta-group.json",
                "/scim/patch/pathless-user.json");
    }
}
