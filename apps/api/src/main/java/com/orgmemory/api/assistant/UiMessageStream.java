package com.orgmemory.api.assistant;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/** Encodes Assistant parts as AI SDK UI Message Stream v1 SSE frames. */
final class UiMessageStream {

    private UiMessageStream() {
    }

    static Flux<ServerSentEvent<String>> encode(
            Flux<AssistantStreamPart> source,
            UUID messageId,
            ObjectMapper json,
            Duration heartbeatInterval,
            Duration turnTimeout) {
        return Flux.defer(() -> {
            Encoder encoder = new Encoder(json, messageId);
            Flux<ServerSentEvent<String>> live = withHeartbeat(
                    limitDuration(source, turnTimeout).map(encoder::part),
                    heartbeatInterval);
            return Flux.concat(
                            Flux.just(encoder.start()),
                            live,
                            Flux.just(encoder.finish(), encoder.done()))
                    .onErrorResume(AssistantStreamAbortedException.class,
                            error -> Flux.just(encoder.abort(error.getMessage()), encoder.done()))
                    .onErrorResume(error -> Flux.just(
                            encoder.error(AssistantStreamFailures.describe(error)),
                            encoder.done()));
        });
    }

    private static Flux<AssistantStreamPart> limitDuration(
            Flux<AssistantStreamPart> source,
            Duration timeout) {
        AtomicBoolean completed = new AtomicBoolean();
        return source
                .doOnComplete(() -> completed.set(true))
                .take(timeout)
                .concatWith(Flux.defer(() -> completed.get()
                        ? Flux.empty()
                        : Flux.error(new AssistantStreamAbortedException("Assistant turn timed out."))));
    }

    private static Flux<ServerSentEvent<String>> withHeartbeat(
            Flux<ServerSentEvent<String>> source,
            Duration interval) {
        return source.publish(shared -> {
            Flux<ServerSentEvent<String>> heartbeat = Flux.interval(interval)
                    .map(ignored -> ServerSentEvent.<String>builder().comment("ping").build())
                    .takeUntilOther(shared.ignoreElements());
            return Flux.merge(shared, heartbeat);
        });
    }

    private static final class Encoder {

        private final ObjectMapper json;
        private final String messageId;

        private Encoder(ObjectMapper json, UUID messageId) {
            this.json = json;
            this.messageId = messageId.toString();
        }

        ServerSentEvent<String> start() {
            return event(json.writeValueAsString(fields("type", "start", "messageId", messageId)));
        }

        ServerSentEvent<String> part(AssistantStreamPart part) {
            return event(json.writeValueAsString(payload(part)));
        }

        ServerSentEvent<String> finish() {
            return event(json.writeValueAsString(fields("type", "finish", "finishReason", "stop")));
        }

        ServerSentEvent<String> error(String errorText) {
            return event(json.writeValueAsString(
                    fields("type", "error", "errorText", errorText)));
        }

        ServerSentEvent<String> abort(String reason) {
            return event(json.writeValueAsString(fields("type", "abort", "reason", reason)));
        }

        ServerSentEvent<String> done() {
            return event("[DONE]");
        }

        private static Map<String, Object> payload(AssistantStreamPart part) {
            return switch (part) {
                case AssistantStreamPart.StartStep ignored -> fields("type", "start-step");
                case AssistantStreamPart.FinishStep ignored -> fields("type", "finish-step");
                case AssistantStreamPart.Activity activity -> fields(
                        "type", "data-assistantActivity",
                        "data", activityFields(activity),
                        "transient", true);
                case AssistantStreamPart.TextStart text -> fields("type", "text-start", "id", text.id());
                case AssistantStreamPart.TextDelta text -> fields(
                        "type", "text-delta", "id", text.id(), "delta", text.delta());
                case AssistantStreamPart.TextEnd text -> fields("type", "text-end", "id", text.id());
                case AssistantStreamPart.SourceUrl source -> fields(
                        "type", "source-url",
                        "sourceId", source.sourceId(),
                        "url", source.url(),
                        "title", source.title(),
                        "providerMetadata", fields(
                                "orgmemory", fields(
                                        "citationNumber",
                                        source.citationNumber())));
            };
        }

        private static Map<String, Object> activityFields(AssistantStreamPart.Activity activity) {
            Map<String, Object> values = fields(
                    "phase", activity.phase().name(),
                    "state", activity.state().name(),
                    "evidenceCount", activity.evidenceCount());
            if (activity.skillOrdinal() != null) {
                values.put("skillOrdinal", activity.skillOrdinal());
            }
            if (activity.skillTitle() != null) {
                values.put("skillTitle", activity.skillTitle());
            }
            return values;
        }

        private static ServerSentEvent<String> event(String data) {
            return ServerSentEvent.builder(data).build();
        }

        private static Map<String, Object> fields(Object... keyValues) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int index = 0; index < keyValues.length; index += 2) {
                values.put((String) keyValues[index], keyValues[index + 1]);
            }
            return values;
        }
    }

    private static final class AssistantStreamAbortedException extends RuntimeException {

        private AssistantStreamAbortedException(String message) {
            super(message);
        }
    }
}
