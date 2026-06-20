package it.polimi.Buildings.Ritual;

import java.util.*;

import it.polimi.Game.Elements.Player;

/**
 * Data container for tracking player participation and progress in a Shamanic Ritual.
 * Stores star counts for each player and provides utility methods to determine min/max counts.
 */
public class RitualData {

    /** Creates an empty ritual score table. */
    public RitualData() {
    }
    private Map<Player, Integer> star = new HashMap<>();

    /**
     * Increments the star count for a specific player.
     *
     * @param player the player earning stars
     * @param amount the number of stars to add
     */
    public void addStar(Player player, int amount) {
        star.put(player, star.getOrDefault(player, 0) + amount);
    }

    /**
     * Retrieves the current star count for a specific player.
     *
     * @param player the player whose stars are requested
     * @return the number of stars associated with the player
     */
    public int getStar(Player player) {
        return star.getOrDefault(player, 0);
    }

    /**
     * Calculates the minimum star count among all players tracked in this ritual.
     *
     * @return the lowest number of stars found; returns {@link Integer#MAX_VALUE} if no stars recorded
     */
    public int getMinStar() {
        int min = Integer.MAX_VALUE;

        for (int s : star.values()) {
            if (s < min) {
                min = s;
            }
        }

        return min;
    }

    /**
     * Calculates the maximum star count among all players tracked in this ritual.
     *
     * @return the highest number of stars found; returns {@link Integer#MIN_VALUE} if no stars recorded
     */
    public int getMaxStar() {
    int max = Integer.MIN_VALUE;

    for (int s : star.values()) {
        if (s > max) {
            max = s;
        }
    }

    return max;
}
}
