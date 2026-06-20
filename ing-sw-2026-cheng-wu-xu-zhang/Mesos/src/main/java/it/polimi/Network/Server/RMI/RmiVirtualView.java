package it.polimi.Network.Server.RMI;

import it.polimi.Game.Core.GameView;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.RMI.ClientCallbackRemote;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.rmi.RemoteException;

/**
 * Server-side view proxy used for RMI clients.
 */
public class RmiVirtualView implements GameView, PropertyChangeListener {
    private final ClientCallbackRemote callback;
    private final Runnable onCommunicationFailure;

    /** Guards concurrent sends to the same remote callback. */
    private final Object sendLock = new Object();

    /**
     * Creates an RMI virtual view.
     *
     * @param callback client callback endpoint.
     * @param onCommunicationFailure action executed when callback communication fails.
     */
    public RmiVirtualView(ClientCallbackRemote callback, Runnable onCommunicationFailure) {
        this.callback = callback;
        this.onCommunicationFailure = onCommunicationFailure;
    }

    /**
     * Listens to properties fired by the game model and forwards them to the client callback.
     *
     * @param evt The event representing a model state change.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("notification".equals(evt.getPropertyName())) {
            showMessage(String.valueOf(evt.getNewValue()));
        } else {
            updateGameStatus(String.valueOf(evt.getNewValue()));
        }
    }

    /**
     * Sends a structured update to the remote callback.
     *
     * @param update update payload.
     */
    public void sendUpdate(SerializedUpdate update) {
        synchronized (sendLock) {
            try {
                callback.onUpdate(update);
            } catch (RemoteException e) {
                if (onCommunicationFailure != null) {
                    onCommunicationFailure.run();
                }
            }
        }
    }

    /**
     * Sends a notification message to the client.
     *
     * @param message The text message to show.
     */
    @Override
    public void showMessage(String message) {
        sendUpdate(new SerializedUpdate("NOTIFICATION", message, true));
    }

    /**
     * Sends the current game status snapshot to the client.
     *
     * @param status The serialized game status.
     */
    @Override
    public void updateGameStatus(String status) {
        sendUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));
    }

    /**
     * Requests the client to provide a nickname (used during RMI initialization checks).
     */
    @Override
    public void askNickname() {
        sendUpdate(new SerializedUpdate("LOGIN_REQUEST", "Please enter your nickname", true));
    }

    /**
     * Informs the client of the login outcome.
     *
     * @param success true if login succeeded, false otherwise.
     * @param reason The descriptive reason for success or failure.
     */
    @Override
    public void showLoginResult(boolean success, String reason) {
        sendUpdate(new SerializedUpdate("LOGIN_RESULT", reason, success));
    }
}
