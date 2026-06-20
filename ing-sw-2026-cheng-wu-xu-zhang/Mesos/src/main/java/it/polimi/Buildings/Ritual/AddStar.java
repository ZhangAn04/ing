package it.polimi.Buildings.Ritual;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.Building;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;

/**
 * Represents a ritual building that grants stars to the player.
 * Stars are used to track progress or determine outcomes during Shamanic Ritual events.
 */
public class AddStar extends Building {
    private int star;

    /**
     * Constructs an AddStar ritual building with the specified properties.
     *
     * @param era the era the building belongs to
     * @param position the position on the board
     * @param cost the resource cost to build
     * @param points the prestige points awarded by the building
     * @param star the number of stars this building adds to the player's ritual progress
     */
    @JsonCreator
    public AddStar(
         @JsonProperty("era") Era era,
         @JsonProperty("position") Position position,
         @JsonProperty("cost") int cost,
         @JsonProperty("points") int points,
         @JsonProperty("numStar") int star) {

        super(era, position, cost, points);
        this.star = star;
    }

    /**
     * Applies the ritual effect by contributing stars to the player's tally.
     *
     * @param player the player who owns the building
     * @param ritualData the shared data structure tracking stars for all players in the ritual
     */
    public void applyRitualEffect(Player player, RitualData ritualData) {

        ritualData.addStar(player, star);
        // Adds stars to the owner of this building.
    }
}
