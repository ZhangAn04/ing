package it.polimi.Buildings;

import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link Turn_Start_Effect} start-of-turn effects.
 */
class Turn_Start_EffectTest {

    /**
     * Verifies that constructor sets all building properties correctly.
     */
    @Test
    void constructorSetsBuildingProperties() {
        Turn_Start_Effect building = new Turn_Start_Effect(Era.I, Position.TOP, 5, 2);

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
        Turn_Start_Effect building1 = new Turn_Start_Effect(Era.II, Position.BOTTOM, 7, 3);
        Turn_Start_Effect building2 = new Turn_Start_Effect(Era.III, Position.DECK, 10, 5);

        assertEquals(Era.II, building1.getEra());
        assertEquals(Era.III, building2.getEra());
    }

    /**
     * Verifies that applyOrderEffect method exists and can be called.
     */
    @Test
    void applyOrderEffectMethodExists() {
        Turn_Start_Effect building = new Turn_Start_Effect(Era.I, Position.TOP, 5, 2);
        assertNotNull(building);

        // Should not throw an exception
        building.applyOrderEffect();
    }

    /**
     * Verifies that the building inherits from Building class correctly.
     */
    @Test
    void buildingInheritsFromBuildingClass() {
        Turn_Start_Effect building = new Turn_Start_Effect(Era.I, Position.TOP, 5, 2);

        assertNotNull(building);
        assertEquals(5, building.getCost());
        assertEquals(2, building.getPoints());
    }

    /**
     * Verifies constructor with various position values.
     */
    @Test
    void constructorWorksWithDifferentPositions() {
        Turn_Start_Effect building1 = new Turn_Start_Effect(Era.I, Position.TOP, 5, 2);
        Turn_Start_Effect building2 = new Turn_Start_Effect(Era.I, Position.BOTTOM, 5, 2);
        Turn_Start_Effect building3 = new Turn_Start_Effect(Era.I, Position.DECK, 5, 2);

        assertEquals(Position.TOP, building1.getPosition());
        assertEquals(Position.BOTTOM, building2.getPosition());
        assertEquals(Position.DECK, building3.getPosition());
    }
}
