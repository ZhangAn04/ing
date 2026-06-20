package it.polimi.Game.Persistence;

/**
 * No-op implementation used when persistence is disabled.
 */
public final class NoOpGameResultRepository implements GameResultRepository {

    /** Shared repository instance that intentionally discards results. */
    public static final NoOpGameResultRepository INSTANCE = new NoOpGameResultRepository();

    /** Prevents construction outside the singleton instance. */
    private NoOpGameResultRepository() {
    }

    /**
     * No-op implementation of saveGameResult that does not persist any data.
     * Used when database persistence is disabled.
     *
     * @param result The game result (ignored).
     */
    @Override
    public void saveGameResult(GameResult result) {
        // Intentionally left blank.
    }
}
