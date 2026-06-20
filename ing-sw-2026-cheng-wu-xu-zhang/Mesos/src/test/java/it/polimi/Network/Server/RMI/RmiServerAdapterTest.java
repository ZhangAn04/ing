package it.polimi.Network.Server.RMI;

import it.polimi.Network.Server.Lobby;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link RmiServerAdapter} server lifecycle behavior.
 */
class RmiServerAdapterTest {

    @Test
    void startAndStopWorkWhenRegistryAlreadyExists() throws Exception {
        int port = findFreePort();
        Registry preExistingRegistry = LocateRegistry.createRegistry(port);
        Lobby lobby = new Lobby(false);

        RmiServerAdapter adapter = new RmiServerAdapter(port, "MesosTest_" + System.nanoTime(), lobby);

        try {
            adapter.start();
            adapter.stop();
        } finally {
            try { UnicastRemoteObject.unexportObject(preExistingRegistry, true); } catch (Exception ignored) {}
            lobby.shutdown();
        }
    }

    @Test
    void stopWithoutStartDoesNotThrow() {
        Lobby lobby = new Lobby(false);
        RmiServerAdapter adapter = new RmiServerAdapter(2099, "MesosTest", lobby);
        assertDoesNotThrow(adapter::stop);
        lobby.shutdown();
    }

    private int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }
}
