package com.orgmemory.api.ai;

sealed interface Part {

    record TextStart(String id) implements Part {
    }

    record TextDelta(String id, String delta) implements Part {
    }

    record TextEnd(String id) implements Part {
    }
}
