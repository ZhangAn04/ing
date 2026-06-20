package it.polimi.Buildings.Ritual;

import it.polimi.Constants.Color;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link RitualData} ritual configuration data.
 */
class RitualDataTest {

    /**
     * Verifies that stars are accumulated per player correctly.
     */
    @Test
    void addStarAccumulatesForSamePlayer() {
        RitualData ritualData = new RitualData();
        Player player = new Player(Color.ORANGE);

        ritualData.addStar(player, 2);
        ritualData.addStar(player, 3);

        assertEquals(5, ritualData.getStar(player));
    }

    /**
     * Verifies min and max star detection with multiple tracked players.
     */
    @Test
    void minAndMaxAreComputedFromTrackedPlayers() {
        RitualData ritualData = new RitualData();
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        Player p3 = new Player(Color.PURPLE);

        ritualData.addStar(p1, 4);
        ritualData.addStar(p2, 1);
        ritualData.addStar(p3, 2);

        assertEquals(1, ritualData.getMinStar());
        assertEquals(4, ritualData.getMaxStar());
    }

    /**
     * Verifies default min and max values when no player has stars yet.
     */
    @Test
    void minAndMaxReturnDefaultsWhenNoStarsArePresent() {
        RitualData ritualData = new RitualData();

        assertEquals(Integer.MAX_VALUE, ritualData.getMinStar());
        assertEquals(Integer.MIN_VALUE, ritualData.getMaxStar());
    }
}
