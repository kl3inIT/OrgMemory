package com.orgmemory.scim.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.directory.scim.spec.filter.Filter;
import org.apache.directory.scim.spec.patch.PatchOperationPath;
import org.junit.jupiter.api.Test;

class ApacheScimpleComparisonTests {

    @Test
    void releasedParserBuildsAFilterAstForTheCommonSubset() throws Exception {
        Filter filter = new Filter("userName eq \"bjensen\" and active eq true");

        assertNotNull(filter.getExpression());
        assertEquals("userName EQ \"bjensen\" AND active EQ true", filter.toString());
    }

    @Test
    void releasedParserHandlesCaseInsensitiveMembershipPatchPaths() throws Exception {
        PatchOperationPath path =
                PatchOperationPath.fromString("members[value EQ \"2819c223-7f76-453a-919d-413861904646\"]");

        assertEquals("members[value EQ \"2819c223-7f76-453a-919d-413861904646\"]", path.toString());
    }
}
