package it.polimi.Characters;

import it.polimi.Constants.CharacterType;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link Hunter} character.
 */
class HunterTest {

    /**
     * Verifies constructor values when the hunter has no icon.
     */
    @Test
    void constructorSetsFieldsWithNoIcon() {
        Hunter hunter = new Hunter(Era.I, 2, false);

        assertEquals(Era.I, hunter.getEra());
        assertEquals(2, hunter.getPlayers());
        assertEquals(Position.DECK, hunter.getPosition());
        assertEquals(CharacterType.HUNTER, hunter.getType());
        assertFalse(hunter.hasIcon());
        assertFalse(hunter.isEvent());
        assertEquals(0, hunter.getPrestigePoints());
    }

    /**
     * Verifies that icon-enabled hunters expose icon state correctly.
     */
    @Test
    void constructorSetsIconFlagWhenPresent() {
        Hunter hunter = new Hunter(Era.II, 3, true);

        assertTrue(hunter.hasIcon());
    }

    /**
     * Verifies that assigning a hunter to a player adds it to the player's characters.
     */
    @Test
    void assignToPlayerAddsCardToPlayer() {
        Hunter hunter = new Hunter(Era.III, 4, true);
        Player player = new Player(Color.BLUE);

        hunter.assignToPlayer(player);

        assertEquals(1, player.getCharacters().size());
        assertEquals(hunter, player.getCharacters().get(0));
    }
}
