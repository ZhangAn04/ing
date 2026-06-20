package it.polimi.Game.Elements;

import it.polimi.Constants.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OfferTile} drafting tile behavior.
 */
class OfferTileTest {

    /**
     * Verifies that a tile is available only when the minimum player count is met.
     */
    @Test
    void availableForReturnsTrueOnlyWhenMinPlayersIsMet() {
        OfferTile tile = new OfferTile(1, 0, 'A', 0, 3);

        assertFalse(tile.availableFor(2));
        assertTrue(tile.availableFor(3));
        assertTrue(tile.availableFor(4));
    }

    /**
     * Verifies that getter methods expose all constructor values.
     */
    @Test
    void gettersReturnConfiguredValues() {
        OfferTile tile = new OfferTile(2, 1, 'E', 4, 5);

        assertEquals(2, tile.getNumUpperCards());
        assertEquals(1, tile.getNumLowerCards());
        assertEquals('E', tile.getLetter());
        assertEquals(4, tile.getFoodBonus());
        assertEquals(5, tile.getMinPlayers());
    }

    /**
     * Verifies that the food bonus is applied to the player.
     */
    @Test
    void executeActionAddsFoodBonusToPlayer() {
        OfferTile tile = new OfferTile(1, 0, 'B', 2, 2);
        Player player = new Player(Color.ORANGE);

        tile.executeAction(player);

        assertEquals(2, player.getFood());
    }

    /**
     * Verifies that a tile with zero food bonus leaves the player unchanged.
     */
    @Test
    void executeActionDoesNothingWhenFoodBonusIsZero() {
        OfferTile tile = new OfferTile(1, 0, 'C', 0, 2);
        Player player = new Player(Color.BLUE);

        tile.executeAction(player);

        assertEquals(0, player.getFood());
    }

    /**
     * Verifies that executeAction rejects a null player.
     */
    @Test
    void executeActionThrowsWhenPlayerIsNull() {
        OfferTile tile = new OfferTile(1, 0, 'D', 1, 2);

        assertThrows(IllegalArgumentException.class, () -> tile.executeAction(null));
    }
}
