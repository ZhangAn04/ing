package it.polimi.Network.Server.RMI;

import it.polimi.Network.Server.Lobby;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Adapter that boots and manages the RMI server-side transport.
 */
public class RmiServerAdapter {
    private final int rmiPort;
    private final String bindingName;
    private final Lobby lobby;

    private Registry registry;
    private RmiGameService gameService;
    private boolean ownsRegistry;

    /**
     * Creates an adapter for the RMI transport.
     *
     * @param rmiPort     registry port.
     * @param bindingName binding name used in the registry.
     * @param lobby       shared lobby.
     */
    public RmiServerAdapter(int rmiPort, String bindingName, Lobby lobby) {
        this.rmiPort = rmiPort;
        this.bindingName = bindingName;
        this.lobby = lobby;
    }

    /**
     * Starts registry/service export and binds the remote game service.
     *
     * @throws RemoteException if startup fails.
     */
    public synchronized void start() throws RemoteException {
        if (gameService != null) {
            return;
        }

        try {
            registry = LocateRegistry.createRegistry(rmiPort);
            ownsRegistry = true;
        } catch (ExportException e) {
            registry = LocateRegistry.getRegistry(rmiPort);
            ownsRegistry = false;
        }

        gameService = new RmiGameService(lobby);
        registry.rebind(bindingName, gameService);
    }

    /**
     * Unbinds the service and shuts down the registry if owned.
     */
    public synchronized void stop() {
        if (gameService == null) {
            return;
        }

        try {
            registry.unbind(bindingName);
        } catch (Exception ignored) {
            // Nothing to do: already unbound or registry dead.
        }

        try {
            UnicastRemoteObject.unexportObject(gameService, true);
        } catch (Exception ignored) {
            // Nothing to do: export already lost.
        }

        gameService = null;

        if (ownsRegistry) {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (Exception ignored) {
                // Nothing to do: registry may still be in use.
            }
        }

        registry = null;
        ownsRegistry = false;
    }
}
