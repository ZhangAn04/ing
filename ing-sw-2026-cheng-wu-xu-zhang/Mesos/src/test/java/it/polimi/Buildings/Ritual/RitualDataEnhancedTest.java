package it.polimi.Buildings.Ritual;

import it.polimi.Constants.Color;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enhanced test suite for RitualData to improve coverage.
 */
class RitualDataEnhancedTest {

    /**
     * Verifies that getStar returns zero for a player with no stars.
     */
    @Test
    void getStarReturnsZeroForNewPlayer() {
        RitualData ritualData = new RitualData();
        Player player = new Player(Color.ORANGE);

        assertEquals(0, ritualData.getStar(player));
    }

    /**
     * Verifies that getMinStar returns Integer.MAX_VALUE when no stars are added.
     */
    @Test
    void getMinStarReturnsMaxValueWhenEmpty() {
        RitualData ritualData = new RitualData();

        assertEquals(Integer.MAX_VALUE, ritualData.getMinStar());
    }

    /**
     * Verifies that getMaxStar returns Integer.MIN_VALUE when no stars are added.
     */
    @Test
    void getMaxStarReturnsMinValueWhenEmpty() {
        RitualData ritualData = new RitualData();

        assertEquals(Integer.MIN_VALUE, ritualData.getMaxStar());
    }

    /**
     * Verifies that addStar can be called multiple times on the same player.
     */
    @Test
    void addStarMultipleTimesAccumulates() {
        RitualData ritualData = new RitualData();
        Player player = new Player(Color.ORANGE);

        ritualData.addStar(player, 1);
        ritualData.addStar(player, 2);
        ritualData.addStar(player, 3);

        assertEquals(6, ritualData.getStar(player));
    }

    /**
     * Verifies min star detection with two players.
     */
    @Test
    void getMinStarWithTwoPlayers() {
        RitualData ritualData = new RitualData();
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);

        ritualData.addStar(p1, 5);
        ritualData.addStar(p2, 3);

        assertEquals(3, ritualData.getMinStar());
    }

    /**
     * Verifies max star detection with two players.
     */
    @Test
    void getMaxStarWithTwoPlayers() {
        RitualData ritualData = new RitualData();
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);

        ritualData.addStar(p1, 5);
        ritualData.addStar(p2, 3);

        assertEquals(5, ritualData.getMaxStar());
    }

    /**
     * Verifies min and max with single player multiple stars.
     */
    @Test
    void minAndMaxWithSinglePlayer() {
        RitualData ritualData = new RitualData();
        Player player = new Player(Color.ORANGE);

        ritualData.addStar(player, 7);

        assertEquals(7, ritualData.getMinStar());
        assertEquals(7, ritualData.getMaxStar());
    }

    /**
     * Verifies that negative star values are handled.
     */
    @Test
    void addStarHandlesNegativeValues() {
        RitualData ritualData = new RitualData();
        Player player = new Player(Color.ORANGE);

        ritualData.addStar(player, 5);
        ritualData.addStar(player, -2);

        assertEquals(3, ritualData.getStar(player));
    }

    /**
     * Verifies zero star value.
     */
    @Test
    void addStarWithZeroValue() {
        RitualData ritualData = new RitualData();
        Player player = new Player(Color.ORANGE);

        ritualData.addStar(player, 0);

        assertEquals(0, ritualData.getStar(player));
    }

    /**
     * Verifies min and max with multiple players of different star counts.
     */
    @Test
    void minAndMaxWithMultiplePlayers() {
        RitualData ritualData = new RitualData();
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        Player p3 = new Player(Color.PURPLE);
        Player p4 = new Player(Color.YELLOW);

        ritualData.addStar(p1, 10);
        ritualData.addStar(p2, 5);
        ritualData.addStar(p3, 1);
        ritualData.addStar(p4, 8);

        assertEquals(1, ritualData.getMinStar());
        assertEquals(10, ritualData.getMaxStar());
    }
}
