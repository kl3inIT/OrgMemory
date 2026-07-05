package com.orgmemory.api.ai;

import java.io.IOException;

@FunctionalInterface
interface FrameWriter {
    void write(String frame) throws IOException;
}
