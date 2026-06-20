package it.polimi.Game.Persistence;

/**
 * Immutable per-player score snapshot at match end.
 */
public final class PlayerScore {

        private final String nickname;
        private final int prestigePoints;
        private final int food;
        private final int endgameBonus;
        private final int rank;
        private final boolean winner;

        /**
         * Constructs a PlayerScore snapshot representing a player's final performance.
         *
         * @param nickname The player's unique nickname.
         * @param prestigePoints The prestige points scored.
         * @param food The final remaining food count.
         * @param endgameBonus The final endgame bonus points.
         * @param rank The rank of the player (e.g., 1 for winner).
         * @param winner true if the player is a winner of the match, false otherwise.
         */
        public PlayerScore(String nickname, int prestigePoints, int food, int endgameBonus, int rank, boolean winner) {
                this.nickname = nickname;
                this.prestigePoints = prestigePoints;
                this.food = food;
                this.endgameBonus = endgameBonus;
                this.rank = rank;
                this.winner = winner;
        }

        /**
         * Returns the player's nickname.
         *
         * @return The nickname.
         */
        public String nickname() {
                return nickname;
        }

        /**
         * Returns the prestige points scored.
         *
         * @return The prestige points.
         */
        public int prestigePoints() {
                return prestigePoints;
        }

        /**
         * Returns the final food count.
         *
         * @return The food count.
         */
        public int food() {
                return food;
        }

        /**
         * Returns the final endgame bonus points.
         *
         * @return The endgame bonus.
         */
        public int endgameBonus() {
                return endgameBonus;
        }

        /**
         * Returns the rank of the player in the match.
         *
         * @return The player's rank.
         */
        public int rank() {
                return rank;
        }

        /**
         * Checks if the player won the match.
         *
         * @return true if the player is a winner, false otherwise.
         */
        public boolean winner() {
                return winner;
        }
}
