package it.polimi.Game.Core;

/**
 * Common interface for all Mesos game views (VirtualView, TUI, GUI).
 * <p>
 * This interface defines the contract for displaying information and requesting
 * input from the user. It allows the Controller and Model to interact with the
 * user interface in a decoupled manner, supporting multiple interface types.
 * </p>
 */
public interface GameView {
    /**
     * Displays a generic message or notification to the user.
     *
     * @param message The text content to be shown.
     */
    void showMessage(String message);

    /**
     * Updates the displayed representation of the current game state.
     *
     * @param status A string representation (textual or serialized) of the new state.
     */
    void updateGameStatus(String status);

    /**
     * Prompts the user to provide a unique nickname for the game session.
     */
    void askNickname();

    /**
     * Displays the outcome of a nickname registration or login attempt.
     *
     * @param success {@code true} if the login was successful, {@code false} otherwise.
     * @param reason  A descriptive message explaining the result (e.g., "Nickname taken").
     */
    void showLoginResult(boolean success, String reason);
}
