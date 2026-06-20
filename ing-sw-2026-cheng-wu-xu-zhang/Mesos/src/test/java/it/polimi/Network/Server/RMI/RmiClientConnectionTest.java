package it.polimi.Network.Server.RMI;

import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.RMI.ClientCallbackRemote;
import it.polimi.Network.Server.Lobby;
import it.polimi.Network.Server.GameRoom;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RmiClientConnection} RMI connection state.
 */
class RmiClientConnectionTest {

    @Test
    void activateSendAndDisconnectCoverMainBranches() throws Exception {
        Lobby lobby = new Lobby(false);
        GameRoom room = new GameRoom(1, null, 4);
        RecordingCallback callback = new RecordingCallback();
        AtomicInteger disconnectedCalls = new AtomicInteger(0);

        try {
            RmiClientConnection connection = new RmiClientConnection(
                    "Alice",
                    callback,
                    lobby,
                    room,
                    disconnectedCalls::incrementAndGet
            );

            connection.sendToClient(new SerializedUpdate("NOTIFICATION", "before", true));
            assertFalse(connection.isConnected());

            connection.activate();
            assertTrue(connection.isConnected());

            connection.markActivity();
            assertTrue(connection.getLastActivity() > 0);

            connection.sendToClient(new SerializedUpdate("NOTIFICATION", "hello", true));
            assertTrue(callback.count > 0);

            connection.disconnect();
            assertFalse(connection.isConnected());
            assertTrue(disconnectedCalls.get() >= 1);
        } finally {
            lobby.shutdown();
            callback.close();
        }
    }

    /**
     * Callback test double that counts delivered updates.
     */
    private static class RecordingCallback extends UnicastRemoteObject implements ClientCallbackRemote {
        private int count;
        protected RecordingCallback() throws RemoteException { super(); }
        @Override public void onUpdate(SerializedUpdate update) { count++; }
        void close() {
            try { UnicastRemoteObject.unexportObject(this, true); } catch (Exception ignored) {}
        }
    }
}
