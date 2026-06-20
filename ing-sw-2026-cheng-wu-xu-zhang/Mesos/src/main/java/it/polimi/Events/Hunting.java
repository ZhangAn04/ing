package it.polimi.Events;

import it.polimi.Abstract.Event;
import it.polimi.Abstract.EventContext;
import it.polimi.Constants.*;
import it.polimi.Game.Elements.Player;
import java.util.List;

/**
 * Represents a Hunting event in the game Mesos.
 * This event grants food and prestige points to players based on the number of Hunter characters in their tribe.
 * It scales according to the specific multipliers defined for the event instance.
 */
public class Hunting extends Event {

    /** The food reward multiplier per Hunter. */
    private final int food;
    /** The prestige point reward multiplier per Hunter. */
    private final int points;

    /**
     * Constructs a Hunting event with specified era, position, and reward values.
     *
     * @param era      The {@link Era} in which this event occurs.
     * @param position The {@link Position} of the event card.
     * @param food     The amount of food granted per Hunter owned by the player.
     * @param points   The amount of prestige points granted per Hunter owned by the player.
     */
    public Hunting(Era era, Position position, int food, int points) {
        super(era, position, EventType.HUNTING);
        this.food = food;
        this.points = points;
    }

    /**
     * Returns the food reward multiplier per Hunter.
     *
     * @return The food multiplier value.
     */
    public int getFood() {
        return food;
    }

    /**
     * Returns the prestige point reward multiplier per Hunter.
     *
     * @return The points multiplier value.
     */
    public int getPoints() {
        return points;
    }

    /**
     * Applies the hunting effect to every player within the provided event context.
     * For each player, the number of characters of type {@link CharacterType#HUNTER} is counted,
     * and the rewards are distributed accordingly.
     *
     * @param context The {@link EventContext} containing the list of players to process.
     */
    @Override
    public void applyEffect(EventContext context) {
        List<Player> players = context.getPlayers();
        
        for (Player player : players) {
            // Count the player's hunters
            long hunterCount = player.getCharacters().stream()
                    .filter(c -> c.getType() == CharacterType.HUNTER)
                    .count();
            
            // Add food and points based on the number of hunters
            player.receiveFood((int) (food * hunterCount));
            player.modifyPrestigePoints((int) (points * hunterCount));
        }
    }
}
