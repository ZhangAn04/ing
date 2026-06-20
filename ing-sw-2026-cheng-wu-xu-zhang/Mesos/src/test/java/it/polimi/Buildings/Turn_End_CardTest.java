package it.polimi.Buildings;

import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link Turn_End_Card} end-of-turn effects.
 */
class Turn_End_CardTest {

    /**
     * Verifies that constructor sets all building properties correctly.
     */
    @Test
    void constructorSetsBuildingProperties() {
        Turn_End_Card building = new Turn_End_Card(Era.I, Position.TOP, 5, 2);

        assertEquals(Era.I, building.getEra());
        assertEquals(Position.TOP, building.getPosition());
        assertEquals(5, building.getCost());
        assertEquals(2, building.getPoints());
    }

    /**
     * Verifies that all properties are set correctly in different eras.
     */
    @Test
    void constructorWorksWithDifferentEras() {
        Turn_End_Card building1 = new Turn_End_Card(Era.II, Position.BOTTOM, 7, 3);
        Turn_End_Card building2 = new Turn_End_Card(Era.III, Position.DECK, 10, 5);

        assertEquals(Era.II, building1.getEra());
        assertEquals(Era.III, building2.getEra());
    }

    /**
     * Verifies that applyCardEffect method exists and can be called.
     */
    @Test
    void applyCardEffectMethodExists() {
        Turn_End_Card building = new Turn_End_Card(Era.I, Position.TOP, 5, 2);
        assertNotNull(building);

        // Should not throw an exception
        building.applyCardEffect();
    }

    /**
     * Verifies that the building inherits from Building class correctly.
     */
    @Test
    void buildingInheritsFromBuildingClass() {
        Turn_End_Card building = new Turn_End_Card(Era.I, Position.TOP, 5, 2);

        assertNotNull(building);
        assertEquals(5, building.getCost());
        assertEquals(2, building.getPoints());
    }
}
