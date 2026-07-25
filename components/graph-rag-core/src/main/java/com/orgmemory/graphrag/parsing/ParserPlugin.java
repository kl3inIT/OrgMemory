package com.orgmemory.graphrag.parsing;

/** Java ServiceLoader extension point for third-party parsers. */
public interface ParserPlugin {

    void register(ParserRegistry registry);
}
