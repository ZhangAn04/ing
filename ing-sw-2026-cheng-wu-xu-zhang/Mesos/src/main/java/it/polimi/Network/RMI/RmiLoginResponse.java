package it.polimi.Network.RMI;

import java.io.Serializable;

/**
 * DTO for RMI login outcomes.
 */
public class RmiLoginResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Whether the login request was accepted. */
    private boolean success;
    /** Human-readable login result. */
    private String message;

    /**
     * Default constructor for serialization frameworks.
     */
    public RmiLoginResponse() {
    }

    /**
     * Creates a login response.
     *
     * @param success true if login succeeds.
     * @param message explanatory message.
     */
    public RmiLoginResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    /**
     * Reports whether the login request was accepted.
     *
     * @return true when login is accepted.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets whether the login request was accepted.
     *
     * @param success true when login is accepted.
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Returns the human-readable login result.
     *
     * @return login message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the human-readable login result.
     *
     * @param message login message.
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
