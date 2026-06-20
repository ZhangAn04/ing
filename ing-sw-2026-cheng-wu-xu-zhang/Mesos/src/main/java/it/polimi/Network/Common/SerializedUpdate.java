package it.polimi.Network.Common;

import java.io.Serializable;

/**
 * Represents a structured update or notification sent from the server to the client.
 * <p>
 * This class acts as the standard data transfer object (DTO) for server-to-client 
 * communication, complementing {@link ActionMessage}. It encapsulates the outcome of user actions,
 * global lobby notifications, and synchronized game state updates.
 * </p>
 */
public class SerializedUpdate implements Serializable {
    /** The classification of the update (e.g., "LOGIN_RESULT", "STATUS_UPDATE", "NOTIFICATION"). */
    private String type;
    
    /** The text payload or serialized state associated with the update. */
    private String content;
    
    /** A flag indicating whether the update represents a successful action or an error/rejection. */
    private boolean success;

    /**
     * Default constructor.
     * Required by the Jackson library for JSON deserialization.
     */
    public SerializedUpdate() {}

    /**
     * Constructs a new SerializedUpdate with the specified parameters.
     *
     * @param type    The specific type of update being sent.
     * @param content The descriptive payload or state data.
     * @param success {@code true} if the update relates to a successful server operation, {@code false} otherwise.
     */
    public SerializedUpdate(String type, String content, boolean success) {
        this.type = type;
        this.content = content;
        this.success = success;
    }

    /**
     * Gets the type of the update.
     *
     * @return The update type string.
     */
    public String getType() { return type; }

    /**
     * Sets the type of the update.
     *
     * @param type The update type string.
     */
    public void setType(String type) { this.type = type; }

    /**
     * Gets the textual content or payload of the update.
     *
     * @return The message or serialized state.
     */
    public String getContent() { return content; }

    /**
     * Sets the textual content or payload of the update.
     *
     * @param content The message or serialized state.
     */
    public void setContent(String content) { this.content = content; }

    /**
     * Checks if the update denotes a successful operation.
     *
     * @return {@code true} if successful, {@code false} if it represents an error or denial.
     */
    public boolean isSuccess() { return success; }

    /**
     * Sets the success flag of the update.
     *
     * @param success {@code true} for a successful operation, {@code false} otherwise.
     */
    public void setSuccess(boolean success) { this.success = success; }
}
