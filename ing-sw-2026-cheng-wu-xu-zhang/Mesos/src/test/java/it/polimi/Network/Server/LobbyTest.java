package it.polimi.Network.Server;

import it.polimi.Network.Common.SerializedUpdate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the refactored multi-match Lobby.
 */
class LobbyTest {

    @Test
    void testCreateRoom() {
        Lobby lobby = new Lobby(false);
        assertTrue(lobby.createRoom(101, null, "Alice", 4));
        assertFalse(lobby.createRoom(101, null, 2), "Should not create room with duplicate ID");
        assertNotNull(lobby.getRoom(101));
        assertEquals("Alice", lobby.getRoom(101).getCreatorNickname());
        assertEquals(4, lobby.getRoom(101).getRequiredPlayers());
    }

    @Test
    void testJoinRoom() {
        Lobby lobby = new Lobby(false);
        StubConnection handler = new StubConnection();
        
        // Try join non-existent room
        assertFalse(lobby.joinRoom(99, "Alice", "1234", handler));
        
        lobby.createRoom(1, null, 4);
        assertTrue(lobby.joinRoom(1, "Alice", "1111", handler));
        assertEquals(lobby.getRoom(1), lobby.getRoomForPlayer("Alice"));
        
        // Duplicate nick in same room
        assertFalse(lobby.joinRoom(1, "Alice", "2222", new StubConnection()));
    }

    @Test
    void testReconnectRoom() {
        Lobby lobby = new Lobby(false);
        StubConnection initialHandler = new StubConnection();
        lobby.createRoom(1, null, 4);
        lobby.joinRoom(1, "Alice", "secret_pin", initialHandler);
        
        // Simulate disconnect
        lobby.disconnectPlayer("Alice");
        
        // Reconnect attempt
        StubConnection newHandler = new StubConnection();
        assertTrue(lobby.reconnectRoom(1, "Alice", "secret_pin", newHandler));
        
        // Reconnect with wrong PIN
        lobby.disconnectPlayer("Alice");
        assertFalse(lobby.reconnectRoom(1, "Alice", "wrong", new StubConnection()));
        
        // Reconnect to wrong room
        assertFalse(lobby.reconnectRoom(99, "Alice", "secret_pin", new StubConnection()));
    }

    @Test
    void testBroadcastToRoom() {
        Lobby lobby = new Lobby(false);
        StubConnection alice = new StubConnection();
        StubConnection bob = new StubConnection();
        StubConnection charlie = new StubConnection();
        
        lobby.createRoom(1, null, 4);
        lobby.createRoom(2, null, 4);
        
        lobby.joinRoom(1, "Alice", "p1", alice);
        lobby.joinRoom(1, "Bob", "p2", bob);
        lobby.joinRoom(2, "Charlie", "p3", charlie);
        
        lobby.broadcastToRoom(1, "TEST", "Message for Room 1");
        
        assertTrue(alice.hasReceived("TEST", "Message for Room 1"));
        assertTrue(bob.hasReceived("TEST", "Message for Room 1"));
        assertFalse(charlie.hasReceived("TEST", "Message for Room 1"), "Charlie is in Room 2 and shouldn't hear Room 1");
    }

    @Test
    void testBroadcastToRoomExceptSkipsSender() {
        Lobby lobby = new Lobby(false);
        StubConnection alice = new StubConnection();
        StubConnection bob = new StubConnection();

        lobby.createRoom(1, null, 2);
        lobby.joinRoom(1, "Alice", "p1", alice);
        lobby.joinRoom(1, "Bob", "p2", bob);

        lobby.broadcastToRoomExcept(1, "Bob", "NOTIFICATION", "Bob connected to the room.");

        assertTrue(alice.hasReceived("NOTIFICATION", "Bob connected to the room."));
        assertFalse(bob.hasReceived("NOTIFICATION", "Bob connected to the room."));
    }

    @Test
    void testSendToPlayerTargetsOnlyRequestedPlayer() {
        Lobby lobby = new Lobby(false);
        StubConnection alice = new StubConnection();
        StubConnection bob = new StubConnection();
        lobby.createRoom(1, null, 2);
        lobby.joinRoom(1, "Alice", "p1", alice);
        lobby.joinRoom(1, "Bob", "p2", bob);

        lobby.sendToPlayer("Bob", "NOTIFICATION", "Now acting: Bob");

        assertFalse(alice.hasReceived("NOTIFICATION", "Now acting: Bob"));
        assertTrue(bob.hasReceived("NOTIFICATION", "Now acting: Bob"));
    }

    @Test
    void testGlobalBroadcast() {
        Lobby lobby = new Lobby(false);
        StubConnection alice = new StubConnection();
        StubConnection bob = new StubConnection();
        
        lobby.createRoom(1, null, 4);
        lobby.joinRoom(1, "Alice", "p1", alice);
        lobby.joinRoom(1, "Bob", "p2", bob);
        
        lobby.broadcast("GLOBAL", "Server shutting down");
        
        assertTrue(alice.hasReceived("GLOBAL", "Server shutting down"));
        assertTrue(bob.hasReceived("GLOBAL", "Server shutting down"));
    }

    /**
     * Stub implementation of ClientConnection for testing.
     */
    private static class StubConnection implements ClientConnection {
        private final List<SerializedUpdate> received = new ArrayList<>();
        private boolean disconnected = false;

        @Override public long getLastActivity() { return System.currentTimeMillis(); }
        @Override public void disconnect() { disconnected = true; }
        
        @Override
        public void sendToClient(SerializedUpdate update) {
            received.add(update);
        }

        public boolean hasReceived(String type, String content) {
            return received.stream().anyMatch(u -> 
                u.getType().equals(type) && u.getContent().equals(content));
        }
    }
}
