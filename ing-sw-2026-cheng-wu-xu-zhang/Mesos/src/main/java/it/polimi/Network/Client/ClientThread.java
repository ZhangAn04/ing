package it.polimi.Network.Client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.Network.Common.SerializedUpdate;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * A background thread dedicated to asynchronously listening for incoming messages from the server.
 * <p>
 * This thread ensures that the client application remains responsive to user input while simultaneously
 * receiving and displaying updates, notifications, and game state changes broadcasted by the server.
 * It utilizes Jackson to deserialize incoming JSON strings into {@link SerializedUpdate} objects.
 * </p>
 */
public class ClientThread extends Thread {
    /** The input stream used to read data from the server socket. */
    private final BufferedReader in;

    /** Console renderer used by the socket TUI client. */
    private final ConsoleTuiRenderer tuiRenderer;

    /** Optional observer used to react to incoming updates (e.g., login state). */
    private final Consumer<SerializedUpdate> updateObserver;
    
    /** The Jackson object mapper used for JSON deserialization. */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs a new ClientThread.
     *
     * @param in The {@link BufferedReader} connected to the server's output stream.
     */
    public ClientThread(BufferedReader in) {
        this(in, new ConsoleTuiRenderer(), null);
    }

    /**
     * Constructs a new ClientThread with custom rendering and update observation.
     *
     * @param in             The {@link BufferedReader} connected to the server's output stream.
     * @param tuiRenderer    Console renderer used to print updates.
     * @param updateObserver Optional update callback.
     */
    public ClientThread(BufferedReader in, ConsoleTuiRenderer tuiRenderer, Consumer<SerializedUpdate> updateObserver) {
        this.in = in;
        this.tuiRenderer = tuiRenderer == null ? new ConsoleTuiRenderer() : tuiRenderer;
        this.updateObserver = updateObserver;
    }

    /**
     * The main execution loop of the thread.
     * <p>
     * It continuously blocks on reading lines from the server, attempts to deserialize them
     * into {@link SerializedUpdate} objects, and delegates them for processing. If deserialization
     * fails, it falls back to printing the raw text.
     * </p>
     */
    @Override
    public void run() {
        try {
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                try {
                    // CLIENT-SIDE DESERIALIZATION
                    SerializedUpdate update = mapper.readValue(serverMessage, SerializedUpdate.class);
                    
                    processUpdate(update);

                } catch (Exception e) {
                    System.err.println("Failed to process server message: " + e);
                    e.printStackTrace(System.err);
                    // Fallback for raw text (useful for debugging)
                    tuiRenderer.renderRawServerLine(serverMessage);
                }
            }
        } catch (IOException e) {
            tuiRenderer.renderConnectionLost();
        }
    }

    /**
     * Processes and formats a successfully deserialized server update.
     * <p>
     * This method handles various message types (e.g., LOGIN_REQUEST, LOGIN_RESULT, NOTIFICATION)
     * and currently outputs them to the console (TUI). In the future, this method will be adapted
     * to synchronize with a JavaFX GUI using Platform.runLater().
     * </p>
     *
     * @param update The structured update object received from the server.
     */
    private void processUpdate(SerializedUpdate update) {
        tuiRenderer.renderServerUpdate(update);

        if (updateObserver != null) {
            updateObserver.accept(update);
        }
    }
}
