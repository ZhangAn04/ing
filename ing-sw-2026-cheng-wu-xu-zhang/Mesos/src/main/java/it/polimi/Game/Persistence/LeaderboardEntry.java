package it.polimi.Game.Persistence;

import java.time.Instant;

/**
 * Immutable row of the persistent leaderboard for matches with the same player count.
 */
public final class LeaderboardEntry {

    private final int position;
    private final String nickname;
    private final int prestigePoints;
    private final Instant endedAt;
    private final int playerCount;
    private final String matchId;

    /**
     * Creates a leaderboard row.
     *
     * @param position ranking position in the filtered leaderboard.
     * @param nickname player nickname.
     * @param prestigePoints final prestige points.
     * @param endedAt match end timestamp.
     * @param playerCount number of players in the match.
     * @param matchId match identifier.
     */
    public LeaderboardEntry(int position, String nickname, int prestigePoints,
                            Instant endedAt, int playerCount, String matchId) {
        this.position = position;
        this.nickname = nickname;
        this.prestigePoints = prestigePoints;
        this.endedAt = endedAt;
        this.playerCount = playerCount;
        this.matchId = matchId;
    }

    public int position() {
        return position;
    }

    public String nickname() {
        return nickname;
    }

    public int prestigePoints() {
        return prestigePoints;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public int playerCount() {
        return playerCount;
    }

    public String matchId() {
        return matchId;
    }
}
