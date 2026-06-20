package it.polimi.Network.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import it.polimi.Game.Core.Game;
import it.polimi.Game.Core.GameController;
import it.polimi.Network.Config.NetworkSettings;
import it.polimi.Network.Server.RMI.RmiServerAdapter;
import java.rmi.RemoteException;

/**
 * The main entry point for the Mesos server.
 * <p>
 * This class is responsible for:
 * <ul>
 *     <li>Managing the {@link Lobby} for player connections and multiple matches.</li>
 *     <li>Starting either Socket or RMI transport based on startup configuration.</li>
 * </ul>
 */
public class ServerMain {

    /** Creates a server entry point. */
    public ServerMain() {
    }
    /** The shared lobby instance that manages player nicknames, game rooms, and connection state. */
    private static final Lobby lobby = new Lobby();

    /**
     * Starts the server application.
     * <p>
     * Startup supports both protocol-first and legacy socket arguments:
     * <ul>
     *     <li>{@code socket [port]}</li>
     *     <li>{@code rmi [registryPort]}</li>
     *     <li>{@code [port]} (legacy, interpreted as socket)</li>
     * </ul>
     * If no arguments are provided, protocol and ports are loaded from {@code network_config.json}.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            startBothTransports();
            return;
        }

        String protocol = resolveProtocol(args);

        if ("socket".equals(protocol)) {
            startSocketServer(resolveSocketPort(args));
            return;
        }

        if ("rmi".equals(protocol)) {
            startRmiServer(resolveRmiPort(args));
            return;
        }

        startSocketServer(resolveSocketPort(args));
        startRmiServer(resolveRmiPort(args));
    }

    /**
     * Starts a socket-based server on the specified port.
     * Accepts incoming client connections in an infinite loop and spawns a handler thread for each.
     *
     * @param portNumber the port to listen on
     */
    private static void startSocketServer(int portNumber) {
        System.out.println("Server Starting (socket) on port " + portNumber + "...");

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            System.out.println("Listening on port " + portNumber);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted a new connection from " + clientSocket.getRemoteSocketAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket, lobby);
                Thread t = new Thread(clientHandler);
                t.start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + portNumber);
            e.printStackTrace();
        }
    }

    /**
     * Starts socket and RMI transports together using the configured defaults.
     * The socket server runs on a daemon thread, while the RMI server keeps the JVM alive.
     */
    private static void startBothTransports() {
        Thread socketThread = new Thread(() -> startSocketServer(NetworkSettings.getPortFromJSON()));
        socketThread.setDaemon(true);
        socketThread.start();

        startRmiServer(NetworkSettings.getRmiPortFromJSON());
    }

    /**
     * Starts an RMI-based server on the specified registry port.
     * Registers the game service in the RMI registry and waits for incoming RMI calls.
     *
     * @param rmiPort the RMI registry port
     */
    private static void startRmiServer(int rmiPort) {
        String bindingName = NetworkSettings.getRmiBindingNameFromJSON();
        RmiServerAdapter adapter = new RmiServerAdapter(rmiPort, bindingName, lobby);

        System.out.println("Server Starting (rmi) on registry port " + rmiPort + "...");

        try {
            adapter.start();
            System.out.println("RMI service bound as '" + bindingName + "'.");
            Thread.sleep(Long.MAX_VALUE);
        } catch (RemoteException e) {
            System.err.println("Could not start RMI server on port " + rmiPort);
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            adapter.stop();
        }
    }

    /**
     * Determines the protocol to use for the server (socket or RMI).
     * Checks command-line arguments first; falls back to network_config.json if not specified.
     *
     * @param args command-line arguments
     * @return "socket" or "rmi"
     */
    private static String resolveProtocol(String[] args) {
        if (args.length >= 1) {
            if (isProtocolToken(args[0])) {
                return args[0].toLowerCase();
            }

            // Legacy behavior: a single non-protocol argument means socket mode.
            return "socket";
        }

        return NetworkSettings.getProtocolFromJSON();
    }

    /**
     * Resolves the socket server port number from command-line arguments or configuration.
     * Handles both protocol-first and legacy positional argument formats.
     * Falls back to the default port from network_config.json if parsing fails.
     *
     * @param args command-line arguments
     * @return the socket port number
     */
    private static int resolveSocketPort(String[] args) {
        int defaultPort = NetworkSettings.getPortFromJSON();

        if (args.length >= 1 && !isProtocolToken(args[0])) {
            return parsePortOrDefault(args[0], defaultPort);
        }

        if (args.length >= 2 && isProtocolToken(args[0])) {
            return parsePortOrDefault(args[1], defaultPort);
        }

        return defaultPort;
    }

    /**
     * Resolves the RMI registry port number from command-line arguments or configuration.
     * Expects the port after the protocol keyword in the argument list.
     * Falls back to the default port from network_config.json if parsing fails.
     *
     * @param args command-line arguments
     * @return the RMI registry port number
     */
    private static int resolveRmiPort(String[] args) {
        int defaultPort = NetworkSettings.getRmiPortFromJSON();

        if (args.length >= 2 && isProtocolToken(args[0])) {
            return parsePortOrDefault(args[1], defaultPort);
        }

        return defaultPort;
    }

    /**
     * Parses a port number from a string value.
     * Prints an error message and returns the default port if parsing fails.
     *
     * @param value the string representation of the port
     * @param defaultPort the port to use if parsing fails
     * @return the parsed port number or defaultPort
     */
    private static int parsePortOrDefault(String value, int defaultPort) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port provided. Using JSON/default value.");
            return defaultPort;
        }
    }

    /**
     * Checks if a string is a recognized protocol token ("socket" or "rmi").
     * Case-insensitive comparison.
     *
     * @param value the string to check
     * @return true if the value is "socket" or "rmi" (case-insensitive)
     */
    private static boolean isProtocolToken(String value) {
        return "socket".equalsIgnoreCase(value) || "rmi".equalsIgnoreCase(value);
    }
}
