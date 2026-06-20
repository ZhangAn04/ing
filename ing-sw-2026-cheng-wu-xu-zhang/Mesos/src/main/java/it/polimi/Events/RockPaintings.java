package it.polimi.Events;

import it.polimi.Abstract.Event;
import it.polimi.Abstract.EventContext;
import it.polimi.Constants.Era;
import it.polimi.Constants.EventType;
import it.polimi.Constants.Position;
import it.polimi.Constants.CharacterType;
import it.polimi.Game.Elements.Player;
import java.util.List;

/**
 * Represents a Rock Paintings event in the game Mesos.
 * This event rewards players with prestige points per artist if they meet the high threshold,
 * and penalizes them if they fall at or below the low threshold.
 */
public class RockPaintings extends Event {

    private final int thresholdHigh;
    private final int thresholdLow;
    private final int ppIfMet;
    private final int ppIfNotMet;

    /**
     * Constructs a RockPaintings event with specified era, position, and thresholds.
     *
     * @param era           The {@link Era} in which this event occurs.
     * @param position      The {@link Position} of the event card.
     * @param thresholdHigh The required number of Artists to qualify for positive points.
     * @param thresholdLow  The maximum number of Artists that results in a penalty.
     * @param ppIfMet       The amount of prestige points granted PER ARTIST upon meeting the high threshold.
     * @param ppIfNotMet    The penalty applied if the artist count is at most {@code thresholdLow}.
     */
    public RockPaintings(Era era, Position position, int thresholdHigh, int thresholdLow, int ppIfMet, int ppIfNotMet) {
        super(era, position, EventType.ROCK_PAINTINGS);
        this.thresholdHigh = thresholdHigh;
        this.thresholdLow = thresholdLow;
        this.ppIfMet = ppIfMet;
        this.ppIfNotMet = ppIfNotMet;
    }

    /**
     * Returns the high artist threshold required to gain bonus points.
     *
     * @return The high threshold.
     */
    public int getThresholdHigh() {
        return thresholdHigh;
    }

    /**
     * Returns the low artist threshold below which players suffer a penalty.
     *
     * @return The low threshold.
     */
    public int getThresholdLow() {
        return thresholdLow;
    }

    /**
     * Returns the prestige points gained per artist if the high threshold is met.
     *
     * @return The points if met.
     */
    public int getPpIfMet() {
        return ppIfMet;
    }

    /**
     * Returns the prestige points lost if the low threshold is met.
     *
     * @return The points lost if not met.
     */
    public int getPpIfNotMet() {
        return ppIfNotMet;
    }

    /**
     * Applies the rock paintings effect to all players in the provided context.
     * Checks each player's tribe for characters of type {@link CharacterType#ARTIST}.
     * If the count meets or exceeds thresholdHigh, the player receives prestige points per artist.
     * If the count is at or below thresholdLow, the player loses prestige points.
     *
     * @param context The {@link EventContext} containing the players to evaluate.
     */
    @Override
    public void applyEffect(EventContext context) {
        List<Player> players = context.getPlayers();
        
        for (Player player : players) {
            long artistCount = player.getCharacters().stream()
                    .filter(c -> c.getType() == CharacterType.ARTIST)
                    .count();
            
            if (artistCount >= thresholdHigh) {
                player.modifyPrestigePoints((int) (artistCount * ppIfMet));
            } else if (artistCount <= thresholdLow) {
                player.modifyPrestigePoints(ppIfNotMet); // Penalty is usually a negative number
            }
        }
    }
}
