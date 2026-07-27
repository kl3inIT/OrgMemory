package com.orgmemory.api.scim;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class ScimErrorWriterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesValidJsonForEveryControlCharacter() throws Exception {
        StringBuilder detail = new StringBuilder("quote=\" slash=\\");
        for (char character = 0; character < 0x20; character++) {
            detail.append(character);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        ScimErrorWriter.write(response, 400, detail.toString());

        var body = assertDoesNotThrow(() -> objectMapper.readTree(
                response.getContentAsString()));
        assertEquals(detail.toString(), body.get("detail").asText());
        assertEquals("400", body.get("status").asText());
    }
}
