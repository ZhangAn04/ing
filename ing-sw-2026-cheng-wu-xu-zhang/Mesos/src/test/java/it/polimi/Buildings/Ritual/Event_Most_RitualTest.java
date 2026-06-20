package it.polimi.Buildings.Ritual;

import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Events.ShamanicRitual;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link Event_Most_Ritual} majority ritual scoring.
 */
class Event_Most_RitualTest {

    /**
     * Verifies that applyRitualEffect awards bonus points when player has maximum stars.
     */
    @Test
    void applyRitualEffectAwardsBonusWhenPlayerHasMaxStars() {
        Event_Most_Ritual building = new Event_Most_Ritual(Era.I, Position.TOP, 5, 2, 2);
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        ritualData.addStar(p1, 5);
        ritualData.addStar(p2, 2);

        int initialPrestige = p1.getPrestigePoints();
        ShamanicRitual ritual = new ShamanicRitual(Era.I, Position.TOP, 3, 1);

        building.applyRitualEffect(p1, ritualData, ritual);

        // 2 multiplier * 3 maxPoints = 6 prestige
        assertEquals(initialPrestige + 6, p1.getPrestigePoints());
    }

    /**
     * Verifies that no bonus is awarded when player does not have maximum stars.
     */
    @Test
    void applyRitualEffectNoBonus() {
        Event_Most_Ritual building = new Event_Most_Ritual(Era.I, Position.TOP, 5, 2, 3);
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        ritualData.addStar(p1, 2);
        ritualData.addStar(p2, 5);

        int initialPrestige = p1.getPrestigePoints();
        ShamanicRitual ritual = new ShamanicRitual(Era.I, Position.TOP, 2, 3);

        building.applyRitualEffect(p1, ritualData, ritual);

        // p1 does not have max stars, no bonus
        assertEquals(initialPrestige, p1.getPrestigePoints());
    }

    /**
     * Verifies that bonus is correctly calculated with different multipliers and max points.
     */
    @Test
    void applyRitualEffectCalculatesBonusCorrectly() {
        Event_Most_Ritual building = new Event_Most_Ritual(Era.II, Position.BOTTOM, 6, 3, 4);
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        ritualData.addStar(p1, 10);
        ritualData.addStar(p2, 5);

        int initialPrestige = p1.getPrestigePoints();
        ShamanicRitual ritual = new ShamanicRitual(Era.II, Position.BOTTOM, 5, 1);

        building.applyRitualEffect(p1, ritualData, ritual);

        // 4 multiplier * 5 maxPoints = 20 prestige
        assertEquals(initialPrestige + 20, p1.getPrestigePoints());
    }

    /**
     * Verifies that bonus is awarded when tied for maximum.
     */
    @Test
    void applyRitualEffectWhenTiedForMax() {
        Event_Most_Ritual building = new Event_Most_Ritual(Era.I, Position.TOP, 5, 2, 2);
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        ritualData.addStar(p1, 5);
        ritualData.addStar(p2, 5);

        int initialPrestige1 = p1.getPrestigePoints();
        int initialPrestige2 = p2.getPrestigePoints();
        ShamanicRitual ritual = new ShamanicRitual(Era.I, Position.TOP, 3, 1);

        building.applyRitualEffect(p1, ritualData, ritual);
        building.applyRitualEffect(p2, ritualData, ritual);

        // Both have max stars, both get bonus
        assertEquals(initialPrestige1 + 6, p1.getPrestigePoints());
        assertEquals(initialPrestige2 + 6, p2.getPrestigePoints());
    }

    /**
     * Verifies constructor sets all building properties correctly.
     */
    @Test
    void constructorSetsBuildingProperties() {
        Event_Most_Ritual building = new Event_Most_Ritual(Era.III, Position.DECK, 10, 5, 3);

        assertEquals(Era.III, building.getEra());
        assertEquals(Position.DECK, building.getPosition());
        assertEquals(10, building.getCost());
        assertEquals(5, building.getPoints());
    }

    /**
     * Verifies constructor with different multiplier values.
     */
    @Test
    void constructorWorksWithVariousMultipliers() {
        Event_Most_Ritual building1 = new Event_Most_Ritual(Era.I, Position.TOP, 5, 2, 1);
        Event_Most_Ritual building2 = new Event_Most_Ritual(Era.I, Position.TOP, 5, 2, 5);

        Player p = new Player(Color.ORANGE);
        RitualData ritualData = new RitualData();
        ritualData.addStar(p, 10);
        ShamanicRitual ritual = new ShamanicRitual(Era.I, Position.TOP, 2, 2);

        int initialPrestige = p.getPrestigePoints();

        building1.applyRitualEffect(p, ritualData, ritual);
        assertEquals(initialPrestige + 2, p.getPrestigePoints()); // 1 * 2

        int newPrestige = p.getPrestigePoints();
        building2.applyRitualEffect(p, ritualData, ritual);
        assertEquals(newPrestige + 10, p.getPrestigePoints()); // 5 * 2
    }
}
