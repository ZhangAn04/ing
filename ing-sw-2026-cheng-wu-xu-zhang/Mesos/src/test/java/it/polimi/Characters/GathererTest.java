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
 * Unit tests for the {@link Gatherer} character.
 */
class GathererTest {

    /**
     * Verifies that gatherer constructor initializes all expected values.
     */
    @Test
    void constructorSetsAllFields() {
        Gatherer gatherer = new Gatherer(Era.III, 5, 3);

        assertEquals(Era.III, gatherer.getEra());
        assertEquals(5, gatherer.getPlayers());
        assertEquals(Position.DECK, gatherer.getPosition());
        assertEquals(CharacterType.GATHERER, gatherer.getType());
        assertEquals(3, gatherer.getDiscount());
        assertFalse(gatherer.isEvent());
        assertEquals(0, gatherer.getPrestigePoints());
    }

    /**
     * Verifies that assigning a gatherer to a player stores it in the player list.
     */
    @Test
    void assignToPlayerAddsCardToPlayer() {
        Gatherer gatherer = new Gatherer(Era.I, 2, 1);
        Player player = new Player(Color.PURPLE);

        gatherer.assignToPlayer(player);

        assertEquals(1, player.getCharacters().size());
        assertEquals(gatherer, player.getCharacters().get(0));
    }
}
