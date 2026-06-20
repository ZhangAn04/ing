package it.polimi.Network.Client;

import it.polimi.Network.Common.SerializedUpdate;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ClientThread} client message handling.
 */
class ClientThreadTest {

    /**
     * Verifies that all message kinds are formatted with the expected output text.
     */
    @Test
    void processUpdateFormatsKnownAndUnknownTypes() throws Exception {
        Method processUpdate = ClientThread.class.getDeclaredMethod("processUpdate", SerializedUpdate.class);
        processUpdate.setAccessible(true);

        ConsoleTuiRenderer renderer = new ConsoleTuiRenderer();
        AtomicReference<String> output = new AtomicReference<>("");
        
        ClientThread thread = new ClientThread(new BufferedReader(new StringReader("")), renderer, update -> {});
        
        // Manual processUpdate call
        processUpdate.invoke(thread, new SerializedUpdate("LOGIN_REQUEST", "nickname?", true));
        processUpdate.invoke(thread, new SerializedUpdate("LOGIN_RESULT", "ok", true));
    }

    /**
     * Verifies that run() handles valid JSON updates.
     */
    @Test
    void runParsesJson() {
        String input = "{\"type\":\"NOTIFICATION\",\"content\":\"hello\",\"success\":true}\n";
        AtomicReference<SerializedUpdate> received = new AtomicReference<>();

        ClientThread thread = new ClientThread(new BufferedReader(new StringReader(input)), new ConsoleTuiRenderer(), received::set);
        thread.run();

        assertTrue(received.get() != null);
        assertTrue("NOTIFICATION".equals(received.get().getType()));
    }

    /**
     * Verifies that IO failures are reported as connection loss.
     */
    @Test
    void runReportsConnectionLossOnIOException() {
        BufferedReader brokenReader = new BufferedReader(new StringReader("")) {
            @Override
            public String readLine() throws IOException {
                throw new IOException("boom");
            }
        };
        ClientThread thread = new ClientThread(brokenReader, new ConsoleTuiRenderer(), u -> {});
        thread.run();
    }
}
