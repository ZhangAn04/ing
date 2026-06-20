package it.polimi.Network.Server.RMI;

import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.RMI.ClientCallbackRemote;
import it.polimi.Network.Server.ClientConnection;
import it.polimi.Network.Server.GameRoom;
import it.polimi.Network.Server.Lobby;

/**
 * Per-client server-side connection object used by RMI sessions.
 */
public class RmiClientConnection implements ClientConnection {
    private final String nickname;
    private final Lobby lobby;
    private final GameRoom room;
    private final RmiVirtualView virtualView;
    private final Runnable onDisconnected;

    private volatile long lastActivity = System.currentTimeMillis();
    private volatile boolean connected = false;

    /**
     * Creates a new RMI client connection wrapper.
     *
     * @param nickname      player nickname.
     * @param callback      client callback endpoint.
     * @param lobby         server lobby.
     * @param room          the game room the client is connected to.
     * @param onDisconnected callback invoked once this connection is terminated.
     */
    public RmiClientConnection(
            String nickname,
            ClientCallbackRemote callback,
            Lobby lobby,
            GameRoom room,
            Runnable onDisconnected
    ) {
        this.nickname = nickname;
        this.lobby = lobby;
        this.room = room;
        this.onDisconnected = onDisconnected;
        this.virtualView = new RmiVirtualView(callback, this::disconnect);
    }

    /**
     * Starts observing game state changes for this client.
     */
    public synchronized void activate() {
        if (connected) {
            return;
        }
        connected = true;
        room.getGameModel().addPropertyChangeListener(virtualView);
    }

    /**
     * Registers that this client produced activity.
     */
    public void markActivity() {
        lastActivity = System.currentTimeMillis();
    }

    /**
     * Sends login outcome to the client.
     *
     * @param success true if login succeeds.
     * @param message message to display.
     */
    public void showLoginResult(boolean success, String message) {
        virtualView.showLoginResult(success, message);
    }

    /**
     * Sends a controller response to the client.
     *
     * @param message response text.
     */
    public void showMessage(String message) {
        virtualView.showMessage(message);
    }

    /**
     * Reports whether this client connection remains active.
     *
     * @return true if this connection is still active.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns the timestamp of the last activity registered on this connection.
     *
     * @return The last activity timestamp.
     */
    @Override
    public long getLastActivity() {
        return lastActivity;
    }

    /**
     * Sends a game update to the remote client.
     *
     * @param update The serialized update to send.
     */
    @Override
    public void sendToClient(SerializedUpdate update) {
        if (!connected) {
            return;
        }
        virtualView.sendUpdate(update);
    }

    /**
     * Disconnects the client and removes listeners.
     */
    @Override
    public synchronized void disconnect() {
        if (!connected) {
            return;
        }

        connected = false;
        if (room != null) {
            room.getGameModel().removePropertyChangeListener(virtualView);
            room.getController().setPlayerOffline(nickname);
            lobby.broadcastToRoom(room.getRoomId(), "STATUS_UPDATE",
                    room.getController().statusSnapshot());
        }

        if (nickname != null) {
            lobby.disconnectPlayer(nickname);
        }

        if (onDisconnected != null) {
            onDisconnected.run();
        }
    }
}
