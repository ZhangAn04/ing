package it.polimi.Network.Server;

import it.polimi.Network.Common.SerializedUpdate;

/**
 * Common abstraction for any connected client endpoint (Socket, RMI, ...).
 */
public interface ClientConnection {
    /**
     * Gets the timestamp (millis) of the last activity from this client.
     *
     * @return last activity timestamp.
     */
    long getLastActivity();

    /**
     * Pushes an update to the remote client.
     *
     * @param update structured update payload.
     */
    void sendToClient(SerializedUpdate update);

    /**
     * Closes the underlying connection.
     */
    void disconnect();
}