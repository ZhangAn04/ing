package it.polimi.Game.Persistence;

import java.time.Instant;
import java.util.List;

/**
 * Immutable representation of a finished match result.
 */
public final class GameResult {

	private final String matchId;
	private final Instant endedAt;
	private final String finalReport;
	private final List<PlayerScore> playerScores;

	/**
	 * Constructs a new GameResult holding the final scores and reports of a match.
	 *
	 * @param matchId The unique identifier of the match.
	 * @param endedAt The timestamp when the match finished.
	 * @param finalReport A text report summarizing the match events.
	 * @param playerScores The list of player scores.
	 */
	public GameResult(String matchId, Instant endedAt, String finalReport, List<PlayerScore> playerScores) {
		this.matchId = matchId;
		this.endedAt = endedAt;
		this.finalReport = finalReport;
		this.playerScores = playerScores;
	}

	/**
	 * Returns the unique match identifier.
	 *
	 * @return The match ID.
	 */
	public String matchId() {
		return matchId;
	}

	/**
	 * Returns the timestamp when the match ended.
	 *
	 * @return The end timestamp.
	 */
	public Instant endedAt() {
		return endedAt;
	}

	/**
	 * Returns the final textual report of the match.
	 *
	 * @return The final report.
	 */
	public String finalReport() {
		return finalReport;
	}

	/**
	 * Returns the list of final player scores.
	 *
	 * @return The list of player scores.
	 */
	public List<PlayerScore> playerScores() {
		return playerScores;
	}
}
