package it.polimi.Buildings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.*;
import it.polimi.Abstract.Character;

import it.polimi.Constants.*;
import it.polimi.Constants.Position;

/**
 * End-game building that either multiplies Builder prestige or awards fixed points per Builder.
 */
public class EndGame_Builder_Add extends Building {

    /** The multiplier applied to normal Builder prestige; zero for fixed-point cards. */
    private int multiplier;
    /** Fixed points awarded for every Builder, used by cards with a numeric reward. */
    private int pointsPerBuilder;

    /**
     * Creates the builder multiplier end-game building.
     *
     * @param era The era this building belongs to.
     * @param position The initial board position.
     * @param cost The construction cost.
     * @param points The base points granted.
     * @param multiplier The scoring multiplier for builders.
     */
    public EndGame_Builder_Add(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points,
            @JsonProperty("multiplier") int multiplier) {

        this(era, position, cost, points, multiplier, 0);
    }

    @JsonCreator
    public EndGame_Builder_Add(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points,
            @JsonProperty("multiplier") int multiplier,
            @JsonProperty("pointsPerBuilder") int pointsPerBuilder) {

        super(era, position, cost, points);
        this.multiplier = multiplier;
        this.pointsPerBuilder = pointsPerBuilder;
    }

    /**
     * Calculates the final points for the given player based on their builder characters.
     *
     * @param g The player whose points are being calculated.
     * @return The total calculated points for this building.
     */
    public int calculateFinalPoints(Player g) {

        int totalPrestige = 0;
        int builderCount = 0;

        for (Character character : g.getCharacters()) {
            if (character.getType() == CharacterType.BUILDER) {
                builderCount++;
                totalPrestige += character.getPrestigePoints();
            }
        }

        if (pointsPerBuilder > 0) {
            return builderCount * pointsPerBuilder;
        }

        // Normal Builder prestige is scored separately by GameController.
        // Return only the extra amount needed to reach the requested multiplier.
        return totalPrestige * Math.max(0, multiplier - 1);
    }
}
