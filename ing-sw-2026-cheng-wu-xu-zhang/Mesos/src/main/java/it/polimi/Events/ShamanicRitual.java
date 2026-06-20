package it.polimi.Events;

import it.polimi.Abstract.Building;
import it.polimi.Abstract.Event;
import it.polimi.Abstract.EventContext;
import it.polimi.Abstract.RitualEventContext;
import it.polimi.Constants.Era;
import it.polimi.Constants.EventType;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import it.polimi.Buildings.Ritual.RitualData;
import it.polimi.Buildings.Ritual.Event_Less_Ritual;
import it.polimi.Buildings.Ritual.Event_Most_Ritual;
import java.util.List;

/**
 * Represents a Shamanic Ritual event in the game Mesos.
 * This event scores players based on their ritual star totals, typically rewarding the 
 * highest count and potentially penalizing or differently rewarding the lowest count.
 */
public class ShamanicRitual extends Event {

    /** Points granted to the player(s) with the maximum number of stars. */
    private final int maxPoints;
    /** Points granted to the player(s) with the minimum number of stars. */
    private final int minPoints;

    /**
     * Constructs a ShamanicRitual event with specified era, position, and scoring parameters.
     *
     * @param era       The {@link Era} in which this event occurs.
     * @param position  The {@link Position} of the event card.
     * @param maxPoints Points for the player with the most ritual stars.
     * @param minPoints Points for the player with the fewest ritual stars.
     */
    public ShamanicRitual(Era era, Position position, int maxPoints, int minPoints) {
        super(era, position, EventType.RITUAL);
        this.maxPoints = maxPoints;
        this.minPoints = minPoints;
    }

    /**
     * Returns the points granted to the player with the highest star count.
     *
     * @return The max points value.
     */
    public int getMaxPoints() {
        return maxPoints;
    }

    /**
     * Returns the points granted to the player with the lowest star count.
     *
     * @return The min points value.
     */
    public int getMinPoints() {
        return minPoints;
    }

    /**
     * Applies the ritual effect to all players in the context.
     * Expects a {@link RitualEventContext} to access ritual-specific data such as star counts.
     * Players matching the global max or min star counts are rewarded accordingly.
     *
     * @param context The {@link EventContext} (must be an instance of {@link RitualEventContext}).
     */
    @Override
    public void applyEffect(EventContext context) {
        RitualEventContext ritualContext = (RitualEventContext) context;
        List<Player> players = ritualContext.getPlayers();
        RitualData ritualData = ritualContext.getRitualData();

        // First pass: award max points
        for (Player player : players) {
            if (ritualData.getStar(player) == ritualData.getMaxStar()) {
                player.modifyPrestigePoints(maxPoints);
                
                // Extra bonus for having Event_Most_Ritual
                for (Building building : player.getBuildings()) {
                    if (building instanceof Event_Most_Ritual) {
                        // Normally this would be handled inside the building, 
                        // but since the multiplier applies to the gained points:
                        ((Event_Most_Ritual) building).applyRitualEffect(player, ritualData, this);
                    }
                }
            }
        }

        // Second pass: penalize min points
        for (Player player : players) {
            if (ritualData.getStar(player) == ritualData.getMinStar()) {
                boolean protectedFromPenalty = player.getBuildings().stream()
                        .anyMatch(b -> b instanceof Event_Less_Ritual);
                
                if (!protectedFromPenalty) {
                    player.modifyPrestigePoints(minPoints);
                }
            }
        }
    }
}
