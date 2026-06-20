package it.polimi.Network.Server;

import it.polimi.Game.Core.Game;
import it.polimi.Game.Core.GameController;
import it.polimi.Game.Persistence.GameResultRepositories;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Represents a single game room managing a distinct match, its controller, and connected players.
 */
public class GameRoom {
    private final int roomId;
    private final String password;
    private final Game gameModel;
    private final GameController controller;
    private final int requiredPlayers;
    private String creatorNickname;

    /** Nicknames currently registered in this room. */
    private final List<String> nicknames = new CopyOnWriteArrayList<>();
    
    /** Maps a nickname to their secret PIN for reconnection. */
    private final Map<String, String> playerPins = new ConcurrentHashMap<>();

    /**
     * Constructs a new GameRoom.
     *
     * @param roomId     The unique ID of the room.
     * @param password   The optional password for the room (can be null/empty).
     * @param requiredPlayers The number of players requested for the match.
     */
    public GameRoom(int roomId, String password, int requiredPlayers) {
        this(roomId, password, null, requiredPlayers);
    }

    /**
     * Constructs a new GameRoom.
     *
     * @param roomId          The unique ID of the room.
     * @param password        The optional password for the room (can be null/empty).
     * @param creatorNickname The player who created the room.
     * @param requiredPlayers The number of players requested for the match.
     */
    public GameRoom(int roomId, String password, String creatorNickname, int requiredPlayers) {
        this.roomId = roomId;
        this.password = password;
        this.creatorNickname = creatorNickname;
        this.requiredPlayers = requiredPlayers;
        this.gameModel = new Game();
        this.controller = new GameController(gameModel, GameResultRepositories.fromEnvironment());
        this.controller.configureLobby(creatorNickname, requiredPlayers);
    }

    /**
     * Gets the unique ID of the room.
     *
     * @return The room ID.
     */
    public int getRoomId() {
        return roomId;
    }

    /**
     * Gets the game model for this room.
     *
     * @return The game model.
     */
    public Game getGameModel() {
        return gameModel;
    }

    /**
     * Gets the game controller for this room.
     *
     * @return The game controller.
     */
    public GameController getController() {
        return controller;
    }

    /**
     * Gets the nickname of the room creator.
     *
     * @return creator nickname, or null if not known yet.
     */
    public String getCreatorNickname() {
        return creatorNickname;
    }

    /**
     * Gets the number of players requested for this match.
     *
     * @return requested player count.
     */
    public int getRequiredPlayers() {
        return requiredPlayers;
    }

    /**
     * Checks if the room requires a password.
     *
     * @return True if a password is required, false otherwise.
     */
    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    /**
     * Validates the provided password.
     *
     * @param pwd The password to check.
     * @return True if valid, false otherwise.
     */
    public boolean checkPassword(String pwd) {
        if (!hasPassword()) return true;
        return this.password.equals(pwd);
    }

    /**
     * Adds a player to the room, storing their PIN for future reconnections.
     *
     * @param nickname The player's nickname.
     * @param pin      The player's secret PIN.
     * @return True if successfully added, false if the requested player count was reached or nickname is taken.
     */
    public synchronized boolean addPlayer(String nickname, String pin) {
        if (nicknames.size() >= requiredPlayers) return false;
        if (nicknames.contains(nickname)) return false;

        nicknames.add(nickname);
        playerPins.put(nickname, pin);
        if (creatorNickname == null) {
            creatorNickname = nickname;
            controller.configureLobby(creatorNickname, requiredPlayers);
        }
        return true;
    }

    /**
     * Validates a reconnection attempt.
     *
     * @param nickname The player's nickname.
     * @param pin      The provided PIN.
     * @return True if the player is in the room and the PIN matches.
     */
    public boolean validateReconnection(String nickname, String pin) {
        if (!nicknames.contains(nickname)) return false;
        String expectedPin = playerPins.get(nickname);
        return expectedPin != null && expectedPin.equals(pin);
    }

    /**
     * Checks if the player is already registered in this room.
     *
     * @param nickname The player's nickname.
     * @return True if the player is in the room.
     */
    public boolean containsPlayer(String nickname) {
        return nicknames.contains(nickname);
    }
}
