package it.polimi.Game.Persistence;

import java.util.Collections;
import java.util.List;

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

    /**
     * Returns the full leaderboard for matches completed with the given player count.
     *
     * @param playerCount number of players in the match.
     * @return leaderboard rows ordered by ranking.
     */
    default List<LeaderboardEntry> getLeaderboardByPlayerCount(int playerCount) {
        return Collections.emptyList();
    }

    /**
     * Returns one player's global position for a stored match result.
     *
     * @param matchId match identifier.
     * @param nickname player nickname.
     * @param playerCount number of players in the match.
     * @return ranking position, or 0 when unavailable.
     */
    default int getPlayerPosition(String matchId, String nickname, int playerCount) {
        return 0;
    }
}
