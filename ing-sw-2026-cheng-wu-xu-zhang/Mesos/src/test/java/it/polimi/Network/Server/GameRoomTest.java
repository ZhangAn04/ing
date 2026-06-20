package it.polimi.Network.Server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GameRoom class, verifying room isolation and player/PIN management.
 */
class GameRoomTest {

    @Test
    void testRoomInitialization() {
        GameRoom room = new GameRoom(101, "secret", "Alice", 4);
        
        assertEquals(101, room.getRoomId());
        assertEquals("Alice", room.getCreatorNickname());
        assertEquals(4, room.getRequiredPlayers());
        assertTrue(room.hasPassword());
        assertTrue(room.checkPassword("secret"));
        assertFalse(room.checkPassword("wrong"));
        assertNotNull(room.getGameModel());
        assertNotNull(room.getController());
    }

    @Test
    void testNoPasswordRoom() {
        GameRoom room = new GameRoom(202, null, 2);
        assertFalse(room.hasPassword());
        assertTrue(room.checkPassword("anything"));
        assertTrue(room.checkPassword(null));
    }

    @Test
    void testAddPlayerSuccess() {
        GameRoom room = new GameRoom(1, null, 2);
        
        assertTrue(room.addPlayer("Alice", "1234"));
        assertTrue(room.containsPlayer("Alice"));
        assertFalse(room.containsPlayer("Bob"));
    }

    @Test
    void testAddDuplicatePlayerFails() {
        GameRoom room = new GameRoom(1, null, 4);
        
        assertTrue(room.addPlayer("Alice", "1111"));
        assertFalse(room.addPlayer("Alice", "2222"), "Should not allow duplicate nicknames in same room");
    }

    @Test
    void testRoomFullFails() {
        GameRoom room = new GameRoom(1, null, 1);
        
        assertTrue(room.addPlayer("Alice", "1234"));
        assertEquals("Alice", room.getCreatorNickname());
        assertFalse(room.addPlayer("Bob", "5678"), "Should not allow players beyond requested player count");
    }

    @Test
    void testValidateReconnection() {
        GameRoom room = new GameRoom(1, null, 4);
        room.addPlayer("Alice", "1234");
        
        assertTrue(room.validateReconnection("Alice", "1234"));
        assertFalse(room.validateReconnection("Alice", "wrong_pin"));
        assertFalse(room.validateReconnection("Bob", "1234"), "Cannot reconnect player not in room");
    }
}
