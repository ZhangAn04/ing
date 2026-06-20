package it.polimi.Network.Server.View;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.Game.Core.GameView;
import it.polimi.Network.Common.SerializedUpdate;
import java.io.PrintWriter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Acts as the server-side proxy representing a remote client's view.
 * <p>
 * This class implements the Observer pattern by listening to state changes emitted
 * by the central {@link it.polimi.Game.Core.Game} model. When a change is detected, the {@code VirtualView}
 * formats the new state into a {@link SerializedUpdate} and pushes it over the network
 * via the client's output stream.
 * </p>
 */
public class VirtualView implements GameView, PropertyChangeListener {
    /** The output stream directly connected to the remote client. */
    private final PrintWriter out;

    /** Guards concurrent writes to the same client output. */
    private final Object sendLock = new Object();
    
    /** The Jackson object mapper used for JSON serialization. */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs a new VirtualView bounded to a client's output stream.
     *
     * @param out The {@link PrintWriter} used to send data to the client.
     */
    public VirtualView(PrintWriter out) {
        this.out = out;
    }

    /**
     * Invoked automatically when the observed Game model broadcasts a state change.
     *
     * @param evt A PropertyChangeEvent object describing the event source and the state change.
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
     * Serializes a structured update and pushes it across the socket to the client.
     *
     * @param update The update object to be sent.
     */
    private void sendUpdate(SerializedUpdate update) {
        try {
            String json = mapper.writeValueAsString(update);
            synchronized (sendLock) {
                out.println(json);
            }
        } catch (Exception e) {
            System.err.println("Error serializing update: " + e.getMessage());
        }
    }

    /**
     * Sends a generic, non-state-critical notification to the client.
     *
     * @param message The text content of the notification.
     */
    @Override
    public void showMessage(String message) {
        sendUpdate(new SerializedUpdate("NOTIFICATION", message, true));
    }

    /**
     * Broadcasts a newly captured game state to the client.
     *
     * @param status The serialized representation of the current game state.
     */
    @Override
    public void updateGameStatus(String status) {
        sendUpdate(new SerializedUpdate("STATUS_UPDATE", status, true));
    }

    /**
     * Initiates the login handshake by requesting a nickname from the client.
     */
    @Override
    public void askNickname() {
        sendUpdate(new SerializedUpdate("LOGIN_REQUEST", "Please enter your nickname", true));
    }

    /**
     * Informs the client of the outcome of their login request.
     *
     * @param success {@code true} if the nickname was accepted, {@code false} if rejected.
     * @param reason  A descriptive message detailing the outcome.
     */
    @Override
    public void showLoginResult(boolean success, String reason) {
        sendUpdate(new SerializedUpdate("LOGIN_RESULT", reason, success));
    }
}
