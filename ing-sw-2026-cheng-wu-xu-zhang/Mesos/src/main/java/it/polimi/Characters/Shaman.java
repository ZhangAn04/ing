package it.polimi.Characters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Abstract.Character;
import it.polimi.Constants.Era;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Position;

/**
 * Represents a Shaman character in the game.
 * Shamans contribute to the tribe's spiritual strength by providing stars
 * that are utilized in ritual-related effects and events.
 */
public class Shaman extends Character {
    private final int star;

    /**
     * Constructs a Shaman character with the specified properties.
     *
     * @param era the era during which this character is available
     * @param players the minimum number of players required for this character to be in the deck
     * @param star the number of ritual stars provided by this shaman
     */
    @JsonCreator
    public Shaman(
            @JsonProperty("era") Era era,
            @JsonProperty("players") int players,
            @JsonProperty("stars") @JsonAlias("star") int star
    ) {
        super(era, players, Position.DECK, CharacterType.SHAMAN);
        this.star = star;
    }

    /**
     * Returns the star value contributed by this Shaman.
     *
     * @return the number of ritual stars
     */
    public int getStar() {
        return star;
    }
}
