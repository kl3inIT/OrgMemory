package com.orgmemory.api.ai;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

final class UiMessageStream {

    private UiMessageStream() {
    }

    static void pipe(Flux<Part> source, SseEmitter emitter, ObjectMapper objectMapper) {
        UiMessageStreamEmitter encoder = new UiMessageStreamEmitter(new SseFrameWriter(emitter), objectMapper);

        Disposable subscription = source.subscribe(
                encoder::write,
                ignored -> {
                    encoder.error("The assistant stream failed.");
                    encoder.done();
                    emitter.complete();
                },
                () -> {
                    encoder.finish();
                    encoder.done();
                    emitter.complete();
                });

        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            subscription.dispose();
            emitter.complete();
        });
        emitter.onError(ignored -> subscription.dispose());
    }
}
