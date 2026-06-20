package it.polimi.Network.Common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ActionMessage serialization.
 * Ensures that objects can be converted to JSON and back without losing data.
 */
class ActionMessageTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verifies that an ActionMessage survives a JSON round-trip without data loss.
     */
    @Test
    void testSerialization() throws Exception {
        // 1. Create an Action object (Model)
        ActionMessage original = new ActionMessage("PICK_CARD", "Marco", 5);

        // 2. Serialize to JSON String (The Pipe)
        String json = mapper.writeValueAsString(original);
        System.out.println("Generated JSON: " + json);

        // 3. Deserialize back to Object (The Server)
        ActionMessage result = mapper.readValue(json, ActionMessage.class);

        // 4. Assertions (The Verification)
        assertEquals(original.getType(), result.getType(), "Action type should match");
        assertEquals(original.getNickname(), result.getNickname(), "Nickname should match");
        assertEquals(original.getValue(), result.getValue(), "Value should match");
    }

    /**
     * Verifies that malformed JSON is rejected by the mapper.
     */
    @Test
    void testInvalidJson() {
        // Test that the mapper throws an exception for broken JSON
        String brokenJson = "{ \"type\": \"MOVE\", \"value\": }"; // Missing value
        
        assertThrows(Exception.class, () -> {
            mapper.readValue(brokenJson, ActionMessage.class);
        }, "Should throw an exception for malformed JSON");
    }

    /**
     * Verifies that missing optional fields fall back to default values.
     */
    @Test
    void testMissingFields() throws Exception {
        // Test JSON with only one field
        String partialJson = "{\"type\": \"CHAT\"}";
        
        ActionMessage result = mapper.readValue(partialJson, ActionMessage.class);
        
        assertEquals("CHAT", result.getType());
        assertNull(result.getNickname(), "Missing string fields should be null");
        assertEquals(0, result.getValue(), "Missing int fields should be default (0)");
    }

    /**
     * Verifies that toString includes all core fields.
     */
    @Test
    void toStringContainsCoreFields() {
        ActionMessage message = new ActionMessage("CHAT", "Alice", 9);

        String text = message.toString();

        assertTrue(text.contains("type='CHAT'"));
        assertTrue(text.contains("nickname='Alice'"));
        assertTrue(text.contains("value=9"));
    }

    @Test
    void testFullSerialization() throws Exception {
        // 1. Create a full ActionMessage
        ActionMessage original = new ActionMessage("JOIN_ROOM", "Luca", 0, 101, "abcd");

        // 2. Round-trip
        String json = mapper.writeValueAsString(original);
        ActionMessage result = mapper.readValue(json, ActionMessage.class);

        // 3. Verify all fields
        assertEquals("JOIN_ROOM", result.getType());
        assertEquals("Luca", result.getNickname());
        assertEquals(101, result.getRoomID());
        assertEquals("abcd", result.getPin());
    }

    /**
     * Verifies that toString includes new room and pin fields.
     */
    @Test
    void toStringContainsNewFields() {
        ActionMessage message = new ActionMessage("CREATE", "Admin", 0, 555, "pin1");

        String text = message.toString();

        assertTrue(text.contains("roomID=555"));
        assertTrue(text.contains("pin='pin1'"));
    }

    /**
     * Verifies that integer boundary values survive JSON round-trip.
     */
    @Test
    void testIntegerBoundarySerialization() throws Exception {
        ActionMessage original = new ActionMessage("BOUNDARY", "Edge", Integer.MAX_VALUE);

        String json = mapper.writeValueAsString(original);
        ActionMessage result = mapper.readValue(json, ActionMessage.class);

        assertEquals(Integer.MAX_VALUE, result.getValue());
    }
}
