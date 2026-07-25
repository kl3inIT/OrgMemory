package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;

/** Provider boundary for exact tokenization. */
public interface TextTokenizer {

    ProcessingComponentRef component();

    EncodedText encode(String canonicalText);

    default int count(String canonicalText) {
        return encode(canonicalText).size();
    }
}
