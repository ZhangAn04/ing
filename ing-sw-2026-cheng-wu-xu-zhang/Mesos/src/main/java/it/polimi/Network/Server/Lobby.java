package it.polimi.Network.Server;

import it.polimi.Network.Common.SerializedUpdate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A thread-safe registry that manages game rooms and active client connections.
 * <p>
 * The {@code Lobby} acts as the grand central station for the multi-match architecture.
 * Its primary responsibilities include:
 * <ul>
 *     <li>Managing multiple concurrent {@link GameRoom} instances.</li>
 *     <li>Handling player room creation, joining, and secure reconnections via PIN.</li>
 *     <li>Maintaining active {@link ClientConnection} references.</li>
 *     <li>Monitoring connection health via a background task that disconnects timed-out clients.</li>
 * </ul>
 */
public class Lobby {
    private static final long TIMEOUT_THRESHOLD = 20000;

    /** Maps a Room ID to the corresponding GameRoom object. */
    private final Map<Integer, GameRoom> activeRooms = new ConcurrentHashMap<>();
    
    /** Maps a player's nickname to the Room ID they are currently in. */
    private final Map<String, Integer> playerRooms = new ConcurrentHashMap<>();

    /** Maps a player's nickname to their active network connection. */
    private final Map<String, ClientConnection> activeConnections = new ConcurrentHashMap<>();

    private final ScheduledExecutorService monitor;

    /**
     * Constructs a new Lobby and starts the background connection monitoring task.
     */
    public Lobby() {
        this(true);
    }

    /**
     * Constructs a new Lobby with optional timeout monitor startup.
     *
     * @param startMonitor true to start background timeout checks, false otherwise.
     */
    public Lobby(boolean startMonitor) {
        if (startMonitor) {
            monitor = Executors.newSingleThreadScheduledExecutor();
            monitor.scheduleAtFixedRate(this::checkTimeouts, 5, 5, TimeUnit.SECONDS);
        } else {
            monitor = null;
        }
    }

    /**
     * Stops the background monitor thread.
     */
    public void shutdown() {
        if (monitor != null) {
            monitor.shutdownNow();
        }
    }

    /**
     * Periodically checks all active handlers for inactivity and disconnects those exceeding the threshold.
     */
    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ClientConnection> entry : activeConnections.entrySet()) {
            ClientConnection handler = entry.getValue();
            if (now - handler.getLastActivity() > TIMEOUT_THRESHOLD) {
                System.out.println("[MONITOR] Client " + entry.getKey() + " timed out. Forcing disconnection.");
                handler.disconnect();
                // We keep the player in the GameRoom (they just go offline).
                activeConnections.remove(entry.getKey());
            }
        }
    }

    /**
     * Creates a new game room.
     *
     * @param roomId     The requested room ID.
     * @param password   An optional password for the room.
     * @param requiredPlayers The number of players requested for this match.
     * @return true if created successfully, false if the room ID already exists.
     */
    public synchronized boolean createRoom(int roomId, String password, int requiredPlayers) {
        return createRoom(roomId, password, null, requiredPlayers);
    }

    /**
     * Creates a new game room.
     *
     * @param roomId          The requested room ID.
     * @param password        An optional password for the room.
     * @param creatorNickname The nickname of the room creator.
     * @param requiredPlayers The number of players requested for this match.
     * @return true if created successfully, false if the room ID already exists.
     */
    public synchronized boolean createRoom(int roomId, String password, String creatorNickname, int requiredPlayers) {
        if (activeRooms.containsKey(roomId)) {
            return false;
        }
        activeRooms.put(roomId, new GameRoom(roomId, password, creatorNickname, requiredPlayers));
        System.out.println("[LOBBY] Created Room " + roomId + " (Players: " + requiredPlayers + ")");
        return true;
    }

    /**
     * Attempts to join an existing game room.
     *
     * @param roomId   The target room ID.
     * @param nickname The player's nickname.
     * @param pin      The player's secret PIN.
     * @param handler  The active client connection.
     * @return true if successfully joined, false otherwise.
     */
    public synchronized boolean joinRoom(int roomId, String nickname, String pin, ClientConnection handler) {
        GameRoom room = activeRooms.get(roomId);
        if (room == null) return false;

        // If player is already in this room, they should use reconnectRoom instead.
        if (room.containsPlayer(nickname)) return false;

        if (room.addPlayer(nickname, pin)) {
            playerRooms.put(nickname, roomId);
            activeConnections.put(nickname, handler);
            System.out.println("[LOBBY] " + nickname + " joined Room " + roomId);
            return true;
        }
        return false;
    }

    /**
     * Attempts to reconnect a previously disconnected player.
     *
     * @param roomId   The target room ID.
     * @param nickname The player's nickname.
     * @param pin      The secret PIN for validation.
     * @param handler  The new client connection.
     * @return true if successfully reconnected, false otherwise (e.g. wrong PIN or wrong room).
     */
    public synchronized boolean reconnectRoom(int roomId, String nickname, String pin, ClientConnection handler) {
        GameRoom room = activeRooms.get(roomId);
        if (room == null) return false;

        if (room.validateReconnection(nickname, pin)) {
            activeConnections.put(nickname, handler);
            System.out.println("[LOBBY] " + nickname + " successfully reconnected to Room " + roomId);
            return true;
        }
        return false;
    }

    /**
     * Retrieves a GameRoom by its ID.
     *
     * @param roomId The target room ID.
     * @return The GameRoom, or null if it doesn't exist.
     */
    public GameRoom getRoom(int roomId) {
        return activeRooms.get(roomId);
    }

    /**
     * Retrieves the GameRoom a specific player is currently in.
     *
     * @param nickname The player's nickname.
     * @return The GameRoom, or null if the player is not in any room.
     */
    public GameRoom getRoomForPlayer(String nickname) {
        Integer roomId = playerRooms.get(nickname);
        if (roomId == null) return null;
        return activeRooms.get(roomId);
    }

    /**
     * Unregisters a player's connection (they go offline).
     * The player remains in the GameRoom for potential reconnection.
     *
     * @param nickname The player's nickname.
     */
    public synchronized void disconnectPlayer(String nickname) {
        activeConnections.remove(nickname);
        System.out.println("[LOBBY] " + nickname + " connection dropped. (Retained in room for reconnect)");
    }

    /**
     * Broadcasts a targeted message to all clients in a specific room.
     *
     * @param roomId  The target room ID.
     * @param type    The message type (e.g., "STATUS_UPDATE").
     * @param message The textual content of the message.
     */
    public void broadcastToRoom(int roomId, String type, String message) {
        SerializedUpdate update = new SerializedUpdate(type, message, true);
        GameRoom room = activeRooms.get(roomId);
        if (room == null) return;

        for (String nickname : playerRooms.keySet()) {
            if (playerRooms.get(nickname) == roomId) {
                ClientConnection handler = activeConnections.get(nickname);
                if (handler != null) {
                    handler.sendToClient(update);
                }
            }
        }
    }

    /**
     * Broadcasts a room update to every active player except the command sender.
     *
     * @param roomId room receiving the update
     * @param excludedNickname nickname that must not receive the update
     * @param type serialized update type
     * @param message update payload
     */
    public void broadcastToRoomExcept(int roomId, String excludedNickname, String type, String message) {
        SerializedUpdate update = new SerializedUpdate(type, message, true);
        GameRoom room = activeRooms.get(roomId);
        if (room == null) return;

        for (String nickname : playerRooms.keySet()) {
            if (playerRooms.get(nickname) == roomId && !nickname.equals(excludedNickname)) {
                ClientConnection handler = activeConnections.get(nickname);
                if (handler != null) {
                    handler.sendToClient(update);
                }
            }
        }
    }

    /**
     * Sends an update to one active player, if connected.
     *
     * @param nickname target player nickname
     * @param type serialized update type
     * @param message update payload
     */
    public void sendToPlayer(String nickname, String type, String message) {
        if (nickname == null) return;
        ClientConnection connection = activeConnections.get(nickname);
        if (connection != null) {
            connection.sendToClient(new SerializedUpdate(type, message, true));
        }
    }

    /**
     * Broadcasts a targeted message to all active clients (Global).
     *
     * @param type    The message type.
     * @param message The textual content.
     */
    public void broadcast(String type, String message) {
        SerializedUpdate update = new SerializedUpdate(type, message, true);
        for (ClientConnection handler : activeConnections.values()) {
            handler.sendToClient(update);
        }
    }
}
