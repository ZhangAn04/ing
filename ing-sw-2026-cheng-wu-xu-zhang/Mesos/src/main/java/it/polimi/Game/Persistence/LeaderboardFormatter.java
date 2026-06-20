package it.polimi.Game.Persistence;

import java.util.List;

/**
 * Formats persistent leaderboard rows for text-based clients and GUI dialogs.
 */
public final class LeaderboardFormatter {

    private LeaderboardFormatter() {
    }

    /**
     * Formats a full leaderboard for a given player count.
     *
     * @param entries leaderboard rows.
     * @param playerCount player count used as filter.
     * @return user-facing leaderboard text.
     */
    public static String format(List<LeaderboardEntry> entries, int playerCount) {
        if (entries == null || entries.isEmpty()) {
            return "No leaderboard entries for " + playerCount + "-player games.";
        }

        StringBuilder sb = new StringBuilder("Leaderboard for ")
                .append(playerCount)
                .append("-player games:");
        for (LeaderboardEntry entry : entries) {
            sb.append("\n#")
                    .append(entry.position())
                    .append(" ")
                    .append(entry.nickname())
                    .append(" pp=")
                    .append(entry.prestigePoints());
            if (entry.endedAt() != null) {
                sb.append(" date=").append(entry.endedAt());
            }
        }
        return sb.toString();
    }
}
