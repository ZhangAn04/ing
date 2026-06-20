package it.polimi.Network.RMI;

import it.polimi.Network.Common.ActionMessage;
import it.polimi.Network.Common.SerializedUpdate;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI contract exposed by the server for Mesos clients.
 */
public interface GameServiceRemote extends Remote {
    /**
     * Registers a player nickname and callback in the current match lobby.
     *
     * @param request  login request payload.
     * @param callback callback exported by the client.
     * @return login outcome.
     * @throws RemoteException if the remote call fails.
     */
    RmiLoginResponse login(RmiLoginRequest request, ClientCallbackRemote callback) throws RemoteException;

    /**
     * Sends a player action to the game controller.
     *
     * @param action action payload.
     * @return synchronous server acknowledgment.
     * @throws RemoteException if the remote call fails.
     */
    SerializedUpdate sendAction(ActionMessage action) throws RemoteException;

    /**
     * Marks activity for an already connected player.
     *
     * @param nickname player nickname.
     * @throws RemoteException if the remote call fails.
     */
    void heartbeat(String nickname) throws RemoteException;

    /**
     * Disconnects a player from the match.
     *
     * @param nickname player nickname.
     * @throws RemoteException if the remote call fails.
     */
    void disconnect(String nickname) throws RemoteException;
}
