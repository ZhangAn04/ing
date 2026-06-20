package it.polimi.Buildings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Abstract.Building;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;

/**
 * Represents a building that provides an effect triggered at the beginning of a round or turn.
 * The effect typically depends on the player's order or position in the current turn.
 */
public class Turn_Start_Effect extends Building {

    /**
     * Constructs a Turn_Start_Effect building with the specified properties.
     *
     * @param era the era the building belongs to
     * @param position the position on the board
     * @param cost the resource cost to build
     * @param points the prestige points awarded by the building
     */
    @JsonCreator
    public Turn_Start_Effect(
          @JsonProperty("era") Era era,
          @JsonProperty("position") Position position,
          @JsonProperty("cost") int cost,
          @JsonProperty("points") int points) {

        super(era, position, cost, points);
    }

    /**
     * Applies the turn-start effect based on play order.
     * This method evaluates bonuses or penalties relative to the owner's position
     * in the turn order.
     */
    public void applyOrderEffect() {
        // Apply order-based bonus depending on the owner's position.
    }
}
