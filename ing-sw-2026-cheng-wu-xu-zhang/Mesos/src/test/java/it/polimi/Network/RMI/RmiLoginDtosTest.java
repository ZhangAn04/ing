package it.polimi.Network.RMI;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests constructors and accessors for RMI login DTOs.
 */
class RmiLoginDtosTest {

    @Test
    void requestConstructorsAndSetterWork() {
        RmiLoginRequest empty = new RmiLoginRequest();
        empty.setNickname("Alice");
        empty.setType("CREATE_ROOM");
        empty.setRoomID(101);
        empty.setPin("1234");
        
        assertEquals("Alice", empty.getNickname());
        assertEquals("CREATE_ROOM", empty.getType());
        assertEquals(101, empty.getRoomID());
        assertEquals("1234", empty.getPin());

        RmiLoginRequest withValues = new RmiLoginRequest("JOIN_ROOM", "Bob", 202, "abcd");
        assertEquals("Bob", withValues.getNickname());
        assertEquals("JOIN_ROOM", withValues.getType());
        assertEquals(202, withValues.getRoomID());
        assertEquals("abcd", withValues.getPin());
    }

    @Test
    void responseConstructorsAndSettersWork() {
        RmiLoginResponse empty = new RmiLoginResponse();
        empty.setSuccess(true);
        empty.setMessage("ok");

        assertTrue(empty.isSuccess());
        assertEquals("ok", empty.getMessage());

        RmiLoginResponse withValues = new RmiLoginResponse(false, "nope");
        assertFalse(withValues.isSuccess());
        assertEquals("nope", withValues.getMessage());
    }
}
