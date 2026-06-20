package it.polimi.Characters;

import it.polimi.Constants.Era;
import it.polimi.Constants.InventorIcon;
import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import it.polimi.Constants.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the {@link Inventor} character.
 */
class InventorTest {

    /**
     * Verifies that the constructor assigns all fields correctly.
     */
    @Test
    void constructorSetsAllFields() {
        Inventor inventor = new Inventor(Era.I, 2, InventorIcon.boat);

        assertEquals(Era.I, inventor.getEra());
        assertEquals(2, inventor.getPlayers());
        assertEquals(Position.DECK, inventor.getPosition());
        assertEquals(CharacterType.INVENTOR, inventor.getType());
        assertEquals(InventorIcon.boat, inventor.getIcon());
    }

    /**
     * Verifies that the constructor rejects a null icon value.
     */
    @Test
    void constructorRejectsNullIcon() {
        assertThrows(NullPointerException.class, () -> new Inventor(Era.I, 2, null));
    }

    /**
     * Verifies that assigning the inventor to a player adds it to the player's cards.
     */
    @Test
    void assignToPlayerAddsCardToPlayer() {
        Inventor inventor = new Inventor(Era.II, 3, InventorIcon.flute);
        Player player = new Player(Color.BLUE);

        inventor.assignToPlayer(player);

        assertEquals(1, player.getCharacters().size());
        assertEquals(inventor, player.getCharacters().get(0));
    }
}
