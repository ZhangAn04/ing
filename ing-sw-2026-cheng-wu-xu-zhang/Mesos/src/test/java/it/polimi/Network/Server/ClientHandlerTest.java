package it.polimi.Network.Server;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.Server.View.VirtualView;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClientHandler} socket client handling.
 */
class ClientHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendToClientWithoutViewDoesNothing() {
        ClientHandler handler = new ClientHandler(new Socket(), new Lobby(false));
        handler.sendToClient(new SerializedUpdate("NOTIFICATION", "hello", true));
        assertTrue(handler.getLastActivity() > 0);
    }

    @Test
    void sendToClientWithViewForwardsStatusUpdate() throws Exception {
        ClientHandler handler = new ClientHandler(new Socket(), new Lobby(false));
        StringWriter buffer = new StringWriter();
        VirtualView view = new VirtualView(new PrintWriter(buffer, true));
        setField(handler, "virtualView", view);

        handler.sendToClient(new SerializedUpdate("NOTIFICATION", "payload", true));

        SerializedUpdate result = mapper.readValue(buffer.toString().trim(), SerializedUpdate.class);
        assertEquals("NOTIFICATION", result.getType());
        assertEquals("payload", result.getContent());
    }

    @Test
    void disconnectClosesSocket() {
        Socket socket = new Socket();
        ClientHandler handler = new ClientHandler(socket, new Lobby(false));
        handler.disconnect();
        assertTrue(socket.isClosed());
    }

    @Test
    void processActionCoversHeartbeatLoginAndCommandFlow() throws Exception {
        Lobby lobby = new Lobby(false);
        ClientHandler handler = new ClientHandler(new Socket(), lobby);

        StringWriter buffer = new StringWriter();
        setField(handler, "virtualView", new VirtualView(new PrintWriter(buffer, true)));

        Method processAction = ClientHandler.class.getDeclaredMethod("processAction", ActionMessage.class);
        processAction.setAccessible(true);

        processAction.invoke(handler, new ActionMessage("HEARTBEAT", "Alice", 0));
        
        // handshake CREATE_ROOM
        ActionMessage createReq = new ActionMessage("CREATE_ROOM", "Alice", 0, 101, "1234");
        processAction.invoke(handler, createReq);
        
        processAction.invoke(handler, new ActionMessage("status", "Alice", 0));

        assertTrue(buffer.toString().contains("LOGIN_RESULT"));
        assertTrue(buffer.toString().contains("Status:"));

        lobby.shutdown();
    }

    @Test
    void unknownCommandDoesNotBroadcastStatus() throws Exception {
        Lobby lobby = new Lobby(false);
        ClientHandler handler = new ClientHandler(new Socket(), lobby);
        StringWriter buffer = new StringWriter();
        setField(handler, "virtualView", new VirtualView(new PrintWriter(buffer, true)));

        Method processAction = ClientHandler.class.getDeclaredMethod("processAction", ActionMessage.class);
        processAction.setAccessible(true);
        processAction.invoke(handler, new ActionMessage("CREATE_ROOM", "Alice", 0, 102, "1234"));
        buffer.getBuffer().setLength(0);

        processAction.invoke(handler, new ActionMessage("unknown", "Alice", 0));

        assertTrue(buffer.toString().contains("Unknown command: unknown"));
        assertFalse(buffer.toString().contains("STATUS_UPDATE"));
        lobby.shutdown();
    }

    @Test
    void invalidLobbyChoicesAreNotEligibleForBroadcast() throws Exception {
        ClientHandler handler = new ClientHandler(new Socket(), new Lobby(false));
        Method method = ClientHandler.class.getDeclaredMethod(
                "isSuccessfulLobbyEvent", String.class, String.class);
        method.setAccessible(true);

        assertEquals(false, method.invoke(handler, "color", "Color already selected or unavailable: BLUE"));
        assertEquals(false, method.invoke(handler, "color", "Not your turn to choose color. Current player: Alice"));
        assertEquals(true, method.invoke(handler, "color",
                "Alice chose BLUE. Available colors: ORANGE. Next color choice: Bob"));
    }

    @Test
    void runHandlesInvalidJsonThenCleansUp() throws Exception {
        Lobby lobby = new Lobby(false);

        try (ServerSocket server = new ServerSocket(0);
             Socket clientSide = new Socket("127.0.0.1", server.getLocalPort());
             Socket serverSide = server.accept()) {

            ClientHandler handler = new ClientHandler(serverSide, lobby);
            Thread t = new Thread(handler);
            t.start();

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSide.getOutputStream()));

            out.write("not-a-json\n");
            out.flush();

            String loginJson = mapper.writeValueAsString(new ActionMessage("CREATE_ROOM", "Alice", 0, 1, "p"));
            out.write(loginJson + "\n");
            out.flush();

            clientSide.shutdownOutput();
            t.join(2000);

            assertNotNull(lobby.getRoomForPlayer("Alice"));
        } finally {
            lobby.shutdown();
        }
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
