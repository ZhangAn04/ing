package it.polimi.Network.Common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for {@link SerializedUpdate} network update payloads.
 */
class SerializedUpdateTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verifies that a SerializedUpdate survives a JSON round-trip.
     */
    @Test
    void serializationRoundTripPreservesFields() throws Exception {
        SerializedUpdate original = new SerializedUpdate("STATUS_UPDATE", "game ok", true);

        String json = mapper.writeValueAsString(original);
        SerializedUpdate result = mapper.readValue(json, SerializedUpdate.class);

        assertEquals("STATUS_UPDATE", result.getType());
        assertEquals("game ok", result.getContent());
        assertEquals(true, result.isSuccess());
    }

    /**
     * Verifies that a missing success flag defaults to false.
     */
    @Test
    void missingBooleanFieldDefaultsToFalse() throws Exception {
        String json = "{\"type\":\"LOGIN_RESULT\",\"content\":\"Denied\"}";

        SerializedUpdate result = mapper.readValue(json, SerializedUpdate.class);

        assertEquals("LOGIN_RESULT", result.getType());
        assertEquals("Denied", result.getContent());
        assertFalse(result.isSuccess());
    }
}
