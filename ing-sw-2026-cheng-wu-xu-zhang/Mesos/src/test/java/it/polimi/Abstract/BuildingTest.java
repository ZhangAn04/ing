package it.polimi.Abstract;

import it.polimi.Constants.Era;
import it.polimi.Constants.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the base {@link Building} card behavior.
 */
class BuildingTest {

    /**
     * Verifies that getters expose constructor values.
     */
    @Test
    void gettersReturnConfiguredValues() {
        Building building = new Building(Era.II, Position.TOP, 3, 6);

        assertEquals(Era.II, building.getEra());
        assertEquals(Position.TOP, building.getPosition());
        assertEquals(3, building.getCost());
        assertEquals(6, building.getPoints());
    }

    /**
     * Verifies default effect hooks are safe no-ops and default discount is zero.
     */
    @Test
    void defaultEffectsAreNoOpAndDiscountIsZero() {
        Building building = new Building(Era.I, Position.DECK, 1, 1);

        building.applyRitualEffect();
        building.applyCardEffect();
        building.applyContinuousEffect();
        building.applyEventsEffect();
        building.applyOrderEffect();

        assertEquals(0, building.applyDiscountEffect());
    }
}
