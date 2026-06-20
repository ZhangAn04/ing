package it.polimi.Network.Server.RMI;

import it.polimi.Game.Core.GameController;
import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.RMI.ClientCallbackRemote;
import it.polimi.Network.RMI.GameServiceRemote;
import it.polimi.Network.RMI.RmiLoginRequest;
import it.polimi.Network.RMI.RmiLoginResponse;
import it.polimi.Network.Server.GameRoom;
import it.polimi.Network.Server.Lobby;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RMI implementation of the game service exposed by the Mesos server.
 */
public class RmiGameService extends UnicastRemoteObject implements GameServiceRemote {
    /** Lobby that owns rooms and player sessions. */
    private final Lobby lobby;
    /** Active RMI connections keyed by nickname. */
    private final Map<String, RmiClientConnection> connections = new ConcurrentHashMap<>();

    /**
     * Creates the RMI game service.
     *
     * @param lobby      server lobby.
     * @throws RemoteException if remote object export fails.
     */
    public RmiGameService(Lobby lobby) throws RemoteException {
        super();
        this.lobby = lobby;
    }

    /**
     * Handles RMI client login requests (creating, joining, or reconnecting to a game room).
     *
     * @param request The login details (nickname, room ID, action type, PIN).
     * @param callback The client callback object for server notifications.
     * @return The login response containing success status and message details.
     */
    @Override
    public synchronized RmiLoginResponse login(RmiLoginRequest request, ClientCallbackRemote callback) {
        if (request == null || callback == null) {
            return new RmiLoginResponse(false, "Invalid login request.");
        }

        String nickname = request.getNickname();
        if (nickname == null || nickname.trim().isEmpty()) {
            return new RmiLoginResponse(false, "Nickname cannot be empty.");
        }

        String type = request.getType() == null ? "JOIN_ROOM" : request.getType().toUpperCase();
        int roomId = request.getRoomID();
        String pin = request.getPin();
        int requiredPlayers = normalizeRequiredPlayers(request.getMaxPlayers());

        if (connections.containsKey(nickname)) {
            if (!"RECONNECT_ROOM".equals(type) && !"JOIN_ROOM".equals(type)) {
                return new RmiLoginResponse(false, "Client already connected.");
            }
            GameRoom existingRoom = lobby.getRoom(roomId);
            if (existingRoom == null || !existingRoom.validateReconnection(nickname, pin)) {
                return new RmiLoginResponse(false, "Failed to reconnect. Invalid PIN or nickname not found.");
            }
            disconnect(nickname);
            type = "RECONNECT_ROOM";
        }

        boolean success = false;
        String message = "";

        if ("CREATE_ROOM".equals(type)) {
            if (!lobby.createRoom(roomId, null, nickname, requiredPlayers)) {
                return new RmiLoginResponse(false, "Room " + roomId + " already exists.");
            }
        }

        GameRoom room = lobby.getRoom(roomId);
        if (room == null) {
            return new RmiLoginResponse(false, "Room " + roomId + " does not exist.");
        }

        RmiClientConnection connection = new RmiClientConnection(
                nickname,
                callback,
                lobby,
                room,
                () -> connections.remove(nickname)
        );

        if ("CREATE_ROOM".equals(type) || "JOIN_ROOM".equals(type)) {
            success = lobby.joinRoom(roomId, nickname, pin, connection);
            if (success) message = "Joined Room " + roomId + " successfully.";
            else if ("JOIN_ROOM".equals(type) && room.validateReconnection(nickname, pin)) {
                success = lobby.reconnectRoom(roomId, nickname, pin, connection);
                if (success) message = "Reconnected to Room " + roomId + " successfully.";
                else message = "Failed to reconnect. Invalid PIN or nickname not found.";
            } else {
                message = "Failed to join room. Requested player count might be reached or nickname taken.";
            }
        } else if ("RECONNECT_ROOM".equals(type)) {
            success = lobby.reconnectRoom(roomId, nickname, pin, connection);
            if (success) message = "Reconnected to Room " + roomId + " successfully.";
            else message = "Failed to reconnect. Invalid PIN or nickname not found.";
        } else {
            return new RmiLoginResponse(false, "Invalid login type. Use CREATE_ROOM, JOIN_ROOM, or RECONNECT_ROOM.");
        }

        if (!success) {
            return new RmiLoginResponse(false, message);
        }

        connection.activate();
        connection.markActivity();
        connections.put(nickname, connection);
        room.getController().registerPlayer(nickname);

        System.out.println("Player logged in (RMI): " + nickname + " to Room: " + roomId);
        connection.showLoginResult(true, "Welcome " + nickname + "! " + message);
        lobby.broadcastToRoomExcept(roomId, nickname, "NOTIFICATION",
                nickname + " connected to the room.");

        // Broadcast status update within the room
        lobby.broadcastToRoom(roomId, "STATUS_UPDATE", room.getController().statusSnapshot());
        if (room.getController().isGameFinished()) {
            connection.showMessage(room.getController().finalReportText());
        }

        return new RmiLoginResponse(true, "Welcome " + nickname + "! " + message);
    }

    /**
     * Receives and processes action messages sent by RMI clients.
     *
     * @param action The action details containing nickname, type, and command payload.
     * @return The response containing action processing results.
     */
    @Override
    public SerializedUpdate sendAction(ActionMessage action) {
        if (action == null || action.getNickname() == null) {
            return new SerializedUpdate("ERROR", "Invalid action payload.", false);
        }

        RmiClientConnection connection = connections.get(action.getNickname());
        if (connection == null || !connection.isConnected()) {
            return new SerializedUpdate("LOGIN_RESULT", "Please login first.", false);
        }

        connection.markActivity();

        if ("HEARTBEAT".equals(action.getType())) {
            return new SerializedUpdate("HEARTBEAT_ACK", "OK", true);
        }

        try {
            GameRoom room = lobby.getRoomForPlayer(action.getNickname());
            if (room == null) {
                return new SerializedUpdate("ERROR", "Player is not in any room.", false);
            }

            String rawCommand = buildRawCommand(action);
            String response = room.getController().handlePlayerCommand(action.getNickname(), rawCommand);

            String cmd = commandName(action.getType());
            if ("status".equals(cmd)) {
                return new SerializedUpdate("STATUS_UPDATE", room.getController().statusSnapshot(), true);
            }

            if (isSuccessfulLobbyEvent(cmd, response)) {
                lobby.broadcastToRoomExcept(room.getRoomId(), action.getNickname(), "NOTIFICATION", response);
            }
            notifyNextActingPlayer(action.getNickname(), response);
            if (!cmd.startsWith("help") && !cmd.startsWith("hand") && !cmd.startsWith("stats") && !cmd.startsWith("quit")
                    && !response.startsWith("Unknown command:")) {
                lobby.broadcastToRoom(room.getRoomId(), "STATUS_UPDATE", room.getController().statusSnapshot());
            }

            if ("QUIT".equalsIgnoreCase(response)) {
                disconnect(action.getNickname());
            }

            return new SerializedUpdate("NOTIFICATION", response, true);
        } catch (Exception e) {
            return new SerializedUpdate("ERROR", "Error: " + e.getMessage(), false);
        }
    }

    /**
     * Determines whether a lobby command changed shared room state and may be broadcast.
     *
     * @param command normalized command verb
     * @param response controller response
     * @return {@code true} for successful ready, unready, or color actions
     */
    private boolean isSuccessfulLobbyEvent(String command, String response) {
        if (response == null) return false;
        if ("ready".equals(command)) {
            return response.contains(" is ready.") || response.startsWith("All players ready.");
        }
        if ("unready".equals(command)) {
            return response.contains(" returned to the lobby.");
        }
        if ("color".equals(command)) {
            return response.contains(" chose ") || "Game started.".equals(response);
        }
        return false;
    }

    /**
     * Extracts the lowercase command verb from a raw command line.
     *
     * @param rawCommand complete command line
     * @return command verb, or an empty string for blank input
     */
    private String commandName(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return "";
        }
        return rawCommand.trim().toLowerCase().split("\\s+", 2)[0];
    }

    /**
     * Forwards an action-transition response to the newly active player.
     *
     * @param sender player whose command produced the transition
     * @param response controller response that may identify the next player
     */
    private void notifyNextActingPlayer(String sender, String response) {
        String target = extractActingPlayer(response);
        if (target != null && !target.equals(sender)) {
            lobby.sendToPlayer(target, "NOTIFICATION", response);
        }
    }

    /**
     * Extracts the nickname from a {@code Now acting: ...} response.
     *
     * @param response controller response
     * @return acting player's nickname, or {@code null} when absent
     */
    private String extractActingPlayer(String response) {
        String prefix = "Now acting: ";
        if (response == null || !response.startsWith(prefix)) return null;
        int end = response.indexOf(" (tile", prefix.length());
        if (end < 0) return null;
        return response.substring(prefix.length(), end).trim();
    }

    /**
     * Receives a heartbeat ping from the client, updating its last activity timestamp.
     *
     * @param nickname The nickname of the client.
     */
    @Override
    public void heartbeat(String nickname) {
        RmiClientConnection connection = connections.get(nickname);
        if (connection != null) {
            connection.markActivity();
        }
    }

    /**
     * Disconnects a client by nickname and frees resources.
     *
     * @param nickname The client nickname.
     */
    @Override
    public void disconnect(String nickname) {
        RmiClientConnection connection = connections.remove(nickname);
        if (connection != null) {
            connection.disconnect();
        }
    }

    /**
     * Reconstructs the textual controller command from an RMI action DTO.
     *
     * @param action incoming action
     * @return textual command accepted by the game controller
     */
    private String buildRawCommand(ActionMessage action) {
        if (action.getType() == null) {
            return "";
        }
        if (action.getValue() != 0) {
            return action.getType() + " " + action.getValue();
        }
        return action.getType();
    }

    /**
     * Normalizes the player count requested during room creation.
     *
     * @param requiredPlayers requested number of players.
     * @return the requested count when valid, otherwise the default four-player count.
     */
    private int normalizeRequiredPlayers(int requiredPlayers) {
        return requiredPlayers >= 2 && requiredPlayers <= 5 ? requiredPlayers : 4;
    }
}
