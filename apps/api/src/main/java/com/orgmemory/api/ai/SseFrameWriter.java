package com.orgmemory.api.ai;

import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

record SseFrameWriter(SseEmitter sseEmitter) implements FrameWriter {

    @Override
    public void write(String frame) throws IOException {
        synchronized (sseEmitter) {
            sseEmitter.send(SseEmitter.event().data(frame));
        }
    }
}
