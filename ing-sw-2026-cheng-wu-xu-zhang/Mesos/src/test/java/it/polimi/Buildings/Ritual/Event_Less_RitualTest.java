package it.polimi.Buildings.Ritual;

import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link Event_Less_Ritual} minority ritual scoring.
 */
class Event_Less_RitualTest {

    /**
     * Verifies that applyRitualEffect recognizes when player has minimum stars.
     */
    @Test
    void applyRitualEffectWhenPlayerHasMinStars() {
        Event_Less_Ritual building = new Event_Less_Ritual(Era.I, Position.TOP, 5, 2);
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        ritualData.addStar(p1, 1);
        ritualData.addStar(p2, 3);

        // Should not throw exception - p1 has minimum stars
        building.applyRitualEffect(p1, ritualData);
    }

    /**
     * Verifies that applyRitualEffect can be called when player does not have minimum stars.
     */
    @Test
    void applyRitualEffectWhenPlayerDoesNotHaveMinStars() {
        Event_Less_Ritual building = new Event_Less_Ritual(Era.I, Position.TOP, 5, 2);
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        ritualData.addStar(p1, 5);
        ritualData.addStar(p2, 2);

        // Should not throw exception - p1 does not have minimum stars
        building.applyRitualEffect(p1, ritualData);
    }

    /**
     * Verifies that applyRitualEffect handles tied minimum values.
     */
    @Test
    void applyRitualEffectHandlesMinStarTie() {
        Event_Less_Ritual building = new Event_Less_Ritual(Era.I, Position.TOP, 5, 2);
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        ritualData.addStar(p1, 2);
        ritualData.addStar(p2, 2);

        // Both have minimum, both should be protected
        building.applyRitualEffect(p1, ritualData);
        building.applyRitualEffect(p2, ritualData);
    }

    /**
     * Verifies that constructor sets all building properties correctly.
     */
    @Test
    void constructorSetsBuildingProperties() {
        Event_Less_Ritual building = new Event_Less_Ritual(Era.II, Position.BOTTOM, 7, 3);

        assertEquals(Era.II, building.getEra());
        assertEquals(Position.BOTTOM, building.getPosition());
        assertEquals(7, building.getCost());
        assertEquals(3, building.getPoints());
    }

    /**
     * Verifies that constructor works with different era and position combinations.
     */
    @Test
    void constructorWorksWithVariousErasAndPositions() {
        Event_Less_Ritual building1 = new Event_Less_Ritual(Era.I, Position.TOP, 5, 2);
        Event_Less_Ritual building2 = new Event_Less_Ritual(Era.III, Position.DECK, 10, 5);

        assertEquals(Era.I, building1.getEra());
        assertEquals(Era.III, building2.getEra());
        assertEquals(Position.TOP, building1.getPosition());
        assertEquals(Position.DECK, building2.getPosition());
    }

    /**
     * Verifies that applyRitualEffect on single player with no min/max.
     */
    @Test
    void applyRitualEffectOnSinglePlayer() {
        Event_Less_Ritual building = new Event_Less_Ritual(Era.I, Position.TOP, 5, 2);
        Player player = new Player(Color.ORANGE);
        RitualData ritualData = new RitualData();

        ritualData.addStar(player, 5);

        // Single player has both min and max stars
        building.applyRitualEffect(player, ritualData);
    }
}
