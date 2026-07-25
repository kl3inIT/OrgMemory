package com.orgmemory.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTuplePage;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import com.orgmemory.core.knowledge.QueryEmbeddingPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves a Knowledge Space can be created and granted at runtime, which until now required a
 * Flyway migration and an edit to the bootstrap tuple file.
 *
 * <p>The relationship store is a stub that records what was written, because what matters here is
 * that the endpoint authors the right tuples and refuses the wrong ones — not that OpenFGA stores
 * them, which its own adapter tests cover.
 */
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnowledgeSpaceAdminIntegrationTests {

    private static final String ISSUER = "http://localhost:8180/realms/orgmemory";
    private static final String MODEL_ID = "test-model";

    private static final UUID ORG = UUID.fromString("e1000000-0000-4000-8000-000000000001");
    private static final UUID DEPT = UUID.fromString("e1000000-0000-4000-8000-000000000002");
    private static final UUID ADMIN_USER = UUID.fromString("e1000000-0000-4000-8000-000000000003");
    private static final UUID AN_USER = UUID.fromString("e1000000-0000-4000-8000-000000000004");

    private static final UUID OTHER_ORG = UUID.fromString("e2000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_DEPT = UUID.fromString("e2000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_ADMIN = UUID.fromString("e2000000-0000-4000-8000-000000000003");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    RelationshipAuthorizationPort authorization;

    @MockitoBean
    RelationshipTupleWritePort writes;

    @MockitoBean
    RelationshipTupleReconciliationPort tuples;

    @MockitoBean
    QueryEmbeddingPort queryEmbeddings;

    /** Stands in for the relationship store so a listing can read back what a write put in. */
    private final Set<RelationshipTuple> stored = new LinkedHashSet<>();

    @BeforeEach
    void prepare() {
        Integer alreadySeeded = jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE id = ?", Integer.class, ORG);
        if (alreadySeeded == null || alreadySeeded == 0) {
            seed();
        }
        jdbc.update("DELETE FROM knowledge_spaces WHERE organization_id IN (?, ?)", ORG, OTHER_ORG);
        stored.clear();

        when(authorization.check(any())).thenReturn(AuthorizationDecision.allow(MODEL_ID));
        when(tuples.policyVersion()).thenReturn(MODEL_ID);
        when(writes.write(any())).thenAnswer(invocation -> {
            stored.addAll(invocation.getArgument(0, RelationshipTupleWriteRequest.class).tuples());
            return RelationshipTupleWriteResult.applied(MODEL_ID);
        });
        when(tuples.delete(any())).thenAnswer(invocation -> {
            stored.removeAll(invocation.getArgument(0, RelationshipTupleWriteRequest.class).tuples());
            return RelationshipTupleWriteResult.applied(MODEL_ID);
        });
        when(tuples.readObject(anyString(), anyInt(), any())).thenAnswer(invocation -> {
            String object = invocation.getArgument(0);
            return RelationshipTuplePage.resolved(
                    new ArrayList<>(stored.stream()
                            .filter(tuple -> tuple.object().equals(object))
                            .toList()),
                    null,
                    MODEL_ID);
        });
    }

    @Test
    void anAdministratorCreatesASpaceAndSeesItListedWithItsCreatorAsAdministrator() throws Exception {
        String created = mvc.perform(post("/api/admin/knowledge-spaces")
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sales Knowledge\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("sales-knowledge"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID spaceId = UUID.fromString(JsonPath.read(created, "$.id"));

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_spaces WHERE id = ? AND organization_id = ?",
                        Integer.class,
                        spaceId,
                        ORG));
        mvc.perform(get("/api/admin/knowledge-spaces").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + spaceId + "')].grantsComplete").value(true))
                .andExpect(jsonPath(
                                "$[?(@.id == '" + spaceId + "')].grants[?(@.relation == 'administrator')]"
                                        + ".subject")
                        .value("user:" + ADMIN_USER));
    }

    /**
     * Without the structural {@code organization} tuple, {@code org_admin} cannot resolve and the
     * new space is unreachable to every administrator including its creator.
     */
    @Test
    void creationWritesTheStructuralOrganizationLink() throws Exception {
        mvc.perform(post("/api/admin/knowledge-spaces")
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Company Handbook\",\"departmentId\":\"" + DEPT + "\"}"))
                .andExpect(status().isCreated());

        assertEquals(
                Set.of(
                        "organization:" + ORG + " organization",
                        "organizational_unit:" + DEPT + " organizational_unit",
                        "user:" + ADMIN_USER + " administrator"),
                stored.stream()
                        .map(tuple -> tuple.user() + " " + tuple.relation())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void aSecondSpaceWhoseNameDerivesTheSameKeyIsRefused() throws Exception {
        mvc.perform(post("/api/admin/knowledge-spaces")
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sales Knowledge\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/knowledge-spaces")
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  sales knowledge  \"}"))
                .andExpect(status().isConflict());

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_spaces WHERE organization_id = ?",
                        Integer.class,
                        ORG));
    }

    @Test
    void grantingThenRevokingLeavesTheSpaceAsItStarted() throws Exception {
        UUID spaceId = createSpace("Sales Knowledge");

        mvc.perform(post("/api/admin/knowledge-spaces/{id}/grants", spaceId)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relation\":\"viewer\",\"kind\":\"DEPARTMENT\",\"subjectId\":\""
                                + DEPT + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/knowledge-spaces").with(jwtFor(ADMIN_USER)))
                .andExpect(jsonPath(
                                "$[?(@.id == '" + spaceId + "')].grants[?(@.relation == 'viewer')].subject")
                        .value("organizational_unit:" + DEPT + "#member"));

        mvc.perform(delete("/api/admin/knowledge-spaces/{id}/grants", spaceId)
                        .with(jwtFor(ADMIN_USER))
                        .param("relation", "viewer")
                        .param("kind", "DEPARTMENT")
                        .param("subjectId", DEPT.toString()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/knowledge-spaces").with(jwtFor(ADMIN_USER)))
                .andExpect(jsonPath(
                                "$[?(@.id == '" + spaceId + "')].grants[?(@.relation == 'viewer')]")
                        .isEmpty());
    }

    @Test
    void aGrantMustNameAnAclRelationAndASubjectInThisOrganization() throws Exception {
        UUID spaceId = createSpace("Sales Knowledge");

        // 'organization' is structural, written once at creation, never granted.
        mvc.perform(post("/api/admin/knowledge-spaces/{id}/grants", spaceId)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relation\":\"organization\",\"kind\":\"ORGANIZATION\"}"))
                .andExpect(status().isBadRequest());
        // A department of the other tenant must not become a viewer here.
        mvc.perform(post("/api/admin/knowledge-spaces/{id}/grants", spaceId)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relation\":\"viewer\",\"kind\":\"DEPARTMENT\",\"subjectId\":\""
                                + OTHER_DEPT + "\"}"))
                .andExpect(status().isBadRequest());
        // A user grant with no user named is a request error, not a null dereference.
        mvc.perform(post("/api/admin/knowledge-spaces/{id}/grants", spaceId)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relation\":\"viewer\",\"kind\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * The model accepts {@code organizational_unit#manager} for reviewing and {@code #member} for
     * reading, so a form built from the cross product of relations and subjects offers grants that
     * can only be refused. The endpoint publishes the combinations that exist instead.
     */
    @Test
    void theGrantOptionsPublishedAreTheOnesTheEndpointAccepts() throws Exception {
        UUID spaceId = createSpace("Sales Knowledge");

        mvc.perform(get("/api/admin/knowledge-spaces/grant-options").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.relation == 'viewer')].kinds").value(
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.hasItem("ORGANIZATION"))))
                .andExpect(jsonPath("$[?(@.relation == 'reviewer')].kinds").value(
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.hasItem("DEPARTMENT_MANAGERS"))))
                .andExpect(jsonPath("$[?(@.relation == 'administrator')].kinds").value(
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.hasItem("ORGANIZATION")))));

        // Reviewing takes a unit's managers, not its members.
        mvc.perform(post("/api/admin/knowledge-spaces/{id}/grants", spaceId)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relation\":\"reviewer\",\"kind\":\"DEPARTMENT\",\"subjectId\":\""
                                + DEPT + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/knowledge-spaces/{id}/grants", spaceId)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relation\":\"reviewer\",\"kind\":\"DEPARTMENT_MANAGERS\","
                                + "\"subjectId\":\"" + DEPT + "\"}"))
                .andExpect(status().isNoContent());

        assertEquals(
                Set.of("organizational_unit:" + DEPT + "#manager reviewer"),
                stored.stream()
                        .filter(tuple -> "reviewer".equals(tuple.relation()))
                        .map(tuple -> tuple.user() + " " + tuple.relation())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void anAdministratorOfAnotherOrganizationCannotReachThisTenantsSpace() throws Exception {
        UUID spaceId = createSpace("Sales Knowledge");

        mvc.perform(get("/api/admin/knowledge-spaces").with(jwtFor(OTHER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + spaceId + "')]").isEmpty());
        mvc.perform(post("/api/admin/knowledge-spaces/{id}/grants", spaceId)
                        .with(jwtFor(OTHER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relation\":\"viewer\",\"kind\":\"ORGANIZATION\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserWithoutTheOrganizationPermissionIsRefused() throws Exception {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny("NO_RELATIONSHIP", MODEL_ID));

        mvc.perform(get("/api/admin/knowledge-spaces").with(jwtFor(AN_USER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/knowledge-spaces")
                        .with(jwtFor(AN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Shadow Space\"}"))
                .andExpect(status().isForbidden());

        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_spaces WHERE organization_id = ?",
                        Integer.class,
                        ORG));
    }

    private UUID createSpace(String name) throws Exception {
        String created = mvc.perform(post("/api/admin/knowledge-spaces")
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    private static RequestPostProcessor jwtFor(UUID userId) {
        return jwt()
                .jwt(token -> token
                        .claim("iss", ISSUER)
                        .claim("sub", userId.toString())
                        .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private void seed() {
        insertOrganization(ORG, "Space Test Org", DEPT, "Sales");
        insertUser(ADMIN_USER, ORG, DEPT, "admin@spacetest.example", "ADMIN");
        insertUser(AN_USER, ORG, DEPT, "an@spacetest.example", "EMPLOYEE");
        insertOrganization(OTHER_ORG, "Other Tenant", OTHER_DEPT, "Other Sales");
        insertUser(OTHER_ADMIN, OTHER_ORG, OTHER_DEPT, "admin@othertenant.example", "ADMIN");
        List.of(ADMIN_USER, AN_USER, OTHER_ADMIN).forEach(this::linkIdentity);
    }

    private void insertOrganization(UUID id, String name, UUID departmentId, String departmentName) {
        jdbc.update(
                "INSERT INTO organizations (id, name, created_at, updated_at, version) "
                        + "VALUES (?, ?, now(), now(), 0)",
                id,
                name);
        jdbc.update(
                "INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, now(), now(), 0)",
                departmentId,
                id,
                departmentName);
    }

    private void insertUser(UUID id, UUID organizationId, UUID departmentId, String email, String role) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, true, now(), now(), 0)
                """,
                id,
                organizationId,
                departmentId,
                email,
                email,
                role);
    }

    private void linkIdentity(UUID userId) {
        jdbc.update(
                """
                INSERT INTO external_identities (id, app_user_id, issuer, subject, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, now(), now(), 0)
                """,
                UUID.randomUUID(),
                userId,
                ISSUER,
                userId.toString());
    }
}
