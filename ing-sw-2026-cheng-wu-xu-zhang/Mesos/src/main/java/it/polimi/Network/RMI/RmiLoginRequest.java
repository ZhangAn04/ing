package it.polimi.Network.RMI;

import java.io.Serializable;

/**
 * DTO for RMI login requests.
 */
public class RmiLoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Requested login action. */
    private String type;
    /** Requested nickname. */
    private String nickname;
    /** Target room identifier. */
    private int roomID;
    /** Room access PIN. */
    private String pin;
    /** Requested room capacity. */
    private int maxPlayers;

    /**
     * Default constructor for serialization frameworks.
     */
    public RmiLoginRequest() {
    }

    /**
     * Creates a simple login request (Legacy).
     *
     * @param nickname desired nickname.
     */
    public RmiLoginRequest(String nickname) {
        this.nickname = nickname;
        this.type = "JOIN_ROOM";
    }

    /**
     * Creates a login request with room ID and PIN.
     *
     * @param type     The login action type (e.g., "CREATE_ROOM", "JOIN_ROOM", "RECONNECT_ROOM").
     * @param nickname desired nickname.
     * @param roomID   the room ID to join or create.
     * @param pin      the secret PIN for connection/reconnection.
     */
    public RmiLoginRequest(String type, String nickname, int roomID, String pin) {
        this(type, nickname, roomID, pin, 4);
    }

    /**
     * Creates a login request with room ID, PIN, and room size.
     *
     * @param type       The login action type (e.g., "CREATE_ROOM", "JOIN_ROOM", "RECONNECT_ROOM").
     * @param nickname   desired nickname.
     * @param roomID     the room ID to join or create.
     * @param pin        the secret PIN for connection/reconnection.
     * @param maxPlayers number of players requested for room creation.
     */
    public RmiLoginRequest(String type, String nickname, int roomID, String pin, int maxPlayers) {
        this.type = type;
        this.nickname = nickname;
        this.roomID = roomID;
        this.pin = pin;
        this.maxPlayers = maxPlayers;
    }

    /**
     * Returns the requested login action.
     *
     * @return the login action type.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the requested login action.
     *
     * @param type the login action type.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the requested nickname.
     *
     * @return requested nickname.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Sets the requested nickname.
     *
     * @param nickname requested nickname.
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * Returns the target room identifier.
     *
     * @return requested room ID.
     */
    public int getRoomID() {
        return roomID;
    }

    /**
     * Sets the target room identifier.
     *
     * @param roomID requested room ID.
     */
    public void setRoomID(int roomID) {
        this.roomID = roomID;
    }

    /**
     * Returns the room access PIN.
     *
     * @return secret PIN.
     */
    public String getPin() {
        return pin;
    }

    /**
     * Sets the room access PIN.
     *
     * @param pin secret PIN.
     */
    public void setPin(String pin) {
        this.pin = pin;
    }

    /**
     * Returns the requested room capacity.
     *
     * @return number of players requested when creating a room.
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Sets the requested room capacity.
     *
     * @param maxPlayers number of players requested when creating a room.
     */
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
