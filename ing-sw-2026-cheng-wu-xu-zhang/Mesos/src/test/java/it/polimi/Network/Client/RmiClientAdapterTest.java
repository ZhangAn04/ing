package it.polimi.Network.Client;

import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.RMI.ClientCallbackRemote;
import it.polimi.Network.RMI.GameServiceRemote;
import it.polimi.Network.RMI.RmiLoginRequest;
import it.polimi.Network.RMI.RmiLoginResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RmiClientAdapter} RMI client operations.
 */
class RmiClientAdapterTest {

    /**
     * Verifies that invalid options are reported in the gateway menu.
     */
    @Test
    void startReportsErrorWhenRegistryIsUnavailable() throws Exception {
        int port = findFreePort();

        CapturedIo io = runWithCapturedIo("1\nAlice\n101\n1234\n2\n", new java.util.function.Consumer<ByteArrayInputStream>() {
            @Override
            public void accept(ByteArrayInputStream input) {
                new RmiClientAdapter().start("127.0.0.1", port, "missing", input);
            }
        });

        assertTrue(io.stderr.contains("Error: Could not connect to the RMI server."));
    }

    /**
     * Verifies successful login and action flow.
     */
    @Test
    void startProcessesActionsAndDisconnectsAtInputEnd() throws Exception {
        int port = findFreePort();
        String bindingName = "RmiClientAdapterTest_" + System.nanoTime();
        Registry registry = LocateRegistry.createRegistry(port);
        StubGameService service = new StubGameService();
        registry.rebind(bindingName, service);

        try {
            // Choice: 1 (Create), Nick: Alice, Room: 101, PIN: 1234, Players: 2, Action: status
            String input = "1" + System.lineSeparator()
                    + "Alice" + System.lineSeparator()
                    + "101" + System.lineSeparator()
                    + "1234" + System.lineSeparator()
                    + "2" + System.lineSeparator()
                    + "status" + System.lineSeparator();

            CapturedIo io = runWithCapturedIo(input, new java.util.function.Consumer<ByteArrayInputStream>() {
                @Override
                public void accept(ByteArrayInputStream commandInput) {
                    new RmiClientAdapter().start("127.0.0.1", port, bindingName, commandInput);
                }
            });

            assertEquals(1, service.loginCalls);
            assertEquals(1, service.actionCalls);
            assertEquals("Alice", service.loggedNicknames.get(0));
            assertTrue(io.stdout.contains("[NOTIFICATION]" + System.lineSeparator() + "callback-ping"));
            assertTrue(io.stdout.contains("Hint: use 'status' to view the current lobby information."));
            assertTrue(io.stdout.contains("Hint: use 'ready' when you are ready to start."));
        } finally {
            cleanupRmi(registry, bindingName, service);
        }
    }

    /**
     * Verifies that help is handled locally and not sent as a game action.
     */
    @Test
    void startShowsLocalHelpWithoutSendingAction() throws Exception {
        int port = findFreePort();
        String bindingName = "RmiClientAdapterHelpTest_" + System.nanoTime();
        Registry registry = LocateRegistry.createRegistry(port);
        StubGameService service = new StubGameService();
        registry.rebind(bindingName, service);

        try {
            String input = "help" + System.lineSeparator()
                    + "1" + System.lineSeparator()
                    + "Alice" + System.lineSeparator()
                    + "101" + System.lineSeparator()
                    + "1234" + System.lineSeparator()
                    + "2" + System.lineSeparator()
                    + "help" + System.lineSeparator();

            CapturedIo io = runWithCapturedIo(input, new java.util.function.Consumer<ByteArrayInputStream>() {
                @Override
                public void accept(ByteArrayInputStream commandInput) {
                    new RmiClientAdapter().start("127.0.0.1", port, bindingName, commandInput);
                }
            });

            assertEquals(1, service.loginCalls);
            assertEquals(0, service.actionCalls);
            assertTrue(io.stdout.contains("--- Mesos TUI Help ---"));
            assertTrue(io.stdout.contains("hand <player>"));
            assertTrue(io.stdout.contains("pick <upper|lower> <t|b> <index>"));
            assertFalse(io.stdout.contains("prepare tiles"));
            assertFalse(io.stdout.contains("prepare firstround"));
            assertFalse(io.stdout.contains("manage"));
            assertFalse(io.stdout.contains("round"));
        } finally {
            cleanupRmi(registry, bindingName, service);
        }
    }

    /**
     * Verifies that GUI-only round-management commands are blocked locally.
     */
    @Test
    void startBlocksGuiOnlyCommandsLocally() throws Exception {
        int port = findFreePort();
        String bindingName = "RmiClientAdapterBlockTest_" + System.nanoTime();
        Registry registry = LocateRegistry.createRegistry(port);
        StubGameService service = new StubGameService();
        registry.rebind(bindingName, service);

        try {
            String input = "1" + System.lineSeparator()
                    + "Alice" + System.lineSeparator()
                    + "101" + System.lineSeparator()
                    + "1234" + System.lineSeparator()
                    + "2" + System.lineSeparator()
                    + "prepare firstround" + System.lineSeparator()
                    + "manage" + System.lineSeparator()
                    + "round" + System.lineSeparator()
                    + "quit" + System.lineSeparator();

            CapturedIo io = runWithCapturedIo(input, new java.util.function.Consumer<ByteArrayInputStream>() {
                @Override
                public void accept(ByteArrayInputStream commandInput) {
                    new RmiClientAdapter().start("127.0.0.1", port, bindingName, commandInput);
                }
            });

            assertEquals(1, service.loginCalls);
            assertEquals(1, service.actionCalls);
            assertTrue(io.stdout.contains("This command is not available in the TUI."));
        } finally {
            cleanupRmi(registry, bindingName, service);
        }
    }

    /**
     * Verifies that a hand request for another player is forwarded to the server unchanged.
     */
    @Test
    void startForwardsNamedHandCommand() throws Exception {
        int port = findFreePort();
        String bindingName = "RmiClientAdapterHandTest_" + System.nanoTime();
        Registry registry = LocateRegistry.createRegistry(port);
        StubGameService service = new StubGameService();
        registry.rebind(bindingName, service);

        try {
            String input = "1" + System.lineSeparator()
                    + "Alice" + System.lineSeparator()
                    + "101" + System.lineSeparator()
                    + "1234" + System.lineSeparator()
                    + "2" + System.lineSeparator()
                    + "hand Bob" + System.lineSeparator();

            runWithCapturedIo(input, commandInput ->
                    new RmiClientAdapter().start("127.0.0.1", port, bindingName, commandInput));

            assertEquals(1, service.actionCalls);
            assertEquals("hand Bob", service.actionTypes.get(0));
        } finally {
            cleanupRmi(registry, bindingName, service);
        }
    }

    /**
     * Captures standard streams while feeding deterministic stdin to client code.
     */
    private CapturedIo runWithCapturedIo(String input, java.util.function.Consumer<ByteArrayInputStream> action) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(stdout, true));
            System.setErr(new PrintStream(stderr, true));
            action.accept(new ByteArrayInputStream(input.getBytes()));
            return new CapturedIo(stdout.toString(), stderr.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    /**
     * Ensures remote test resources are unbound and unexported.
     */
    private void cleanupRmi(Registry registry, String bindingName, StubGameService service) {
        try {
            registry.unbind(bindingName);
        } catch (Exception ignored) {}
        try {
            UnicastRemoteObject.unexportObject(service, true);
        } catch (Exception ignored) {}
        try {
            UnicastRemoteObject.unexportObject(registry, true);
        } catch (Exception ignored) {}
    }

    private int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Minimal RMI service stub used to exercise adapter login and actions.
     */
    private static class StubGameService extends UnicastRemoteObject implements GameServiceRemote {
        private int loginCalls;
        private int actionCalls;
        private final List<String> loggedNicknames = new ArrayList<>();
        private final List<String> actionTypes = new ArrayList<>();

        protected StubGameService() throws java.rmi.RemoteException {
            super();
        }

        @Override
        public RmiLoginResponse login(RmiLoginRequest request, ClientCallbackRemote callback) throws java.rmi.RemoteException {
            loginCalls++;
            if (request != null) {
                loggedNicknames.add(request.getNickname());
            }
            if (callback != null) {
                callback.onUpdate(new SerializedUpdate("NOTIFICATION", "callback-ping", true));
            }
            return new RmiLoginResponse(true, "Welcome");
        }

        @Override
        public SerializedUpdate sendAction(ActionMessage action) {
            actionCalls++;
            actionTypes.add(action.getType());
            return new SerializedUpdate("OK", "ok", true);
        }

        @Override public void heartbeat(String nickname) {}
        @Override public void disconnect(String nickname) {}
    }

    /**
     * Captures standard output and error emitted during a client adapter run.
     */
    private static class CapturedIo {
        private final String stdout;
        private final String stderr;
        private CapturedIo(String stdout, String stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
