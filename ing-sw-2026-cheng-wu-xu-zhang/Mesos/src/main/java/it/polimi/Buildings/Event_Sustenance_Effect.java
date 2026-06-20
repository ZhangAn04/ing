package it.polimi.Buildings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.Building;
import it.polimi.Abstract.Character;
import it.polimi.Constants.Era;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Position;

/**
 * Building effect that reduces sustenance cost based on matching characters.
 * Provides a food discount for each character of the target type owned by the player.
 */
public class Event_Sustenance_Effect extends Building {

    /** The food discount amount per target character. */
    private int food;
    /** The character type that grants the discount. */
    private CharacterType target;

    /**
     * Creates a sustenance effect building.
     *
     * @param era The era this building belongs to.
     * @param position The initial board position.
     * @param cost The construction cost.
     * @param points The base points granted.
     * @param food The discount amount per character.
     * @param target The character type that triggers the discount.
     */
    @JsonCreator
    public Event_Sustenance_Effect(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points,
            @JsonProperty("discount") int food,
            @JsonProperty("target") CharacterType target) {

        super(era, position, cost, points);
        this.food = food;
        this.target = target;
    }

    /**
     * Returns the discount based on the number of matching target characters.
     *
     * @param player The player whose characters are being checked.
     * @return The total food discount value.
     */
    public int applyDiscountEffect(Player player) {
        int count = 0;

        for (Character character : player.getCharacters()) {
            if (character.getType() == target) {
                count++;
            }
        }
        return count * food;
    }

}
