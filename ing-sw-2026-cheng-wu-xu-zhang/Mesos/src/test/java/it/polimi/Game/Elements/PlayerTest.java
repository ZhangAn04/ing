package it.polimi.Game.Elements;

import it.polimi.Buildings.EndGame_Builder_Add;
import it.polimi.Characters.Gatherer;
import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Player} resource and ownership state.
 */
class PlayerTest {

    /**
     * Verifies that food is consumed normally when enough food is available.
     */
    @Test
    void receiveFoodAndPayFoodConsumeFoodWhenEnough() {
        Player player = new Player(Color.ORANGE);

        player.receiveFood(5);
        player.payFood(3, 2);

        assertEquals(2, player.getFood());
        assertEquals(0, player.getPrestigePoints());
    }

    /**
     * Verifies that a prestige penalty is applied when food is insufficient.
     */
    @Test
    void payFoodAppliesPrestigePenaltyWhenFoodIsNotEnough() {
        Player player = new Player(Color.BLUE);

        player.receiveFood(1);
        player.payFood(3, 2);

        assertEquals(1, player.getFood());
        assertEquals(-2, player.getPrestigePoints());
    }

    /**
     * Verifies that prestige points can be increased and decreased.
     */
    @Test
    void modifyPrestigePointsUpdatesTotal() {
        Player player = new Player(Color.PURPLE);

        player.modifyPrestigePoints(4);
        player.modifyPrestigePoints(-1);

        assertEquals(3, player.getPrestigePoints());
    }

    /**
     * Verifies that characters and buildings are stored in the player's collections.
     */
    @Test
    void addCharacterAndBuildingStoreElements() {
        Player player = new Player(Color.YELLOW);
        Gatherer gatherer = new Gatherer(Era.I, 2, 1);
        EndGame_Builder_Add building = new EndGame_Builder_Add(Era.I, Position.TOP, 1, 2, 1);

        player.addCharacter(gatherer);
        player.addBuilding(building);

        assertEquals(1, player.getCharacters().size());
        assertEquals(gatherer, player.getCharacters().get(0));
        assertEquals(1, player.getBuildings().size());
        assertEquals(building, player.getBuildings().get(0));
    }

    @Test
    void testPinAndOnlineStatus() {
        Player player = new Player(Color.ORANGE);
        
        // Initial state
        assertTrue(player.isOnline());
        assertNull(player.getPin());
        
        // Modify
        player.setPin("1234");
        player.setOnline(false);
        
        assertEquals("1234", player.getPin());
        assertFalse(player.isOnline());
    }

    /**
     * Verifies that the offer tile is initially null and can be assigned later.
     */
    @Test
    void offerTileIsNullByDefaultAndCanBeSet() {
        Player player = new Player(Color.ORANGE);
        OfferTile tile = new OfferTile(1, 0, 'A', 2, 2);

        assertNull(player.getOfferTile());

        player.setOfferTile(tile);

        assertEquals(tile, player.getOfferTile());
    }
}
