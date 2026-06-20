package it.polimi.Buildings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Abstract.Building;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;

/**
 * Represents a building that provides an effect triggered at the end of a round or turn.
 * Typically, this effect allows the player to draw an additional card from the deck.
 */
public class Turn_End_Card extends Building {


    /**
     * Constructs a Turn_End_Card building with the specified properties.
     *
     * @param era the era the building belongs to
     * @param position the position on the board
     * @param cost the resource cost to build
     * @param points the prestige points awarded by the building
     */
    @JsonCreator
    public Turn_End_Card(
          @JsonProperty("era") Era era,
          @JsonProperty("position") Position position,
          @JsonProperty("cost") int cost,
          @JsonProperty("points") int points) {

        super(era, position, cost, points);
    }

    /**
     * Applies the end-of-turn effect.
     * This method is responsible for granting the player an additional card from the deck.
     */
    public void applyCardEffect() {
        // Draw one additional card.
    }
}
