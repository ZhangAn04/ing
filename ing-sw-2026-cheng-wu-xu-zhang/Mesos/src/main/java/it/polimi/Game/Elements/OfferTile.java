package it.polimi.Game.Elements;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an offer tile that defines the drafting requirements and possible bonuses.
 * <p>
 * During the drafting phase, the offer tile determines how many cards a player must pick
 * from the upper and lower rows, and provides immediate resource bonuses.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class
OfferTile {
    /** The number of cards to be picked from the upper row. */
    private final int numUpperCards;
    /** The number of cards to be picked from the lower row. */
    private final int numLowerCards;
    /** The letter identifying the tile (e.g., 'A', 'B', 'C'). */
    private final char letter;
    /** The amount of food awarded as a bonus when picking this tile. */
    private final int foodBonus;
    /** The minimum number of players required for this tile to be in the game. */
    private final int minPlayers;

    /**
     * Constructs an OfferTile with the specified properties.
     *
     * @param numUpperCards Number of upper cards to pick.
     * @param numLowerCards Number of lower cards to pick.
     * @param letter        Identifying letter.
     * @param foodBonus     Food bonus amount.
     * @param minPlayers    Minimum players required.
     */
    @JsonCreator
    public OfferTile(
            @JsonProperty("numUpperCards") int numUpperCards,
            @JsonProperty("numLowerCards") int numLowerCards,
            @JsonProperty("letter") char letter,
            @JsonProperty("foodBonus") int foodBonus,
            @JsonProperty("minPlayers") int minPlayers) {
        this.numUpperCards = numUpperCards;
        this.numLowerCards = numLowerCards;
        this.letter = letter;
        this.foodBonus = foodBonus;
        this.minPlayers = minPlayers;
    }

    /**
     * Returns whether this tile can be used with the given player count.
     *
     * @param numPlayers The number of players in the game.
     * @return {@code true} if the tile is available, {@code false} otherwise.
     */
    public boolean availableFor(int numPlayers) {
        return numPlayers >= minPlayers;
    }

    /**
     * Applies the tile effect to the selected player.
     * <p>
     * Awards the food bonus defined by this tile to the specified player.
     * </p>
     *
     * @param player The player receiving the effect.
     * @throws IllegalArgumentException If the player is null.
     */
    public void executeAction(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player is null");
        }

        if (foodBonus > 0) {
            player.receiveFood(foodBonus);
        }
    }

    /**
     * Returns the number of cards that must be selected from the upper row.
     *
     * @return The number of upper cards.
     */
    public int getNumUpperCards() {
        return numUpperCards;
    }

    /**
     * Returns the number of cards that must be selected from the lower row.
     *
     * @return The number of lower cards.
     */
    public int getNumLowerCards() {
        return numLowerCards;
    }

    /**
     * Returns the identifying letter of the tile.
     *
     * @return The tile letter.
     */
    public char getLetter() {
        return letter;
    }

    /**
     * Returns the food bonus granted by this tile.
     *
     * @return The food bonus value.
     */
    public int getFoodBonus() {
        return foodBonus;
    }

    /**
     * Returns the minimum number of players required to use this tile.
     *
     * @return The minimum number of players.
     */
    public int getMinPlayers() {
        return minPlayers;
    }
}