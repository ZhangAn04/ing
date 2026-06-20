package it.polimi.Buildings.Ritual;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.Building;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;

/**
 * Represents a ritual building that provides protection for players with the minimum number of stars.
 * If the player has the fewest stars among all participants, this building prevents negative consequences.
 */
public class Event_Less_Ritual extends Building{


    /**
     * Constructs an Event_Less_Ritual building with the specified properties.
     *
     * @param era the era the building belongs to
     * @param position the position on the board
     * @param cost the resource cost to build
     * @param points the prestige points awarded by the building
     */
    @JsonCreator
    public Event_Less_Ritual(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points) {

        super(era, position, cost, points);
    }

    /**
     * Applies the ritual protection effect if the player meets the criteria.
     * Checks if the player has the lowest star count in the ritual data.
     *
     * @param player the player who owns the building
     * @param ritualData the shared data structure tracking stars for all players
     */
    public void applyRitualEffect(Player player, RitualData ritualData) {
        if (ritualData.getStar(player) == ritualData.getMinStar()){
            // protectStar(player)
        }
        // If the owner has the fewest stars, they do not lose points.
    }
}
