package com.orgmemory.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.shared.error.BusinessConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantModelAuthorityServiceTests {

    private final AiRouteResolver routes = mock(AiRouteResolver.class);
    private final AiGatewayAdministrationService administration =
            mock(AiGatewayAdministrationService.class);
    private final AiAssistantModelActivationRepository activations =
            mock(AiAssistantModelActivationRepository.class);
    private final AssistantModelAuthorityService service =
            new AssistantModelAuthorityService(routes, administration, activations);

    @Test
    void deploymentDefaultIsSyntheticAndInvalidatesWhenOrganizationAuthorityChanges() {
        UUID organizationId = UUID.randomUUID();
        AiRoute deployment = new AiRoute("deployment-openai", "gpt-default");
        when(routes.reference(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(deployment);
        when(administration.route(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.empty());

        List<AssistantModelChoice> choices = service.choices(organizationId);
        AssistantModelRouteAuthority authority = service.authorize(organizationId, null);

        assertEquals(1, choices.size());
        assertNull(choices.getFirst().activationId());
        assertEquals("gpt-default", choices.getFirst().modelId());
        assertInstanceOf(DefaultAssistantModelRouteAuthority.class, authority);

        when(administration.route(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.of(routeOverride(UUID.randomUUID(), UUID.randomUUID(), 1, null)));
        assertThrows(BusinessConflictException.class, () -> service.revalidate(authority));
    }

    @Test
    void catalogSelectionIsBoundToTheExactRouteIdentityAndVersion() {
        UUID organizationId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AiRouteOverrideView current = routeOverride(routeId, profileId, 7, null);
        AiAssistantModelActivation activation = new AiAssistantModelActivation(
                organizationId,
                profileId,
                "claude-sonnet-4-5",
                "Claude Sonnet",
                actorId);
        when(routes.reference(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(current.route());
        when(administration.route(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.of(current));
        when(administration.require(organizationId, profileId))
                .thenReturn(profile(profileId));
        when(administration.assistantModels(organizationId, profileId))
                .thenReturn(List.of(activation.view()));
        when(activations.findByIdAndOrganizationIdAndEnabledTrue(
                        activation.getId(), organizationId))
                .thenReturn(Optional.of(activation));

        List<AssistantModelChoice> choices = service.choices(organizationId);
        AssistantModelRouteAuthority authority = service.authorize(
                organizationId,
                activation.getId());

        assertEquals(List.of("gpt-default", "claude-sonnet-4-5"), choices.stream()
                .map(AssistantModelChoice::modelId)
                .toList());
        assertEquals(
                new AiRoute("openai-main", "claude-sonnet-4-5"),
                service.revalidate(authority));

        when(administration.route(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.of(routeOverride(routeId, profileId, 8, null)));
        assertThrows(BusinessConflictException.class, () -> service.revalidate(authority));
    }

    @Test
    void explicitNoneOffersCatalogAndPropagatesTheGovernedReasoningPolicy() {
        UUID organizationId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AiRouteOverrideView current = routeOverride(
                routeId,
                profileId,
                3,
                OpenAiReasoningEffort.NONE);
        AiAssistantModelActivation activation = new AiAssistantModelActivation(
                organizationId,
                profileId,
                "gpt-5.6-luna",
                "GPT-5.6 Luna",
                actorId);
        when(routes.reference(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(current.route());
        when(administration.route(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.of(current));
        when(administration.require(organizationId, profileId))
                .thenReturn(profile(profileId));
        when(administration.assistantModels(organizationId, profileId))
                .thenReturn(List.of(activation.view()));
        when(activations.findByIdAndOrganizationIdAndEnabledTrue(
                        activation.getId(), organizationId))
                .thenReturn(Optional.of(activation));

        List<AssistantModelChoice> choices = service.choices(organizationId);
        AssistantModelRouteAuthority authority = service.authorize(
                organizationId,
                activation.getId());

        assertEquals(List.of("gpt-default", "gpt-5.6-luna"), choices.stream()
                .map(AssistantModelChoice::modelId)
                .toList());
        assertEquals(
                new AiRoute("openai-main", "gpt-5.6-luna", OpenAiReasoningEffort.NONE),
                service.revalidate(authority));
    }

    @Test
    void unsupportedExplicitReasoningKeepsOnlyTheDefaultAndRejectsAlternateActivations() {
        UUID organizationId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        AiRouteOverrideView current = routeOverride(
                UUID.randomUUID(),
                profileId,
                3,
                OpenAiReasoningEffort.HIGH);
        when(routes.reference(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(current.route());
        when(administration.route(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.of(current));
        when(administration.require(organizationId, profileId))
                .thenReturn(profile(profileId));

        assertEquals(1, service.choices(organizationId).size());
        assertThrows(
                BusinessConflictException.class,
                () -> service.authorize(organizationId, UUID.randomUUID()));
    }

    private static AiRouteOverrideView routeOverride(
            UUID id,
            UUID profileId,
            long version,
            OpenAiReasoningEffort reasoning) {
        return new AiRouteOverrideView(
                id,
                AiWorkload.ASSISTANT_CHAT,
                profileId,
                "openai-main",
                "gpt-default",
                reasoning,
                version,
                UUID.randomUUID(),
                Instant.parse("2026-08-04T10:00:00Z"));
    }

    private static AiGatewayProfileView profile(UUID profileId) {
        Instant now = Instant.parse("2026-08-04T10:00:00Z");
        return new AiGatewayProfileView(
                profileId,
                "openai-main",
                "Organization AI",
                AiGatewayPreset.OPENAI,
                AiGatewayCategory.DIRECT_PROVIDER,
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                true,
                "https://api.openai.com/v1",
                60,
                true,
                1,
                true,
                UUID.randomUUID(),
                now,
                UUID.randomUUID(),
                UUID.randomUUID(),
                now,
                now);
    }
}
