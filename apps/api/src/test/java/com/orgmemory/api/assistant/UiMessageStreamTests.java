package com.orgmemory.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class UiMessageStreamTests {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void emitsAiSdkUiMessageFramesInOrder() {
        List<String> data = UiMessageStream.encode(
                        Flux.just(
                                new AssistantStreamPart.StartStep(),
                                new AssistantStreamPart.SourceUrl(
                                        "citation-1", "https://example.test/handbook", "Employee Handbook"),
                                new AssistantStreamPart.TextStart("answer"),
                                new AssistantStreamPart.TextDelta("answer", "Sixty days. [1]"),
                                new AssistantStreamPart.TextEnd("answer"),
                                new AssistantStreamPart.FinishStep()),
                        json,
                        Duration.ofHours(1),
                        Duration.ofMinutes(1))
                .map(ServerSentEvent::data)
                .collectList()
                .block();

        assertThat(data).isNotNull();
        assertThat(data.getFirst()).contains("\"type\":\"start\"").contains("\"messageId\":");
        assertThat(data.subList(1, data.size())).containsExactly(
                "{\"type\":\"start-step\"}",
                "{\"type\":\"source-url\",\"sourceId\":\"citation-1\",\"url\":\"https://example.test/handbook\",\"title\":\"Employee Handbook\"}",
                "{\"type\":\"text-start\",\"id\":\"answer\"}",
                "{\"type\":\"text-delta\",\"id\":\"answer\",\"delta\":\"Sixty days. [1]\"}",
                "{\"type\":\"text-end\",\"id\":\"answer\"}",
                "{\"type\":\"finish-step\"}",
                "{\"type\":\"finish\",\"finishReason\":\"stop\"}",
                "[DONE]");
    }

    @Test
    void heartbeatIsAnSseComment() {
        StepVerifier.withVirtualTime(() -> UiMessageStream.encode(
                        Flux.never(), json, Duration.ofSeconds(15), Duration.ofMinutes(1)))
                .assertNext(event -> assertThat(event.data()).contains("\"type\":\"start\""))
                .thenAwait(Duration.ofSeconds(15))
                .assertNext(event -> {
                    assertThat(event.data()).isNull();
                    assertThat(event.comment()).isEqualTo("ping");
                })
                .thenCancel()
                .verify();
    }

    @Test
    void timeoutEmitsAbortAndDoneWithoutFinish() {
        Flux<ServerSentEvent<String>> dataEvents = UiMessageStream.encode(
                        Flux.<AssistantStreamPart>just(new AssistantStreamPart.StartStep())
                                .concatWith(Flux.never()),
                        json,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(20))
                .filter(event -> event.data() != null);

        StepVerifier.withVirtualTime(() -> dataEvents)
                .assertNext(event -> assertThat(event.data()).contains("\"type\":\"start\""))
                .assertNext(event -> assertThat(event.data()).isEqualTo("{\"type\":\"start-step\"}"))
                .thenAwait(Duration.ofSeconds(20))
                .assertNext(event -> assertThat(event.data())
                        .isEqualTo("{\"type\":\"abort\",\"reason\":\"Assistant turn timed out.\"}"))
                .assertNext(event -> assertThat(event.data()).isEqualTo("[DONE]"))
                .verifyComplete();
    }

    @Test
    void providerFailureEmitsOpaqueErrorAndDone() {
        List<String> data = UiMessageStream.encode(
                        Flux.error(new IllegalStateException("provider secret")),
                        json,
                        Duration.ofHours(1),
                        Duration.ofMinutes(1))
                .map(ServerSentEvent::data)
                .collectList()
                .block();

        assertThat(data).containsExactly(
                data.getFirst(),
                "{\"type\":\"error\",\"errorText\":\"The assistant stream failed.\"}",
                "[DONE]");
        assertThat(data.getFirst()).contains("\"type\":\"start\"");
        assertThat(data).allMatch(frame -> !frame.contains("provider secret"));
    }
}
