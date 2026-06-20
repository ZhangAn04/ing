package it.polimi.Buildings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.polimi.Game.Elements.Player;
import it.polimi.Abstract.Building;
import it.polimi.Abstract.Character;
import it.polimi.Characters.Inventor;
import it.polimi.Constants.Era;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.InventorIcon;
import it.polimi.Constants.Position;

/**
 * Represents a building that provides a continuous in-game effect.
 * This specific implementation rewards the player with food (meat) when they possess
 * a required number of characters of specific types.
 */
public class InGame_Effect extends Building {
    private List<CharacterType> target;
    private int numTarget;
    private int meat;
    private int timesRewarded = -1; // -1 indicates not yet initialized

    /**
     * Constructs an InGame_Effect building with the specified properties.
     *
     * @param era the era the building belongs to
     * @param position the position on the board
     * @param cost the resource cost to build
     * @param points the prestige points awarded by the building
     * @param target the list of character types required to trigger the effect
     * @param numTarget the minimum number of characters required for each target type
     * @param meat the amount of food rewarded when requirements are met
     */
    @JsonCreator
    public InGame_Effect(
            @JsonProperty("era") Era era,
            @JsonProperty("position") Position position,
            @JsonProperty("cost") int cost,
            @JsonProperty("points") int points,
            @JsonProperty("target") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<CharacterType> target,
            @JsonProperty("numTarget") int numTarget,
            @JsonProperty("meat") int meat) {

        super(era, position, cost, points);
        this.target = target;
        this.numTarget = numTarget;
        this.meat = meat;
    }

    /**
     * Counts complete target sets currently owned by a player.
     *
     * @param player player whose cards are inspected
     * @return the number of complete sets
     */
    private int calculateCurrentSets(Player player) {
        if (target == null || target.isEmpty()) {
            return 0;
        }
        if (target.size() == 6) {
            // Set of 6 different character types
            Map<CharacterType, Integer> counts = new HashMap<>();
            for (CharacterType type : target) {
                counts.put(type, 0);
            }
            for (Character character : player.getCharacters()) {
                CharacterType type = character.getType();
                if (counts.containsKey(type)) {
                    counts.put(type, counts.get(type) + 1);
                }
            }
            int completeSets = Integer.MAX_VALUE;
            for (int c : counts.values()) {
                if (c < completeSets) completeSets = c;
            }
            return completeSets;
            
        } else if (target.size() == 1 && target.get(0) == CharacterType.INVENTOR) {
            // Pairs of IDENTICAL inventors (same icon)
            Map<InventorIcon, Integer> iconCounts = new HashMap<>();
            for (Character character : player.getCharacters()) {
                if (character instanceof Inventor) {
                    InventorIcon icon = ((Inventor) character).getIcon();
                    iconCounts.put(icon, iconCounts.getOrDefault(icon, 0) + 1);
                }
            }
            int pairs = 0;
            for (int count : iconCounts.values()) {
                pairs += count / 2;
            }
            return pairs;
        }

        int required = Math.max(1, numTarget);
        int sets = 0;
        for (CharacterType targetType : target) {
            int count = 0;
            for (Character character : player.getCharacters()) {
                if (character.getType() == targetType) {
                    count++;
                }
            }
            sets += count / required;
        }
        return sets;
    }

    /**
     * Applies the continuous effect to the specified player.
     * Checks if the player's tribe contains enough characters of the target types.
     * If all target requirements are satisfied, the player receives a food bonus.
     *
     * @param player the player who owns this building and receives the effect
     */
    public void applyContinuousEffect(Player player) {
        int currentSets = calculateCurrentSets(player);
        
        if (timesRewarded == -1) {
            timesRewarded = currentSets;
            if (currentSets > 0) {
                player.receiveFood(meat * currentSets);
            }
            return;
        }

        if (currentSets > timesRewarded) {
            int newSets = currentSets - timesRewarded;
            player.receiveFood(meat * newSets);
            timesRewarded = currentSets;
        }
    }
}
