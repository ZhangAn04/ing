package it.polimi.Characters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Abstract.Character;
import it.polimi.Constants.Era;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Position;

/**
 * Represents an Artist character in the game.
 * Artists contribute to the tribe's diversity and can be required for specific building effects,
 * though they do not carry additional special attributes beyond their base character properties.
 */
public class Artist extends Character {
    /**
     * Constructs an Artist character with the specified era and player count requirement.
     *
     * @param era the era during which this character is available
     * @param players the minimum number of players required for this character to be in the deck
     */
    @JsonCreator
    public Artist(
            @JsonProperty("era") Era era,
            @JsonProperty("players") int players
    ) {
        super(era, players, Position.DECK, CharacterType.ARTIST);
    }
}
