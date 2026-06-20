package it.polimi.Network.Server.RMI;

import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.RMI.ClientCallbackRemote;
import it.polimi.Network.RMI.RmiLoginRequest;
import it.polimi.Network.RMI.RmiLoginResponse;
import it.polimi.Network.Server.Lobby;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RmiGameService} remote game service behavior.
 */
class RmiGameServiceTest {

    @Test
    void loginValidationAndDuplicateNicknameAreHandled() throws Exception {
        Lobby lobby = new Lobby(false);
        RmiGameService service = new RmiGameService(lobby);
        Callback cb1 = new Callback();
        Callback cb2 = new Callback();

        try {
            assertFalse(service.login(null, cb1).isSuccess());

            RmiLoginRequest req1 = new RmiLoginRequest("CREATE_ROOM", "Alice", 1, "p");
            RmiLoginResponse first = service.login(req1, cb1);
            RmiLoginResponse second = service.login(req1, cb2);

            assertTrue(first.isSuccess());
            assertFalse(second.isSuccess());
        } finally {
            service.disconnect("Alice");
            lobby.shutdown();
            cb1.close();
            cb2.close();
            UnicastRemoteObject.unexportObject(service, true);
        }
    }

    @Test
    void sendActionHeartbeatAndDisconnectAreHandled() throws Exception {
        Lobby lobby = new Lobby(false);
        RmiGameService service = new RmiGameService(lobby);
        Callback cb = new Callback();

        try {
            assertFalse(service.sendAction(null).isSuccess());
            assertTrue(service.login(new RmiLoginRequest("CREATE_ROOM", "Bob", 1, "p"), cb).isSuccess());

            ActionMessage hb = new ActionMessage("HEARTBEAT", "Bob", 0);
            assertTrue(service.sendAction(hb).isSuccess());

            service.heartbeat("Bob");
            service.disconnect("Bob");
        } finally {
            lobby.shutdown();
            cb.close();
            UnicastRemoteObject.unexportObject(service, true);
        }
    }

    @Test
    void joinWithExistingNicknameAndValidPinReconnects() throws Exception {
        Lobby lobby = new Lobby(false);
        RmiGameService service = new RmiGameService(lobby);
        Callback firstCallback = new Callback();
        Callback reconnectCallback = new Callback();

        try {
            assertTrue(service.login(new RmiLoginRequest("CREATE_ROOM", "Nia", 7, "pin"), firstCallback).isSuccess());

            RmiLoginResponse reconnect = service.login(
                    new RmiLoginRequest("JOIN_ROOM", "Nia", 7, "pin"),
                    reconnectCallback
            );

            assertTrue(reconnect.isSuccess());
            assertTrue(reconnect.getMessage().contains("Reconnected"));
        } finally {
            service.disconnect("Nia");
            lobby.shutdown();
            firstCallback.close();
            reconnectCallback.close();
            UnicastRemoteObject.unexportObject(service, true);
        }
    }

    /**
     * No-op callback used for RMI service login and reconnect tests.
     */
    private static class Callback extends UnicastRemoteObject implements ClientCallbackRemote {
        protected Callback() throws RemoteException { super(); }
        @Override public void onUpdate(SerializedUpdate update) {}
        void close() {
            try { UnicastRemoteObject.unexportObject(this, true); } catch (Exception ignored) {}
        }
    }
}
