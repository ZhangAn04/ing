package it.polimi.Characters;

import it.polimi.Abstract.Character;
import it.polimi.Constants.Era;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Position;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.polimi.Game.Elements.Player;

/**
 * Represents a Hunter character in the game.
 * Hunters contribute to the food supply and may possess a special icon that provides
 * additional bonuses during Hunting events.
 */
public class Hunter extends Character {

    private final boolean icon;

    /**
     * Constructs a Hunter character with the specified properties.
     *
     * @param era the era during which this character is available
     * @param players the minimum number of players required for this character to be in the deck
     * @param icon true if the hunter possesses a special hunting icon, false otherwise
     */
    @JsonCreator
    public Hunter(
        @JsonProperty("era") Era era,
        @JsonProperty("players") int players,
        @JsonProperty("icon") boolean icon
    ) {
        super(era, players, Position.DECK, CharacterType.HUNTER);
        this.icon = icon;
    }

    /**
     * Checks if this hunter possesses the special hunting icon.
     *
     * @return true if the icon is present
     */
    public boolean hasIcon() {
        return icon;
    }

    /**
     * Assigns this hunter card to the given player.
     * If the hunter has a meat icon, the player immediately receives 1 food
     * for every hunter in their tribe (including this newly added one).
     *
     * @param player The player who acquired the character.
     */
    @Override
    public void assignToPlayer(Player player) {
        super.assignToPlayer(player);
        if (hasIcon()) {
            long hunterCount = player.getCharacters().stream()
                    .filter(c -> c.getType() == CharacterType.HUNTER)
                    .count();
            player.receiveFood((int) hunterCount);
        }
    }
}