package it.polimi.Characters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Abstract.Character;
import it.polimi.Constants.Era;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Position;

/**
 * Represents a Gatherer character in the game.
 * Gatherers specialize in resource acquisition and provide discounts when gathering resources.
 */
public class Gatherer extends Character {


    private final int discount;

    /**
     * Constructs a Gatherer character with the specified properties.
     *
     * @param era the era during which this character is available
     * @param players the minimum number of players required for this character to be in the deck
     * @param discount the resource discount value provided by this gatherer
     */
    @JsonCreator
    public Gatherer(
            @JsonProperty("era") Era era,
            @JsonProperty("players") int players,
            @JsonProperty("discount") int discount
    ) {
        super(era, players, Position.DECK, CharacterType.GATHERER);
        this.discount = discount;
    }

    /**
     * Returns the resource discount granted by this Gatherer.
     *
     * @return the discount value
     */
    public int getDiscount() {
        return discount;
    }
}