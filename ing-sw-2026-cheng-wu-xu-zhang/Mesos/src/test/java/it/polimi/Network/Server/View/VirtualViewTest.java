package it.polimi.Network.Server.View;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.Network.Common.SerializedUpdate;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link VirtualView} server-side view updates.
 */
class VirtualViewTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verifies that askNickname emits the login request update.
     */
    @Test
    void askNicknameEmitsLoginRequestUpdate() throws Exception {
        StringWriter buffer = new StringWriter();
        VirtualView view = new VirtualView(new PrintWriter(buffer, true));

        view.askNickname();

        SerializedUpdate update = parseSingleUpdate(buffer.toString());
        assertEquals("LOGIN_REQUEST", update.getType());
        assertEquals("Please enter your nickname", update.getContent());
        assertEquals(true, update.isSuccess());
    }

    /**
     * Verifies that login results are serialized with the expected payload.
     */
    @Test
    void showLoginResultEmitsExpectedPayload() throws Exception {
        StringWriter buffer = new StringWriter();
        VirtualView view = new VirtualView(new PrintWriter(buffer, true));

        view.showLoginResult(false, "Nickname taken");

        SerializedUpdate update = parseSingleUpdate(buffer.toString());
        assertEquals("LOGIN_RESULT", update.getType());
        assertEquals("Nickname taken", update.getContent());
        assertEquals(false, update.isSuccess());
    }

    /**
     * Verifies that showMessage emits a NOTIFICATION update.
     */
    @Test
    void showMessageEmitsNotificationUpdate() throws Exception {
        StringWriter buffer = new StringWriter();
        VirtualView view = new VirtualView(new PrintWriter(buffer, true));

        view.showMessage("hello players");

        SerializedUpdate update = parseSingleUpdate(buffer.toString());
        assertEquals("NOTIFICATION", update.getType());
        assertEquals("hello players", update.getContent());
        assertEquals(true, update.isSuccess());
    }

    /**
     * Verifies that updateGameStatus emits a STATUS_UPDATE message.
     */
    @Test
    void updateGameStatusEmitsStatusUpdate() throws Exception {
        StringWriter buffer = new StringWriter();
        VirtualView view = new VirtualView(new PrintWriter(buffer, true));

        view.updateGameStatus("round=2");

        SerializedUpdate update = parseSingleUpdate(buffer.toString());
        assertEquals("STATUS_UPDATE", update.getType());
        assertEquals("round=2", update.getContent());
        assertEquals(true, update.isSuccess());
    }

    /**
     * Verifies that property changes are forwarded as status updates.
     */
    @Test
    void propertyChangeEmitsStatusUpdateWithNewValue() throws Exception {
        StringWriter buffer = new StringWriter();
        VirtualView view = new VirtualView(new PrintWriter(buffer, true));

        view.propertyChange(new PropertyChangeEvent(this, "gameState", "old", "new-state"));

        SerializedUpdate update = parseSingleUpdate(buffer.toString());
        assertEquals("STATUS_UPDATE", update.getType());
        assertEquals("new-state", update.getContent());
        assertEquals(true, update.isSuccess());
    }

    /**
     * Parses the single JSON update emitted by the view.
     */
    private SerializedUpdate parseSingleUpdate(String raw) throws Exception {
        String json = raw.trim();
        return mapper.readValue(json, SerializedUpdate.class);
    }
}
