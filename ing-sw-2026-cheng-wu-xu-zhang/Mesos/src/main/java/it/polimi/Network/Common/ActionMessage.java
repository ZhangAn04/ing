package it.polimi.Network.Common;

import java.io.Serializable;

/**
 * Represents a discrete action or message sent from the client to the server.
 * <p>
 * This class serves as the standard data transfer object (DTO) for all client-to-server 
 * communication in the Mesos game. It is designed to be easily serialized to and from JSON 
 * using the Jackson library.
 * </p>
 */
public class ActionMessage implements Serializable {
    /** The type of action being requested (e.g., "LOGIN", "CHAT", "PLAY_CARD"). */
    private String type;
    
    /** The nickname of the player sending the action. */
    private String nickname;
    
    /** An optional numeric payload associated with the action (e.g., card index). */
    private int value;

    /** The ID of the room the player is trying to join/create or is currently in. */
    private int roomID;

    /** The secret PIN provided by the player for secure connection/reconnection. */
    private String pin;

    /** Number of players requested when creating a room. */
    private int maxPlayers;

    /**
     * Default constructor.
     * Required by the Jackson library for JSON deserialization.
     */
    public ActionMessage() {}

    /**
     * Constructs a new ActionMessage with the specified parameters.
     *
     * @param type     The type or command of the action.
     * @param nickname The nickname of the player initiating the action.
     * @param value    An optional numeric value related to the action.
     */
    public ActionMessage(String type, String nickname, int value) {
        this.type = type;
        this.nickname = nickname;
        this.value = value;
    }

    /**
     * Constructs a new ActionMessage including room and pin details.
     *
     * @param type     The type or command of the action.
     * @param nickname The nickname of the player.
     * @param value    An optional numeric value.
     * @param roomID   The room ID.
     * @param pin      The secret PIN.
     */
    public ActionMessage(String type, String nickname, int value, int roomID, String pin) {
        this(type, nickname, value, roomID, pin, 4);
    }

    /**
     * Constructs a new ActionMessage including room, pin, and room size details.
     *
     * @param type       The type or command of the action.
     * @param nickname   The nickname of the player.
     * @param value      An optional numeric value.
     * @param roomID     The room ID.
     * @param pin        The secret PIN.
     * @param maxPlayers Number of players requested for room creation.
     */
    public ActionMessage(String type, String nickname, int value, int roomID, String pin, int maxPlayers) {
        this.type = type;
        this.nickname = nickname;
        this.value = value;
        this.roomID = roomID;
        this.pin = pin;
        this.maxPlayers = maxPlayers;
    }

    /**
     * Gets the type of the action.
     *
     * @return A string representing the action type.
     */
    public String getType() { return type; }

    /**
     * Sets the type of the action.
     *
     * @param type A string representing the action type.
     */
    public void setType(String type) { this.type = type; }

    /**
     * Gets the nickname of the player who sent the action.
     *
     * @return The player's nickname.
     */
    public String getNickname() { return nickname; }

    /**
     * Sets the nickname of the player who sent the action.
     *
     * @param nickname The player's nickname.
     */
    public void setNickname(String nickname) { this.nickname = nickname; }

    /**
     * Gets the numeric value associated with the action.
     *
     * @return The numeric payload.
     */
    public int getValue() { return value; }

    /**
     * Sets the numeric value associated with the action.
     *
     * @param value The numeric payload.
     */
    public void setValue(int value) { this.value = value; }

    /**
     * Gets the room ID associated with this message.
     *
     * @return The room ID.
     */
    public int getRoomID() { return roomID; }

    /**
     * Sets the room ID associated with this message.
     *
     * @param roomID The room ID.
     */
    public void setRoomID(int roomID) { this.roomID = roomID; }

    /**
     * Gets the PIN associated with this message.
     *
     * @return The player's PIN.
     */
    public String getPin() { return pin; }

    /**
     * Sets the PIN associated with this message.
     *
     * @param pin The player's PIN.
     */
    public void setPin(String pin) { this.pin = pin; }

    /**
     * Gets the requested number of players for room creation.
     *
     * @return The requested player count.
     */
    public int getMaxPlayers() { return maxPlayers; }

    /**
     * Sets the requested number of players for room creation.
     *
     * @param maxPlayers The requested player count.
     */
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    /**
     * Returns a string representation of the ActionMessage.
     *
     * @return A formatted string detailing the type, nickname, and value.
     */
    @Override
    public String toString() {
        return "ActionMessage{" +
                "type='" + type + '\'' +
                ", nickname='" + nickname + '\'' +
                ", value=" + value +
                ", roomID=" + roomID +
                ", pin='" + pin + '\'' +
                ", players=" + maxPlayers +
                '}';
    }
}
