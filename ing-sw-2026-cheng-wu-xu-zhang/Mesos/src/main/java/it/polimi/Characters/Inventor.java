package it.polimi.Characters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

import it.polimi.Abstract.Character;
import it.polimi.Constants.*;

/**
 * Represents an Inventor character in the game.
 * Inventors are unique characters that carry a specific invention icon,
 * which is utilized by buildings or events that require technological advancement.
 */
public class Inventor extends Character {

    private final InventorIcon icon;

    /**
     * Constructs an Inventor character with the specified properties.
     *
     * @param era the era during which this character is available
     * @param players the minimum number of players required for this character to be in the deck
     * @param icon the specific invention icon carried by this inventor
     * @throws NullPointerException if the icon is null
     */
    @JsonCreator
    public Inventor(
            @JsonProperty("era") Era era,
            @JsonProperty("players") int players,
            @JsonProperty("inventionIcon") InventorIcon icon) {
        super(era, players, Position.DECK, CharacterType.INVENTOR);
        this.icon = Objects.requireNonNull(icon, "inventionIcon cannot be null");
    }

    /**
     * Retrieves the invention icon associated with this character.
     *
     * @return the invention icon
     */
    public InventorIcon getIcon() {
        return icon;
    }
}
