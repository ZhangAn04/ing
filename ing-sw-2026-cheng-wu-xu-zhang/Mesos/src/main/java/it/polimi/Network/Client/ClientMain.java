package it.polimi.Network.Client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.Config.NetworkSettings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The main entry point for the Mesos client application.
 */
public class ClientMain {

    /** Creates a command-line client entry point. */
    public ClientMain() {
    }
    
    /**
     * Entry point for launching the Mesos client.
     * Resolves the network protocol (Socket or RMI) and runs the corresponding client lifecycle.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        String protocol = resolveProtocolInteractive(args);

        if ("rmi".equals(protocol)) {
            String hostName = resolveRmiHost(args);
            int rmiPort = resolveRmiPort(args);
            String bindingName = NetworkSettings.getRmiBindingNameFromJSON();

            System.out.println("Attempting to connect (rmi) to " + hostName + ":" + rmiPort);
            new RmiClientAdapter().start(hostName, rmiPort, bindingName);
        } else {
            String hostName = resolveSocketHost(args);
            int portNumber = resolveSocketPort(args);
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
            while (runSocketClient(hostName, portNumber, stdIn)) {
                // A fresh connection is required after leaving the current room.
            }
        }
    }

    /**
     * Resolves the protocol to use.
     * <p>
     * If the first command-line argument is a protocol token ({@code socket} or {@code rmi}),
     * that value is used. Otherwise, the user is prompted through the console; pressing Enter
     * selects the default provided by {@link NetworkSettings#getProtocolFromJSON()}.
     *
     * @param args command-line arguments
     * @return the selected protocol, or the configured default when the input is empty
     */
    private static String resolveProtocolInteractive(String[] args) {
        if (args.length >= 1 && isProtocolToken(args[0])) return args[0].toLowerCase();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (true) {
                System.out.print("Choose the protocol (socket/rmi) [socket]: ");
                String line = br.readLine();
                if (line == null) return NetworkSettings.getProtocolFromJSON();
                line = line.trim().toLowerCase();
                if (line.isEmpty()) return NetworkSettings.getProtocolFromJSON();
                if (isProtocolToken(line)) return line;
                System.out.println("Invalid protocol. Enter 'socket' or 'rmi'.");
            }
        } catch (IOException e) {
            return NetworkSettings.getProtocolFromJSON();
        }
    }

    /**
     * Runs the interactive socket client lifecycle.
     *
     * @param hostName server host name
     * @param portNumber server socket port
     * @param stdIn shared terminal input, retained when returning to the gateway menu
     * @return {@code true} when the client requested another gateway session
     */
    private static boolean runSocketClient(String hostName, int portNumber, BufferedReader stdIn) {
        ObjectMapper mapper = new ObjectMapper();
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        ConsoleTuiRenderer tuiRenderer = new ConsoleTuiRenderer();
        SocketLoginState loginState = new SocketLoginState();

        System.out.println("Attempting to connect to " + hostName + ":" + portNumber);

        try (Socket echoSocket = new Socket(hostName, portNumber);
             PrintWriter out = new PrintWriter(echoSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()))) {

            System.out.println("Connected to " + hostName + ":" + portNumber);

            ClientThread listener = new ClientThread(in, tuiRenderer, loginState::onServerUpdate);
            listener.setDaemon(true);
            listener.start();

            try {
                // Handshake Phase. Rejected attempts return to the gateway menu while
                // keeping the same server connection open.
                while (true) {
                    ActionMessage loginAction = handleGatewayMenu(stdIn, tuiRenderer);
                    if (loginAction == null) return false;

                    loginState.reset();
                    loginState.setNickname(loginAction.getNickname());
                    tuiRenderer.setLocalNickname(loginAction.getNickname());
                    out.println(mapper.writeValueAsString(loginAction));

                    if (!loginState.awaitLoginResult(5, TimeUnit.SECONDS)) {
                        tuiRenderer.printLine("Login timed out. Please reconnect and try again.");
                        return false;
                    }
                    if (loginState.isLoginAccepted()) {
                        break;
                    }
                    tuiRenderer.printLine("Returning to the gateway menu.");
                }
                heartbeatExecutor.scheduleAtFixedRate(() -> {
                    try {
                        String nick = loginState.getNickname();
                        if (nick != null) {
                            ActionMessage hb = new ActionMessage("HEARTBEAT", nick, 0);
                            out.println(mapper.writeValueAsString(hb));
                        }
                    } catch (Exception e) { heartbeatExecutor.shutdown(); }
                }, 10, 10, TimeUnit.SECONDS);

                // Interaction loop
                tuiRenderer.printLine("Type 'help' to show available commands.");
                tuiRenderer.printLine("Hint: use 'status' to view the current lobby information.");
                tuiRenderer.renderPrompt();
                String userInput;
                boolean returnToLobby = false;
                while ((userInput = stdIn.readLine()) != null) {
                    String command = userInput.trim();
                    if (command.isEmpty()) continue;
                    if ("help".equalsIgnoreCase(command)) {
                        tuiRenderer.renderHelp();
                        continue;
                    }
                    if ("hand".equalsIgnoreCase(command)) {
                        command = "hand " + loginState.getNickname();
                    }
                    if ("player".equalsIgnoreCase(command)) {
                        tuiRenderer.renderPlayerIdentity();
                        continue;
                    }
                    if ("lobby".equalsIgnoreCase(command)) {
                        tuiRenderer.printLine("Leaving the current match. Returning to the gateway menu.");
                        returnToLobby = true;
                        break;
                    }
                    if (isRestrictedTuiCommand(command)) {
                        tuiRenderer.printLine("This command is not available in the TUI. Use the GUI controls instead.");
                        tuiRenderer.renderPrompt();
                        continue;
                    }
                    if ("status".equalsIgnoreCase(command)) {
                        tuiRenderer.markManualStatusRequest();
                    }
                    tuiRenderer.markCommandSent(command);
                    ActionMessage action = new ActionMessage(command, loginState.getNickname(), 0);
                    out.println(mapper.writeValueAsString(action));
                    if ("quit".equalsIgnoreCase(command)) break;
                }
                return returnToLobby;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                echoSocket.close();
                listener.interrupt();
            }
        } catch (IOException e) {
            System.err.println("Error: Could not connect to the server.");
            return false;
        } finally {
            heartbeatExecutor.shutdownNow();
        }
        return false;
    }

    /**
     * Reads the socket gateway menu and builds the selected login action.
     *
     * @param stdIn terminal input
     * @param tuiRenderer terminal renderer
     * @return login action, or {@code null} when input ends
     * @throws IOException if terminal input cannot be read
     */
    private static ActionMessage handleGatewayMenu(BufferedReader stdIn, ConsoleTuiRenderer tuiRenderer) throws IOException {
        while (true) {
            tuiRenderer.printLine("\n--- Mesos Gateway Menu ---");
            tuiRenderer.printLine("1. Create New Match");
            tuiRenderer.printLine("2. Join Existing Match");
            tuiRenderer.printLine("3. Reconnect to Match");
            System.out.print("Select an option (1-3, or help): ");

            String choice = stdIn.readLine();
            if (choice == null) return null;
            choice = choice.trim();
            if ("help".equalsIgnoreCase(choice)) {
                tuiRenderer.renderHelp();
                continue;
            }

            if (!choice.matches("[123]")) {
                tuiRenderer.printLine("Invalid option.");
                continue;
            }

            System.out.print("Enter nickname: ");
            String nickname = stdIn.readLine();
            if (nickname == null) return null;
            nickname = nickname.trim();

            System.out.print("Enter Room ID: ");
            String roomIdStr = stdIn.readLine();
            if (roomIdStr == null) return null;
            int roomId;
            try { roomId = Integer.parseInt(roomIdStr.trim()); } 
            catch (NumberFormatException e) { tuiRenderer.printLine("Invalid ID."); continue; }

            System.out.print("Enter Personal PIN: ");
            String pin = stdIn.readLine();
            if (pin == null) return null;
            pin = pin.trim();

            String type;
            switch (choice) {
                case "1":
                    type = "CREATE_ROOM";
                    break;
                case "2":
                    type = "JOIN_ROOM";
                    break;
                case "3":
                    type = "RECONNECT_ROOM";
                    break;
                default:
                    type = "JOIN_ROOM";
                    break;
            }

            int requiredPlayers = 4;
            if ("CREATE_ROOM".equals(type)) {
                System.out.print("Enter number of players (2-5): ");
                String playersStr = stdIn.readLine();
                if (playersStr == null) return null;
                try {
                    requiredPlayers = Integer.parseInt(playersStr.trim());
                    if (requiredPlayers < 2 || requiredPlayers > 5) {
                        tuiRenderer.printLine("Invalid player count.");
                        continue;
                    }
                } catch (NumberFormatException e) {
                    tuiRenderer.printLine("Invalid player count.");
                    continue;
                }
            }

            return new ActionMessage(type, nickname, 0, roomId, pin, requiredPlayers);
        }
    }

    /** @return protocol selected from command-line arguments or configuration */
    private static String resolveProtocol(String[] args) {
        if (args.length >= 1 && isProtocolToken(args[0])) return args[0].toLowerCase();
        if (args.length >= 2) return "socket";
        return NetworkSettings.getProtocolFromJSON();
    }

    /** @return socket host selected from command-line arguments or configuration */
    private static String resolveSocketHost(String[] args) {
        if (args.length >= 2 && !isProtocolToken(args[0])) return args[0];
        if (args.length >= 2 && isProtocolToken(args[0])) return args[1];
        return NetworkSettings.getHostFromJSON();
    }

    /** @return socket port selected from command-line arguments or configuration */
    private static int resolveSocketPort(String[] args) {
        int def = NetworkSettings.getPortFromJSON();
        if (args.length >= 2 && !isProtocolToken(args[0])) return parsePortOrDefault(args[1], def);
        if (args.length >= 3 && isProtocolToken(args[0])) return parsePortOrDefault(args[2], def);
        return def;
    }

    /** @return RMI host selected from command-line arguments or configuration */
    private static String resolveRmiHost(String[] args) {
        String def = NetworkSettings.getHostFromJSON();
        if (args.length >= 2 && isProtocolToken(args[0]) && !isInteger(args[1])) return args[1];
        return def;
    }

    /** @return RMI port selected from command-line arguments or configuration */
    private static int resolveRmiPort(String[] args) {
        int def = NetworkSettings.getRmiPortFromJSON();
        if (args.length >= 2 && isProtocolToken(args[0])) {
            if (isInteger(args[1])) return parsePortOrDefault(args[1], def);
            if (args.length >= 3) return parsePortOrDefault(args[2], def);
        }
        return def;
    }

    /**
     * Parses a port number, returning a fallback for invalid input.
     *
     * @param value candidate port text
     * @param def fallback port
     * @return parsed port or fallback
     */
    private static int parsePortOrDefault(String value, int def) {
        try { return Integer.parseInt(value); } catch (Exception e) { return def; }
    }

    /** @return whether {@code v} is a valid decimal integer */
    private static boolean isInteger(String v) {
        try { Integer.parseInt(v); return true; } catch (Exception e) { return false; }
    }

    /** @return whether {@code v} identifies a supported network protocol */
    private static boolean isProtocolToken(String v) {
        return "socket".equalsIgnoreCase(v) || "rmi".equalsIgnoreCase(v);
    }

    /**
     * Returns whether the command is one of the GUI-only game flow controls.
     */
    private static boolean isRestrictedTuiCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String lower = command.trim().toLowerCase();
        return lower.equals("prepare")
                || lower.startsWith("prepare ")
                || lower.equals("manage")
                || lower.equals("round");
    }

    /**
     * Mutable login state shared between the socket listener and startup flow.
     */
    private static final class SocketLoginState {
        private volatile boolean loginAccepted;
        private volatile String nickname;
        private volatile CountDownLatch loginResultReceived = new CountDownLatch(1);

        /**
         * Records a login result received by the socket listener.
         *
         * @param update server update that may contain the login result
         */
        private void onServerUpdate(SerializedUpdate update) {
            if (update != null && "LOGIN_RESULT".equals(update.getType())) {
                loginAccepted = update.isSuccess();
                loginResultReceived.countDown();
            }
        }

        /**
         * Waits until the server returns a login result or the timeout expires.
         *
         * @param timeout maximum amount of time to wait
         * @param unit unit used for {@code timeout}
         * @return {@code true} when a login result was received in time
         * @throws InterruptedException if the waiting thread is interrupted
         */
        private boolean awaitLoginResult(long timeout, TimeUnit unit) throws InterruptedException {
            return loginResultReceived.await(timeout, unit);
        }

        /** Resets the result latch before a new gateway login attempt. */
        private void reset() {
            loginAccepted = false;
            loginResultReceived = new CountDownLatch(1);
        }

        /** @return whether the server accepted the login request */
        private boolean isLoginAccepted() { return loginAccepted; }

        /** @return nickname submitted with the login request */
        private String getNickname() { return nickname; }

        /** @param n nickname submitted with the login request */
        private void setNickname(String n) { this.nickname = n; }
    }
}
