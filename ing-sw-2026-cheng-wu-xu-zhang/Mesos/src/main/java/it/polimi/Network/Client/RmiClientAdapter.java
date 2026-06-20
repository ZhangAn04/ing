package it.polimi.Network.Client;

import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import it.polimi.Network.RMI.ClientCallbackRemote;
import it.polimi.Network.RMI.GameServiceRemote;
import it.polimi.Network.RMI.RmiLoginRequest;
import it.polimi.Network.RMI.RmiLoginResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Adapter for the RMI transport on the client side.
 */
public class RmiClientAdapter {

    /** Creates an RMI client adapter. */
    public RmiClientAdapter() {
    }
    private final ConsoleTuiRenderer tuiRenderer = new ConsoleTuiRenderer();
    private String nickname;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    /**
     * Starts the RMI client using standard System.in for commands.
     *
     * @param host RMI registry host
     * @param rmiPort RMI registry port
     * @param bindingName remote service binding name
     */
    public void start(String host, int rmiPort, String bindingName) {
        start(host, rmiPort, bindingName, System.in);
    }

    /**
     * Starts the RMI client using a custom input stream.
     *
     * @param host RMI registry host
     * @param rmiPort RMI registry port
     * @param bindingName remote service binding name
     * @param commandInput command input stream
     */
    public void start(String host, int rmiPort, String bindingName, InputStream commandInput) {
        ClientCallback callback = null;
        GameServiceRemote service = null;
        try (BufferedReader stdIn = new BufferedReader(new InputStreamReader(commandInput))) {
            Registry registry = LocateRegistry.getRegistry(host, rmiPort);
            service = (GameServiceRemote) registry.lookup(bindingName);
            GameServiceRemote activeService = service;

            callback = new ClientCallback(tuiRenderer);
            ClientCallbackRemote stub = (ClientCallbackRemote) UnicastRemoteObject.exportObject(callback, 0);

            tuiRenderer.printLine("Connected to RMI registry " + host + ":" + rmiPort + " (service: " + bindingName + ")");

            heartbeatExecutor.scheduleAtFixedRate(() -> {
                String currentNickname = nickname;
                if (currentNickname == null) return;
                try {
                    activeService.heartbeat(currentNickname);
                } catch (Exception e) {
                    heartbeatExecutor.shutdown();
                }
            }, 10, 10, TimeUnit.SECONDS);

            boolean returnToGateway;
            do {
                RmiLoginResponse loginResponse;
                while (true) {
                    RmiLoginRequest request = handleGatewayMenu(stdIn, tuiRenderer);
                    if (request == null) return;

                    nickname = request.getNickname();
                    tuiRenderer.setLocalNickname(nickname);
                    loginResponse = service.login(request, stub);
                    tuiRenderer.printLine(loginResponse.getMessage());
                    if (loginResponse.isSuccess()) {
                        break;
                    }
                    nickname = null;
                    tuiRenderer.printLine("Returning to the gateway menu.");
                }

                returnToGateway = false;
                String line;
                tuiRenderer.printLine("Type 'help' to show available commands.");
                tuiRenderer.printLine("Hint: use 'status' to view the current lobby information.");
                tuiRenderer.renderPrompt();
                while ((line = stdIn.readLine()) != null) {
                    String command = line.trim();
                    if (command.isEmpty()) continue;
                    if ("help".equalsIgnoreCase(command)) {
                        tuiRenderer.renderHelp();
                        continue;
                    }
                    if ("hand".equalsIgnoreCase(command)) {
                        command = "hand " + nickname;
                    }
                    if ("player".equalsIgnoreCase(command)) {
                        tuiRenderer.renderPlayerIdentity();
                        continue;
                    }
                    if ("lobby".equalsIgnoreCase(command)) {
                        service.disconnect(nickname);
                        nickname = null;
                        tuiRenderer.printLine("Leaving the current match. Returning to the gateway menu.");
                        returnToGateway = true;
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
                    ActionMessage action = new ActionMessage(command, nickname, 0);
                    SerializedUpdate response = service.sendAction(action);
                    if (response != null) tuiRenderer.renderServerUpdate(response);
                    if ("quit".equalsIgnoreCase(command)) break;
                }
            } while (returnToGateway);
        } catch (Exception e) {
            System.err.println("Error: Could not connect to the RMI server.");
        } finally {
            if (service != null && nickname != null) {
                try { service.disconnect(nickname); } catch (Exception ignored) {}
            }
            nickname = null;
            heartbeatExecutor.shutdownNow();
            if (callback != null) {
                try { UnicastRemoteObject.unexportObject(callback, true); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Reads the RMI gateway menu and creates the corresponding login request.
     *
     * @param stdIn terminal input
     * @param tuiRenderer terminal renderer
     * @return login request, or {@code null} when input ends
     * @throws IOException if terminal input cannot be read
     */
    private RmiLoginRequest handleGatewayMenu(BufferedReader stdIn, ConsoleTuiRenderer tuiRenderer) throws IOException {
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
            if (!choice.matches("[123]")) { tuiRenderer.printLine("Invalid option."); continue; }

            System.out.print("Enter nickname: ");
            String nick = stdIn.readLine();
            if (nick == null) return null;
            nick = nick.trim();

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

            return new RmiLoginRequest(type, nick, roomId, pin, requiredPlayers);
        }
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
     * RMI callback that renders server updates in the terminal client.
     */
    private static class ClientCallback implements ClientCallbackRemote {
        private final ConsoleTuiRenderer tuiRenderer;
        /**
         * Constructs a new ClientCallback.
         *
         * @param renderer The ConsoleTuiRenderer used to render updates.
         */
        public ClientCallback(ConsoleTuiRenderer renderer) { this.tuiRenderer = renderer; }

        /**
         * Receives updates from the server and forwards them to the TUI renderer.
         *
         * @param update The serialized game update received from the server.
         */
        @Override
        public void onUpdate(SerializedUpdate update) {
            if (update != null) tuiRenderer.renderServerUpdate(update);
        }
    }
}
