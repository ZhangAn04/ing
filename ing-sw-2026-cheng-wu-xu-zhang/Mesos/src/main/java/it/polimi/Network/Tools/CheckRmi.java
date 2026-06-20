package it.polimi.Network.Tools;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Small command-line utility that checks whether the RMI binding is reachable.
 */
public class CheckRmi {

    /** Creates an RMI connectivity checker. */
    public CheckRmi() {
    }
    /**
     * Resolves the registry and performs a lookup on the provided binding.
     *
     * @param args optional arguments: host, port, binding name.
     * @throws Exception if lookup fails unexpectedly.
     */
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
        String binding = args.length > 2 ? args[2] : "MesosGameService";

        Registry registry = LocateRegistry.getRegistry(host, port);
        try {
            Object obj = registry.lookup(binding);
            System.out.println("Lookup success: " + obj.getClass().getName());
        } catch (Exception e) {
            System.err.println("Lookup failed: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(2);
        }
    }
}
