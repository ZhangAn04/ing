package it.polimi.Events;

import it.polimi.Abstract.Building;
import it.polimi.Abstract.Event;
import it.polimi.Abstract.EventContext;
import it.polimi.Buildings.Event_Sustenance_Effect;
import it.polimi.Characters.Gatherer;
import it.polimi.Constants.Era;
import it.polimi.Constants.EventType;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.Character;
import java.util.List;

/**
 * Represents a Sustenance event in the game Mesos.
 * This event requires players to pay food based on the size of their tribe (number of characters).
 * Failure to provide sufficient food results in a prestige point penalty.
 */
public class Sustenance extends Event {

    /** The food cost required per character owned by the player. */
    private final int food;
    /** The prestige point penalty applied per missing food unit or as a flat penalty. */
    private final int points;

    /**
     * Constructs a Sustenance event with specified era, position, and cost parameters.
     *
     * @param era      The {@link Era} in which this event occurs.
     * @param position The {@link Position} of the event card.
     * @param food     The food cost multiplier per character.
     * @param points   The penalty points applied if food requirements are not met.
     */
    public Sustenance(Era era, Position position, int food, int points) {
        super(era, position, EventType.SUSTENANCE);
        this.food = food;
        this.points = points;
    }

    /**
     * Returns the food cost multiplier per character.
     *
     * @return The food cost multiplier.
     */
    public int getFood() {
        return food;
    }

    /**
     * Returns the prestige penalty applied when food is insufficient.
     *
     * @return The penalty points.
     */
    public int getPoints() {
        return points;
    }

    /**
     * Applies the sustenance effect to all players in the provided context.
     * Each player must pay an amount of food equal to (tribe size * food multiplier).
     * If the player cannot afford the total cost, they suffer a prestige penalty.
     *
     * @param context The {@link EventContext} containing the players to process.
     */
    @Override
    public void applyEffect(EventContext context) {
        List<Player> players = context.getPlayers();
        
        for (Player player : players) {
            int characterCount = player.getCharacters().size();
            int totalFoodRequired = food * characterCount;

            int discount = 0;
            // Gatherer discounts
            for (Character c : player.getCharacters()) {
                if (c instanceof Gatherer) {
                    discount += ((Gatherer) c).getDiscount();
                }
            }

            // Building discounts
            for (Building b : player.getBuildings()) {
                if (b instanceof Event_Sustenance_Effect) {
                    discount += ((Event_Sustenance_Effect) b).applyDiscountEffect(player);
                }
            }

            int discountedRequirement = Math.max(0, totalFoodRequired - discount);

            if (player.getFood() >= discountedRequirement) {
                player.payFood(discountedRequirement, 0);
            } else {
                int foodShortage = discountedRequirement - player.getFood();
                player.payFood(player.getFood(), 0); // pay all food available
                
                int unfedCharacters = (int) Math.ceil((double) foodShortage / food);
                player.modifyPrestigePoints(-(unfedCharacters * points));
            }
        }
    }
}
