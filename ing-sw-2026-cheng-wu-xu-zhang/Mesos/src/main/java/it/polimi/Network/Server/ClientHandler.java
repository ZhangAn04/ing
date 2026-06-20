package it.polimi.Network.Server;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import it.polimi.Game.Core.GameController;
import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.Server.View.VirtualView;

/**
 * Handles a single client connection on the server side.
 * <p>
 * This class implements {@link Runnable} to allow the server to manage multiple clients
 * concurrently. For each connected client, the {@code ClientHandler}:
 * <ul>
 *     <li>Manages the initial login handshake via the {@link Lobby} (Create, Join, Reconnect to rooms).</li>
 *     <li>Reads incoming JSON actions, deserializes them into {@link ActionMessage} objects, and forwards them to the room's {@link GameController}.</li>
 *     <li>Instantiates and binds a {@link VirtualView} to the client's output stream, registering it as an observer of the room's game state.</li>
 * </ul>
 */
public class ClientHandler implements Runnable, ClientConnection {
    /** The underlying TCP socket connected to the client. */
    private final Socket clientSocket;
    
    /** The shared lobby used to manage game rooms. */
    private final Lobby lobby;
    
    /** The room the client is currently in. */
    private GameRoom room;
    
    /** The Jackson object mapper used for deserializing JSON strings. */
    private final ObjectMapper mapper = new ObjectMapper();
    
    /** The remote view avatar that pushes game state updates to this specific client. */
    private VirtualView virtualView;
    
    /** The verified nickname associated with this client connection. */
    private String nickname;
    
    /** Indicates whether the client has successfully completed the login handshake. */
    private boolean loggedIn = false;

    /** The system time (in milliseconds) of the last received message from this client. */
    private long lastActivity = System.currentTimeMillis();

    /**
     * Constructs a new handler for a connected client.
     *
     * @param clientSocket The socket established with the client.
     * @param lobby        The server's lobby instance for managing rooms.
     */
    public ClientHandler(Socket clientSocket, Lobby lobby) {
        this.clientSocket = clientSocket;
        this.lobby = lobby;
    }

    /**
     * The main execution loop for the client's thread.
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // Link the VirtualView to this client's output stream
            this.virtualView = new VirtualView(out);

            System.out.println("New connection from " + clientSocket.getRemoteSocketAddress());

            String s;
            while ((s = in.readLine()) != null) {
                try {
                    ActionMessage action = mapper.readValue(s, ActionMessage.class);
                    processAction(action);
                } catch (Exception e) {
                    virtualView.showLoginResult(false, "Invalid JSON format.");
                }
            }

        } catch (IOException e) {
            System.err.println("Connection lost with " + (nickname != null ? nickname : "unknown client"));
        } finally {
            if (nickname != null) {
                lobby.disconnectPlayer(nickname);
                if (room != null) {
                    room.getController().setPlayerOffline(nickname);
                    lobby.broadcastToRoom(room.getRoomId(), "STATUS_UPDATE",
                            room.getController().statusSnapshot());
                }
            }
            if (virtualView != null && room != null && room.getGameModel() != null) {
                room.getGameModel().removePropertyChangeListener(virtualView);
            }
            disconnect();
        }
    }

    /**
     * Processes an incoming action received from the client.
     *
     * @param action The deserialized action message.
     */
    private void processAction(ActionMessage action) {
        this.lastActivity = System.currentTimeMillis();

        if ("HEARTBEAT".equals(action.getType())) {
            return; // Heartbeat handled: activity updated, no further action needed.
        }

        if (!loggedIn) {
            handleLogin(action);
        } else {
            // Forward other actions to the room's GameController
            System.out.println("Received action from " + nickname + ": " + action.getType());
            String rawCommand = action.getType() + (action.getValue() != 0 ? " " + action.getValue() : "");
            
            GameController controller = room.getController();
            String response = controller.handlePlayerCommand(nickname, rawCommand);

            // Interactive TUI: broadcast an updated status after each meaningful command,
            // so all clients stay in sync without manually typing 'status'.
            String cmd = commandName(action.getType());
            if ("status".equals(cmd)) {
                virtualView.updateGameStatus(controller.statusSnapshot());
                return;
            }

            virtualView.showMessage(response);
            notifyNextActingPlayer(response);
            if (isSuccessfulLobbyEvent(cmd, response)) {
                lobby.broadcastToRoomExcept(room.getRoomId(), nickname, "NOTIFICATION", response);
            }
            if (!cmd.startsWith("help") && !cmd.startsWith("hand") && !cmd.startsWith("stats") && !cmd.startsWith("quit")
                    && !response.startsWith("Unknown command:")) {
                lobby.broadcastToRoom(room.getRoomId(), "STATUS_UPDATE", controller.statusSnapshot());
            }
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
     * @param response controller response that may identify the next player
     */
    private void notifyNextActingPlayer(String response) {
        String target = extractActingPlayer(response);
        if (target != null && !target.equals(nickname)) {
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
     * Gets the system time of the last activity from this client.
     *
     * @return The last activity timestamp in milliseconds.
     */
    public long getLastActivity() {
        return lastActivity;
    }

    /**
     * Handles the initial login handshake phase with room support.
     *
     * @param action The action message containing the requested nickname, room, and pin.
     */
    private void handleLogin(ActionMessage action) {
        String requestedName = action.getNickname();
        String type = action.getType() == null ? "" : action.getType().toUpperCase();
        int roomId = action.getRoomID();
        String pin = action.getPin();
        int requiredPlayers = normalizeRequiredPlayers(action.getMaxPlayers());

        boolean success = false;
        String message = "";

        if ("CREATE_ROOM".equals(type)) {
            if (lobby.createRoom(roomId, null, requestedName, requiredPlayers)) {
                success = lobby.joinRoom(roomId, requestedName, pin, this);
                if (success) message = "Room " + roomId + " created and joined successfully.";
                else message = "Failed to join newly created room.";
            } else {
                message = "Room " + roomId + " already exists.";
            }
        } else if ("JOIN_ROOM".equals(type)) {
            success = lobby.joinRoom(roomId, requestedName, pin, this);
            if (success) message = "Joined Room " + roomId + " successfully.";
            else {
                GameRoom existingRoom = lobby.getRoom(roomId);
                if (existingRoom != null && existingRoom.validateReconnection(requestedName, pin)) {
                    success = lobby.reconnectRoom(roomId, requestedName, pin, this);
                    if (success) message = "Reconnected to Room " + roomId + " successfully.";
                    else message = "Failed to reconnect. Invalid PIN, wrong room, or nickname not found.";
                } else {
                    message = "Failed to join room. Requested player count might be reached, nickname taken, or room doesn't exist.";
                }
            }
        } else if ("RECONNECT_ROOM".equals(type)) {
            success = lobby.reconnectRoom(roomId, requestedName, pin, this);
            if (success) message = "Reconnected to Room " + roomId + " successfully.";
            else message = "Failed to reconnect. Invalid PIN, wrong room, or nickname not found.";
        } else {
            virtualView.showLoginResult(false, "Invalid login type. Use CREATE_ROOM, JOIN_ROOM, or RECONNECT_ROOM.");
            return;
        }

        if (success) {
            this.nickname = requestedName;
            this.loggedIn = true;
            this.room = lobby.getRoomForPlayer(nickname);
            
            this.room.getController().registerPlayer(nickname);
            this.room.getGameModel().addPropertyChangeListener(this.virtualView);
            
            System.out.println("Player logged in(socket): " + nickname + " to Room: " + roomId);
            virtualView.showLoginResult(true, "Welcome " + nickname + "! " + message);
            lobby.broadcastToRoomExcept(room.getRoomId(), nickname, "NOTIFICATION",
                    nickname + " connected to the room.");

            // Trigger status update
            GameController controller = this.room.getController();
            virtualView.updateGameStatus(controller.statusSnapshot());
            if (controller.isGameFinished()) {
                virtualView.showMessage(controller.finalReportText());
            }
        } else {
            virtualView.showLoginResult(false, message);
        }
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

    /**
     * Forwards a serialized game update to the client via the VirtualView.
     *
     * @param update The structured update containing new game state or notifications.
     */
    public void sendToClient(SerializedUpdate update) {
        if (virtualView != null) {
            if (update == null) {
                return;
            }

            String type = update.getType() == null ? "" : update.getType().trim().toUpperCase();
            if ("STATUS_UPDATE".equals(type)) {
                virtualView.updateGameStatus(update.getContent());
                return;
            }

            virtualView.showMessage(update.getContent());
        }
    }

    /**
     * Gracefully closes the underlying TCP socket connection.
     * Any IO exceptions thrown during closure are silently ignored.
     */
    public void disconnect() {
        try {
            if (!clientSocket.isClosed()) clientSocket.close();
        } catch (IOException e) { /* Ignore */ }
    }
}
