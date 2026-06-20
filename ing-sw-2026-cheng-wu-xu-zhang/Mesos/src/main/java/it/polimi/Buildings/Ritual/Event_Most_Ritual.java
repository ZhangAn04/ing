package it.polimi.Buildings.Ritual;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.Building;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Events.ShamanicRitual;

/**
 * Represents a ritual building that rewards the player with the highest star count.
 * Provides a multiplier-based prestige point bonus during the Shamanic Ritual.
 */
public class Event_Most_Ritual extends Building{
    private int multiplier;

    /**
     * Constructs an Event_Most_Ritual building with the specified properties.
     *
     * @param era the era the building belongs to
     * @param position the position on the board
     * @param cost the resource cost to build
     * @param points the prestige points awarded by the building
     * @param multiplier the multiplier applied to the ritual's base points
     */
    @JsonCreator
    public Event_Most_Ritual(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points,
            @JsonProperty("multiplier") int multiplier) {

        super(era, position, cost, points);
        this.multiplier = multiplier;
    }

    /**
     * Applies the ritual bonus if the player has the maximum number of stars.
     *
     * @param player the player who owns the building
     * @param ritualData the shared data structure tracking stars for all players
     * @param ritual the ShamanicRitual event instance providing context and base points
     */
    public void applyRitualEffect(Player player, RitualData ritualData, ShamanicRitual ritual) {
        // If the owner has the most stars, they gain multiplied points.
        if (ritualData.getStar(player) == ritualData.getMaxStar()){
            player.modifyPrestigePoints(multiplier * ritual.getMaxPoints());
            // multiplier
        }
    }
}
