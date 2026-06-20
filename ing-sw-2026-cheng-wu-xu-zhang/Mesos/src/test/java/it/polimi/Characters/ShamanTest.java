package it.polimi.Characters;

import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for the {@link Shaman} character.
 */
class ShamanTest {

    /**
     * Verifies shaman constructor fields and star value.
     */
    @Test
    void constructorSetsAllFields() {
        Shaman shaman = new Shaman(Era.II, 3, 2);

        assertEquals(Era.II, shaman.getEra());
        assertEquals(3, shaman.getPlayers());
        assertEquals(Position.DECK, shaman.getPosition());
        assertEquals(CharacterType.SHAMAN, shaman.getType());
        assertEquals(2, shaman.getStar());
        assertFalse(shaman.isEvent());
        assertEquals(0, shaman.getPrestigePoints());
    }

    /**
     * Verifies that assigning a shaman to a player appends it to character cards.
     */
    @Test
    void assignToPlayerAddsCardToPlayer() {
        Shaman shaman = new Shaman(Era.I, 2, 1);
        Player player = new Player(Color.ORANGE);

        shaman.assignToPlayer(player);

        assertEquals(1, player.getCharacters().size());
        assertEquals(shaman, player.getCharacters().get(0));
    }
}
