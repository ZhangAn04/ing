package it.polimi.Buildings.Ritual;

import it.polimi.Constants.Color;
import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import it.polimi.Game.Elements.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link AddStar} ritual star effects.
 */
class AddStarTest {

    /**
     * Verifies that applyRitualEffect adds stars to the correct player.
     */
    @Test
    void applyRitualEffectAddsStar() {
        AddStar building = new AddStar(Era.I, Position.TOP, 5, 2, 3);
        Player player = new Player(Color.ORANGE);
        RitualData ritualData = new RitualData();

        building.applyRitualEffect(player, ritualData);

        assertEquals(3, ritualData.getStar(player));
    }

    /**
     * Verifies that applyRitualEffect accumulates stars correctly on multiple calls.
     */
    @Test
    void applyRitualEffectAccumulatesStars() {
        AddStar building = new AddStar(Era.I, Position.TOP, 5, 2, 2);
        Player player = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        building.applyRitualEffect(player, ritualData);
        building.applyRitualEffect(player, ritualData);

        assertEquals(4, ritualData.getStar(player));
    }

    /**
     * Verifies that different players get independent star counts.
     */
    @Test
    void applyRitualEffectTracksMultiplePlayers() {
        AddStar building = new AddStar(Era.I, Position.TOP, 5, 2, 3);
        Player p1 = new Player(Color.ORANGE);
        Player p2 = new Player(Color.BLUE);
        RitualData ritualData = new RitualData();

        building.applyRitualEffect(p1, ritualData);
        building.applyRitualEffect(p2, ritualData);

        assertEquals(3, ritualData.getStar(p1));
        assertEquals(3, ritualData.getStar(p2));
    }

    /**
     * Verifies constructor sets all building properties correctly.
     */
    @Test
    void constructorSetsBuildingProperties() {
        AddStar building = new AddStar(Era.II, Position.BOTTOM, 7, 3, 5);

        assertEquals(Era.II, building.getEra());
        assertEquals(Position.BOTTOM, building.getPosition());
        assertEquals(7, building.getCost());
        assertEquals(3, building.getPoints());
    }

    /**
     * Verifies constructor with different star values.
     */
    @Test
    void constructorWorksWithVariousStarValues() {
        AddStar building1 = new AddStar(Era.I, Position.TOP, 5, 2, 1);
        AddStar building2 = new AddStar(Era.I, Position.TOP, 5, 2, 10);

        Player player = new Player(Color.ORANGE);
        RitualData ritualData = new RitualData();

        building1.applyRitualEffect(player, ritualData);
        assertEquals(1, ritualData.getStar(player));

        RitualData ritualData2 = new RitualData();
        building2.applyRitualEffect(player, ritualData2);
        assertEquals(10, ritualData2.getStar(player));
    }
}
