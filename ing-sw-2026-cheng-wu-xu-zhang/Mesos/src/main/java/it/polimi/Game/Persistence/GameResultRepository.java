package it.polimi.Game.Persistence;

/**
 * Persists final match results.
 * <p>
 * Implementations should be best-effort and must not crash the game flow.
 * </p>
 */
public interface GameResultRepository {

    /**
     * Stores the given match result.
     * <p>
     * Implementations should handle failures internally.
     * </p>
     *
     * @param result match result to store (may be null).
     */
    void saveGameResult(GameResult result);
}
