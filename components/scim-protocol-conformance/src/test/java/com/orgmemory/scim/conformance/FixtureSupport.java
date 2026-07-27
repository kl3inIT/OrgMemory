package com.orgmemory.scim.conformance;

import com.unboundid.scim2.common.utils.JsonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;

final class FixtureSupport {

    private FixtureSupport() {}

    static String text(String path) {
        try (InputStream input = FixtureSupport.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing fixture: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read fixture: " + path, exception);
        }
    }

    static JsonNode json(String path) {
        return JsonUtils.getObjectReader().readTree(text(path));
    }
}
