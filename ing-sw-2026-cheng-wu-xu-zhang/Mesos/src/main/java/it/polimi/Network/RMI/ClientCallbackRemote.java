package it.polimi.Network.RMI;

import it.polimi.Network.Common.SerializedUpdate;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI callback exposed by the client so the server can push asynchronous updates.
 */
public interface ClientCallbackRemote extends Remote {
    /**
     * Delivers a server update to the client.
     *
     * @param update update payload.
     * @throws RemoteException if the callback cannot be reached.
     */
    void onUpdate(SerializedUpdate update) throws RemoteException;
}
