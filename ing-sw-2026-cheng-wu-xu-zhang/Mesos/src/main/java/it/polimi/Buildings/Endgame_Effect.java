package it.polimi.Buildings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import it.polimi.Abstract.Character;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.*;
import it.polimi.Constants.*;

/**
 * Building effect that grants end-game points based on matching character targets.
 * A bonus is awarded if the player owns at least a certain number of characters of specific types.
 */
public class Endgame_Effect extends Building {

    /** The list of character types required for the bonus. */
    private List<CharacterType> target;
    /** The additional bonus points granted if the targets are met. */
    private int bonusPoints;
    /** The required number of characters for each target type. */
    private int numTarget;

    /**
     * Creates an end-game effect building.
     *
     * @param era The era this building belongs to.
     * @param position The initial board position.
     * @param cost The construction cost.
     * @param points The base points granted.
     * @param target The target character types.
     * @param numTarget The required count per target type.
     * @param bonusPoints The bonus points granted.
     */
    @JsonCreator
    public Endgame_Effect(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points,
            @JsonProperty("target") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<CharacterType> target,
            @JsonProperty("numTarget") int numTarget,
            @JsonProperty("bonusPoints") int bonusPoints) {

        super(era, position, cost, points);
        this.target = target;
        this.bonusPoints = bonusPoints;
        this.numTarget = numTarget;
    }

    /**
     * Returns the base end-game points for this building.
     *
     * @return The base points value.
     */
    @Override
    public int getPoints() {

        return super.getPoints();
    }

    /**
     * Calculates the final points for the given player, adding the bonus if targets are satisfied.
     *
     * @param player The player whose points are being calculated.
     * @return The total calculated points.
     */
    public int calculateFinalPoints(Player player) {

        if (player == null) {
            return getPoints();
        }

        // If no targets are specified, this building grants its bonus unconditionally.
        if (target == null || target.isEmpty()) {
            return getPoints() + bonusPoints;
        }

        if (numTarget <= 0) {
            return getPoints();
        }

        Map<CharacterType, Integer> counts = new EnumMap<>(CharacterType.class);

        for (CharacterType type : target) {
            counts.put(type, 0);
        }

        for (Character character : player.getCharacters()) {
            if (character == null) {
                continue;
            }
            CharacterType type = character.getType();
            if (counts.containsKey(type)) {
                counts.put(type, counts.get(type) + 1);
            }
        }

        // The bonus can be scored multiple times: one time for each complete set of targets.
        int times = Integer.MAX_VALUE;
        for (CharacterType type : target) {
            times = Math.min(times, counts.get(type) / numTarget);
        }
        if (times == Integer.MAX_VALUE) {
            times = 0;
        }

        return getPoints() + (bonusPoints * times);
    }

}
