package com.orgmemory.connectors.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Covers the three things about Slack's Web API that are easy to get wrong and expensive to
 * discover in production: a refusal arrives inside a 200 response, a collection is only finished
 * when the next cursor is empty, and a rate limit is an instruction to wait rather than a
 * failure. No network is involved; the responses are the shapes Slack documents.
 */
class SlackWebApiClientTests {

    private static final String TOKEN = "xoxb-not-a-real-token";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private List<Long> sleeps;
    private SlackRateLimitGate gate;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sleeps = new ArrayList<>();
        // Records the wait instead of taking it, and removes the jitter so the arithmetic is
        // assertable; the jitter itself is proved separately.
        gate = new SlackRateLimitGate(fixedRandom(0.0), sleeps::add);
    }

    @Test
    void collectsEveryPageUntilTheCursorRunsOut() {
        server.expect(requestTo("https://slack.com/api/conversations.list"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        """
                        {"ok":true,"channels":[{"id":"C1"},{"id":"C2"}],
                         "response_metadata":{"next_cursor":"page-2"}}
                        """,
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://slack.com/api/conversations.list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cursor=page-2")))
                .andRespond(withSuccess(
                        """
                        {"ok":true,"channels":[{"id":"C3"}],"response_metadata":{"next_cursor":""}}
                        """,
                        MediaType.APPLICATION_JSON));

        List<JsonNode> channels = client().collectPaged(
                "conversations.list", Map.of("types", "public_channel"), "channels");

        assertEquals(List.of("C1", "C2", "C3"), channels.stream().map(c -> c.path("id").asString()).toList());
        server.verify();
    }

    @Test
    void treatsAnAbsentCursorAsTheLastPage() {
        server.expect(requestTo("https://slack.com/api/users.list"))
                .andRespond(withSuccess("{\"ok\":true,\"members\":[{\"id\":\"U1\"}]}", MediaType.APPLICATION_JSON));

        List<JsonNode> members = client().collectPaged("users.list", Map.of(), "members");

        assertEquals(1, members.size());
        server.verify();
    }

    @Test
    void readsARefusalOutOfASuccessfulResponse() {
        server.expect(requestTo("https://slack.com/api/conversations.history"))
                .andRespond(withSuccess("{\"ok\":false,\"error\":\"missing_scope\"}", MediaType.APPLICATION_JSON));

        SlackApiException refused = assertThrows(
                SlackApiException.class, () -> client().call("conversations.history", Map.of("channel", "C1")));

        assertEquals("missing_scope", refused.errorCode());
        assertTrue(refused.getMessage().contains("conversations.history"));
        assertFalse(refused.getMessage().contains(TOKEN), "a failure must not carry the token");
    }

    @Test
    void waitsOutARateLimitAndRetriesTheCall() {
        server.expect(requestTo("https://slack.com/api/conversations.members"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "7"));
        server.expect(requestTo("https://slack.com/api/conversations.members"))
                .andRespond(withSuccess("{\"ok\":true,\"members\":[\"U1\"]}", MediaType.APPLICATION_JSON));

        JsonNode body = client().call("conversations.members", Map.of("channel", "C1"));

        assertTrue(body.path("ok").asBoolean());
        assertEquals(1, sleeps.size(), "the retry waits exactly once");
        assertTrue(sleeps.getFirst() >= 7000, () -> "waited only " + sleeps.getFirst() + "ms for a 7s limit");
        server.verify();
    }

    @Test
    void backsOffBrieflyWhenSlackOmitsTheRetryAfterHeader() {
        server.expect(requestTo("https://slack.com/api/users.list"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://slack.com/api/users.list"))
                .andRespond(withSuccess("{\"ok\":true,\"members\":[]}", MediaType.APPLICATION_JSON));

        client().call("users.list", Map.of());

        assertEquals(1, sleeps.size());
        assertTrue(sleeps.getFirst() > 0, "an unreadable limit still waits");
    }

    @Test
    void givesUpWhenTheRateLimitOutlastsTheRetryBudget() {
        for (int attempt = 0; attempt < 4; attempt++) {
            server.expect(requestTo("https://slack.com/api/conversations.history"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "1"));
        }

        SlackApiException exhausted = assertThrows(
                SlackApiException.class, () -> client().call("conversations.history", Map.of("channel", "C1")));

        assertEquals("ratelimited", exhausted.errorCode());
        server.verify();
    }

    @Test
    void appliesAWaitEarnedByOneCallToTheNext() {
        SlackRateLimitGate shared = new SlackRateLimitGate(fixedRandom(0.0), sleeps::add);
        shared.closeFor(Duration.ofSeconds(5));

        shared.awaitOpen();

        assertEquals(1, sleeps.size(), "a request issued under a live limit waits before it is sent");
        assertTrue(sleeps.getFirst() > 0);
    }

    @Test
    void spreadsResumingRequestsWithJitter() {
        List<Long> jittered = new ArrayList<>();
        SlackRateLimitGate spread = new SlackRateLimitGate(fixedRandom(1.0), jittered::add);
        spread.closeFor(Duration.ofSeconds(10));

        spread.awaitOpen();

        assertTrue(
                jittered.getFirst() > 10_000,
                () -> "a 10s limit should resume later than 10s, waited " + jittered.getFirst());
    }

    private SlackWebApiClient client() {
        return new SlackWebApiClient(builder, TOKEN, new tools.jackson.databind.ObjectMapper(), gate);
    }

    /** A generator that always returns the same draw, so jitter is a decision rather than noise. */
    private static RandomGenerator fixedRandom(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
