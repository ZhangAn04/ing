package it.polimi.Network.Server.RMI;

import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.RMI.ClientCallbackRemote;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests forwarding behavior and failure handling of {@link RmiVirtualView}.
 */
class RmiVirtualViewTest {

    @Test
    void sendUpdateAndViewMethodsForwardToCallback() throws Exception {
        RecordingCallback callback = new RecordingCallback(false);
        AtomicInteger failures = new AtomicInteger(0);

        try {
            RmiVirtualView view = new RmiVirtualView(callback, failures::incrementAndGet);

            view.sendUpdate(new SerializedUpdate("X", "payload", true));
            view.showMessage("hello");
            view.updateGameStatus("status");
            view.askNickname();
            view.showLoginResult(true, "ok");
            view.propertyChange(new PropertyChangeEvent(this, "p", "old", "new"));

            assertTrue(callback.count >= 6);
            assertEquals(0, failures.get());
        } finally {
            callback.close();
        }
    }

    @Test
    void callbackFailureTriggersFallback() throws Exception {
        RecordingCallback failingCallback = new RecordingCallback(true);
        AtomicInteger failures = new AtomicInteger(0);

        try {
            RmiVirtualView view = new RmiVirtualView(failingCallback, failures::incrementAndGet);
            view.sendUpdate(new SerializedUpdate("X", "payload", true));

            assertEquals(1, failures.get());
        } finally {
            failingCallback.close();
        }
    }

    /** Test callback that can optionally simulate remote failures. */
    private static class RecordingCallback extends UnicastRemoteObject implements ClientCallbackRemote {
        private final boolean shouldThrow;
        private int count;

        protected RecordingCallback(boolean shouldThrow) throws RemoteException {
            super();
            this.shouldThrow = shouldThrow;
        }

        @Override
        public void onUpdate(SerializedUpdate update) throws RemoteException {
            count++;
            if (shouldThrow) {
                throw new RemoteException("simulated");
            }
        }

        /** Unexports this callback remote object during cleanup. */
        void close() {
            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (Exception ignored) {
                // No-op for test cleanup.
            }
        }
    }
}
