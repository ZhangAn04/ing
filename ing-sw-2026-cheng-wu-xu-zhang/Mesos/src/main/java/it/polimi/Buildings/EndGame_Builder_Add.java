package it.polimi.Buildings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.*;
import it.polimi.Abstract.Character;

import it.polimi.Constants.*;
import it.polimi.Constants.Position;

/**
 * End-game building that multiplies points contributed by builder characters.
 * The final bonus is proportional to the number of Builder-type characters owned by the player.
 */
public class EndGame_Builder_Add extends Building {

    /** The multiplier value applied to the total builder points. */
    private int multiplier;

    /**
     * Creates the builder multiplier end-game building.
     *
     * @param era The era this building belongs to.
     * @param position The initial board position.
     * @param cost The construction cost.
     * @param points The base points granted.
     * @param multiplier The scoring multiplier for builders.
     */
    @JsonCreator
    public EndGame_Builder_Add(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points,
            @JsonProperty("multiplier") int multiplier) {

        super(era, position, cost, points);
        this.multiplier = multiplier;
    }

    /**
     * Calculates the final points for the given player based on their builder characters.
     *
     * @param g The player whose points are being calculated.
     * @return The total calculated points for this building.
     */
    public int calculateFinalPoints(Player g) {

        int total = 0;

        for (Character character : g.getCharacters()) {
            if (character.getType() == CharacterType.BUILDER) {
                total += character.getPrestigePoints();
            }
        }

        return total * multiplier;
    }
}
