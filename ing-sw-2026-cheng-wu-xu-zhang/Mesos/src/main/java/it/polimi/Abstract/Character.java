package it.polimi.Abstract;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import it.polimi.Game.Elements.Player;
import it.polimi.Game.Elements.Tribe;
import it.polimi.Constants.*;
import it.polimi.Characters.*;

/**
 * Base class for all character cards.
 * Characters are a subset of Tribe cards that can be owned by players and provide various abilities or bonuses.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Hunter.class, name = "HUNTER"),
        @JsonSubTypes.Type(value = Gatherer.class, name = "GATHERER"),
        @JsonSubTypes.Type(value = Builder.class, name = "BUILDER"),
        @JsonSubTypes.Type(value = Inventor.class, name = "INVENTOR"),
        @JsonSubTypes.Type(value = Shaman.class, name = "SHAMAN"),
        @JsonSubTypes.Type(value = Artist.class, name = "ARTIST")
})
public abstract class Character extends Tribe {

    /** The specific type of the character (e.g., HUNTER, BUILDER). */
    private final CharacterType type;
    /** The minimum number of players required for this character to be included in the game. */
    private int players;

    /**
     * Creates a character card for the given era, player count, position, and type.
     *
     * @param era The era this character belongs to.
     * @param players The player count threshold.
     * @param position The initial board position.
     * @param type The character's type.
     */
    public Character(Era era, int players, Position position, CharacterType type) {
        super(era, position);
        this.type = type;
        this.players = players;
    }

    /**
     * Returns the minimum number of players required for this character.
     *
     * @return The required player count.
     */
    public int getPlayers() {
        return players;
    }

    /**
     * Returns the specific character type.
     *
     * @return The CharacterType enum value.
     */
    public CharacterType getType() {
        return type;
    }

    /**
     * Returns the prestige points associated with this character.
     * Default is 0; subclasses can override to provide specific scoring logic.
     *
     * @return The prestige points value.
     */
    public int getPrestigePoints() {
        return 0;
    }

    /**
     * Characters are never events.
     *
     * @return Always false.
     */
    @Override
    public boolean isEvent() {
        return false;
    }

    /**
     * Assigns this character card to the given player.
     *
     * @param player The player who acquired the character.
     */
    @Override
    public void assignToPlayer(Player player) {
        player.addCharacter(this);
    }

}
