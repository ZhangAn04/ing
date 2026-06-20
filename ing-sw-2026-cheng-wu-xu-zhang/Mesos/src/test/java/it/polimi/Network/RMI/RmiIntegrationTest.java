package it.polimi.Network.RMI;

import it.polimi.Game.Core.Game;
import it.polimi.Game.Core.GameController;
import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.Server.Lobby;
import it.polimi.Network.Server.RMI.RmiClientConnection;
import it.polimi.Network.Server.RMI.RmiServerAdapter;
import it.polimi.Network.Server.RMI.RmiVirtualView;
import it.polimi.Network.Server.GameRoom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the RMI transport.
 */
class RmiIntegrationTest {
    private int rmiPort;
    private String bindingName;
    private RmiServerAdapter serverAdapter;
    private Lobby lobby;

    @BeforeEach
    void setUp() throws Exception {
        rmiPort = findFreePort();
        bindingName = "MesosIntegrationTest_" + System.nanoTime();

        // Start Lobby without monitor for deterministic testing
        lobby = new Lobby(false);

        // Server Adapter now only takes the lobby
        serverAdapter = new RmiServerAdapter(rmiPort, bindingName, lobby);
        serverAdapter.start();
    }

    @AfterEach
    void tearDown() {
        if (serverAdapter != null) {
            serverAdapter.stop();
        }
        if (lobby != null) {
            lobby.shutdown();
        }
    }

    @Test
    void loginAndCommandRoundTripOverRmiWorks() throws Exception {
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", rmiPort);
        GameServiceRemote remote = (GameServiceRemote) registry.lookup(bindingName);

        TestCallback callback = new TestCallback();
        try {
            // handshake choice: 1 (CREATE), Nick: Alice, Room: 101, PIN: 1234
            RmiLoginRequest request = new RmiLoginRequest("CREATE_ROOM", "Alice", 101, "1234");
            RmiLoginResponse login = remote.login(request, callback);
            assertTrue(login.isSuccess(), "Login failed: " + login.getMessage());

            SerializedUpdate loginUpdate = callback.waitForType("LOGIN_RESULT", 2, TimeUnit.SECONDS);
            assertNotNull(loginUpdate);
            assertTrue(loginUpdate.isSuccess());

            SerializedUpdate response = remote.sendAction(new ActionMessage("status", "Alice", 0));
            assertTrue(response.isSuccess());
        } finally {
            callback.close();
        }
    }

    @Test
    void rmiVirtualViewCoversSuccessAndFailurePaths() throws Exception {
        TestCallback callback = new TestCallback();
        AtomicBoolean failed = new AtomicBoolean(false);

        try {
            RmiVirtualView view = new RmiVirtualView(callback, () -> failed.set(true));
            view.showMessage("hello");
            view.updateGameStatus("status");
            view.askNickname();
            view.showLoginResult(true, "ok");
            view.propertyChange(new PropertyChangeEvent(this, "state", "old", "new"));

            assertNotNull(callback.waitForType("NOTIFICATION", 2, TimeUnit.SECONDS));
            assertNotNull(callback.waitForType("STATUS_UPDATE", 2, TimeUnit.SECONDS));
            assertFalse(failed.get());
        } finally {
            callback.close();
        }
    }

    @Test
    void rmiClientConnectionLifecycleBranches() throws Exception {
        Lobby localLobby = new Lobby(false);
        GameRoom localRoom = new GameRoom(1, null, 4);
        TestCallback callback = new TestCallback();
        AtomicBoolean disconnected = new AtomicBoolean(false);

        try {
            RmiClientConnection connection = new RmiClientConnection(
                    "Alice",
                    callback,
                    localLobby,
                    localRoom,
                    () -> disconnected.set(true)
            );

            connection.sendToClient(new SerializedUpdate("NOTIFICATION", "before", true));
            assertFalse(connection.isConnected());

            connection.activate();
            assertTrue(connection.isConnected());

            connection.markActivity();
            assertTrue(connection.getLastActivity() > 0);

            connection.disconnect();
            assertFalse(connection.isConnected());
            assertTrue(disconnected.get());
        } finally {
            callback.close();
            localLobby.shutdown();
        }
    }

    private int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Test callback that records RMI updates for integration assertions.
     */
    private static class TestCallback extends UnicastRemoteObject implements ClientCallbackRemote {
        private final BlockingQueue<SerializedUpdate> updates = new LinkedBlockingQueue<>();
        protected TestCallback() throws java.rmi.RemoteException { super(); }

        @Override
        public void onUpdate(SerializedUpdate update) {
            if (update != null) updates.offer(update);
        }

        private SerializedUpdate waitForType(String type, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (System.nanoTime() < deadline) {
                SerializedUpdate u = updates.poll(100, TimeUnit.MILLISECONDS);
                if (u != null && type.equals(u.getType())) return u;
            }
            return null;
        }

        private void close() {
            try { UnicastRemoteObject.unexportObject(this, true); } catch (Exception ignored) {}
        }
    }
}
